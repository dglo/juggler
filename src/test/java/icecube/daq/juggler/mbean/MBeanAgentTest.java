package icecube.daq.juggler.mbean;

import icecube.daq.juggler.mbean.MBeanAgent;
import icecube.daq.juggler.mbean.MBeanAgentException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.MalformedURLException;
import java.net.URL;

import javax.management.JMException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.xmlrpc.XmlRpcException;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

public class MBeanAgentTest
    extends TestCase
{
    private MBeanAgent agent;

    public MBeanAgentTest(String name)
    {
        super(name);
    }

    private boolean findBeanInHtml(MBeanAgent agent, String name)
        throws IOException, MBeanAgentException
    {
        boolean found = false;

        final String target = "name=" + name;
        final String urlStr = "http://localhost:" + agent.getHtmlPort();

        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException mue) {
            throw new IOException("Couldn't connect to <" + urlStr + ">");
        }

        InputStream urlStream = url.openStream();
        BufferedReader in =
            new BufferedReader(new InputStreamReader(urlStream));

        for ( ; ; ) {
            String line = in.readLine();
            if (line == null) {
                break;
            }

            if (line.contains(target)) {
                found = true;
            }
        }

        urlStream.close();

        return found;
    }

    private boolean findBeanInXmlRpc(MBeanAgent agent, String name)
        throws MBeanAgentException, XmlRpcException
    {
        boolean found = false;

        final String target = "name=" + name;
        final String urlStr = "http://localhost:" + agent.getXmlRpcPort();

        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException mue) {
            throw new XmlRpcException("Couldn't build URL <" + urlStr + ">");
        }

        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(url);

        XmlRpcClient client = new XmlRpcClient();
        client.setConfig(config);

        Object[] params = new Object[] { name };
        Object reply = client.execute("mbean.listGetters", params);
        assertNotNull("No response from mbean.listGetters", reply);
        assertTrue("Expected array, got " + reply.getClass().getName(),
                   reply.getClass().isArray());

        Object[] results = (Object[]) reply;
        assertEquals("Unexpected array length", 1, results.length);

        return true;
    }

    protected void setUp()
        throws Exception
    {
        super.setUp();

        agent = new MBeanAgent();
    }

    public static Test suite()
    {
        return new TestSuite(MBeanAgentTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        try {
            agent.stop();
        } catch (Exception ex) {
            // ignore teardown errors
        }

        super.tearDown();
    }

    public void testSimple()
        throws MBeanAgentException
    {
        assertFalse("Agent should not be running", agent.isRunning());

        final String beanName = "hello";

        try {
            agent.removeBean(beanName);
            fail("Should not be able to remove non-existent MBean");
        } catch (MBeanAgentException mbe) {
            // expect this to fail
        }

        Hello bean = new Hello();
        agent.addBean(beanName, bean);

        assertFalse("Agent should not be running", agent.isRunning());

        Object removed = agent.removeBean(beanName);
        assertEquals("Unexpected MBean removed", bean, removed);
    }

    public void testHtml()
        throws IOException, JMException, MBeanAgentException
    {
        final String beanName = "hello";

        Hello bean = new Hello();
        agent.addBean(beanName, bean);

        agent.start();

        assertTrue("Couldn't find entry for \"" + beanName +
                   "\" in MBean HTML page", findBeanInHtml(agent, beanName));
    }

    public void testXmlRpc()
        throws IOException, JMException, MBeanAgentException, XmlRpcException
    {
        final String beanName = "hello";

        Hello bean = new Hello();
        agent.addBean(beanName, bean);

        agent.start();

        findBeanInXmlRpc(agent, beanName);
    }

    public void testDynamicMBean()
        throws IOException, MBeanAgentException
    {
        assertFalse("Agent should not be running", agent.isRunning());

        final String beanName = "hello";

        try {
            agent.removeBean(beanName);
            fail("Should not be able to remove non-existent MBean");
        } catch (MBeanAgentException mbe) {
            // expect this to fail
        }

        Hello bean = new Hello();
        String[] methods = new String[] { "Message" };

        MBeanWrapper wrapper = new MBeanWrapper(bean, methods);
        agent.addBean(beanName, wrapper);

        agent.start();

        assertTrue("Couldn't find entry for \"" + beanName +
                   "\" in MBean HTML page", findBeanInHtml(agent, beanName));

        agent.stop();

        assertFalse("Agent should not be running", agent.isRunning());

        Object removed = agent.removeBean(beanName);
        assertEquals("Unexpected MBean removed", wrapper, removed);
    }

    public static void main(String argv[])
        throws JMException, MBeanAgentException
    {
        junit.textui.TestRunner.run(suite());
    }
}
