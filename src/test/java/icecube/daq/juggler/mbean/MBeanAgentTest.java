package icecube.daq.juggler.mbean;

import icecube.daq.juggler.mbean.MBeanAgent;
import icecube.daq.juggler.mbean.MBeanAgentException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;

import javax.management.JMException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class MBeanAgentTest
    extends TestCase
{
    private MBeanAgent agent;

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

    protected void setUp()
    {
        agent = new MBeanAgent();
    }

    public static Test suite()
    {
        return new TestSuite(MBeanAgentTest.class);
    }

    protected void tearDown()
    {
        try {
            agent.stop();
        } catch (Exception ex) {
            // ignore teardown errors
        }
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

    public void testMultiHtml()
        throws IOException, JMException, MBeanAgentException
    {
        final String beanName = "hello1";

        Hello bean = new Hello();
        agent.addBean(beanName, bean);

        agent.start();

        assertTrue("Couldn't find entry for \"" + beanName +
                   "\" in MBean HTML page", findBeanInHtml(agent, beanName));

        MBeanAgent agent2 = new MBeanAgent();

        final String beanName2 = "hello2";

        Hello bean2 = new Hello();
        try {
            agent2.addBean(beanName2, bean2);

            agent2.start();

            assertTrue("Couldn't find entry for \"" + beanName2 +
                       "\" in second MBean HTML page",
                       findBeanInHtml(agent2, beanName2));

            assertTrue("Couldn't find entry for \"" + beanName +
                       "\" in second MBean HTML page",
                       findBeanInHtml(agent2, beanName));

            assertTrue("Couldn't find entry for \"" + beanName +
                       "\" in first MBean HTML page",
                       findBeanInHtml(agent, beanName));

            assertTrue("Couldn't find entry for \"" + beanName2 +
                       "\" in first MBean HTML page",
                       findBeanInHtml(agent, beanName2));
        } finally {
            agent2.stop();
        }
    }

    public void testHtmlOverlap()
        throws IOException, JMException, MBeanAgentException
    {
        ServerSocket ss = new ServerSocket(MBeanAgent.DEFAULT_PORT);
        ServerSocket ss2 = new ServerSocket(MBeanAgent.DEFAULT_PORT + 1);

        final String beanName = "hello";

        Hello bean = new Hello();
        agent.addBean(beanName, bean);

        agent.start();

        assertTrue("Couldn't find entry for \"" + beanName +
                   "\" in MBean HTML page", findBeanInHtml(agent, beanName));

        ss.close();
    }

    public static void main(String argv[])
        throws JMException, MBeanAgentException
    {
        junit.textui.TestRunner.run(suite());
    }
}
