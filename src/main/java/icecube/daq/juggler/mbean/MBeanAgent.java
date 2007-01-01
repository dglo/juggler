package icecube.daq.juggler.mbean;

import com.sun.jdmk.comm.HtmlAdaptorServer;

import java.io.IOException;

import java.lang.management.ManagementFactory;

import java.net.ServerSocket;

import java.util.HashMap;
import java.util.Iterator;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class BeanBin
{
    private String name;
    private Object bean;
    private ObjectName beanName;

    BeanBin(String name, Object bean)
    {
        this.name = name;
        this.bean = bean;
    }

    Object getBean()
    {
        return bean;
    }

    ObjectName getBeanName(MBeanAgent agent)
        throws JMException
    {
        if (beanName == null) {
            beanName = new ObjectName(agent.getDomain(), "name", name);
        }

        return beanName;
    }

    public String toString()
    {
        return name + ":" + bean;
    }
}

public class MBeanAgent
{
    private static final Log LOG = LogFactory.getLog(MBeanAgent.class);

    private HashMap beans = new HashMap();

    private int htmlPort = Integer.MIN_VALUE;
    private HtmlAdaptorServer htmlAdapter;

    private int xmlRpcPort = Integer.MIN_VALUE;
    private XMLRPCServer xmlRpcAdapter;

    public MBeanAgent()
    {
    }

    public void addBean(String name, Object bean)
        throws MBeanAgentException
    {
        if (isRunning()) {
            throw new MBeanAgentException("Cannot remove MBean while" +
                                          " agent is running");
        }

        if (beans.containsKey(name)) {
            throw new MBeanAgentException("MBean \"" + name + "\" has" +
                                          " already been added to this agent");
        }

        beans.put(name, new BeanBin(name, bean));
    }

    private static int findUnusedPort()
        throws MBeanAgentException
    {
        int port;

        ServerSocket ss;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(null);
            port = ss.getLocalPort();
            ss.close();
        } catch (IOException ioe) {
            throw new MBeanAgentException("Couldn't search for port", ioe);
        }

        return port;
    }

    String getDomain()
    {
        return getClass().getName();
    }

    private ObjectName getHtmlName()
        throws JMException
    {
        return new ObjectName(getDomain() + ":name=htmlAdapter,port=" +
                              htmlPort);
    }

    public int getHtmlPort()
        throws MBeanAgentException
    {
        if (htmlPort == Integer.MIN_VALUE) {
            throw new MBeanAgentException("HTML port has not been set");
        }

        return htmlPort;
    }

    private ObjectName getXmlRpcName()
        throws JMException
    {
        return new ObjectName(getDomain() + ":name=xmlRpcAdapter,port=" +
                              xmlRpcPort);
    }

    public int getXmlRpcPort()
        throws MBeanAgentException
    {
        if (xmlRpcPort == Integer.MIN_VALUE) {
            throw new MBeanAgentException("XML-RPC port has not been set");
        }

        return xmlRpcPort;
    }

    public boolean isRunning()
    {
        return (htmlAdapter != null && xmlRpcAdapter != null);
    }

    private void registerBeans()
    {
        // Get the platform MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        for (Iterator iter = beans.values().iterator(); iter.hasNext();) {
            BeanBin bin = (BeanBin) iter.next();

            // register MBean with the platform MBeanServer
            try {
                mbs.registerMBean(bin.getBean(), bin.getBeanName(this));
            } catch (JMException jme) {
                LOG.error("Couldn't register bean \"" + bin + "\"", jme);
            }
        }
    }

    public Object removeBean(String name)
        throws MBeanAgentException
    {
        if (isRunning()) {
            throw new MBeanAgentException("Cannot remove MBean while" +
                                          " agent is running");
        }

        if (!beans.containsKey(name)) {
            throw new MBeanAgentException("MBean \"" + name + "\" has not" +
                                          " been added to this agent");
        }

        BeanBin bin = (BeanBin) beans.remove(name);
        return bin.getBean();
    }

    /**
     * Start agent.
     *
     * @throws MBeanAgentException if the agent is already running
     */
    public void start()
        throws MBeanAgentException
    {
        if (htmlAdapter != null || xmlRpcAdapter != null) {
            throw new MBeanAgentException("Agent is already running");
        }

        // Get the platform MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        htmlAdapter = new HtmlAdaptorServer();
        htmlPort = findUnusedPort();
        htmlAdapter.setPort(htmlPort);

        // Register the HTML adapter
        try {
            mbs.registerMBean(htmlAdapter, getHtmlName());
        } catch (JMException jme) {
            throw new MBeanAgentException("Couldn't register HTML adapter",
                                          jme);
        }

        xmlRpcAdapter = new XMLRPCServer();
        xmlRpcPort = findUnusedPort();
        xmlRpcAdapter.setPort(xmlRpcPort);

        // Register the XML-RPC adapter
        try {
            mbs.registerMBean(xmlRpcAdapter, getXmlRpcName());
        } catch (JMException jme) {
            throw new MBeanAgentException("Couldn't register XML-RPC adapter",
                                          jme);
        }

        registerBeans();

        htmlAdapter.start();
        xmlRpcAdapter.start();

        LOG.info("Started MBean agent: HTML port " + htmlPort +
                 ", XML-RPC port " + xmlRpcPort);
    }

    /**
     * Stop agent.
     *
     * @throws MBeanAgentException if the agent is not running
     */
    public void stop()
        throws MBeanAgentException
    {
        if (htmlAdapter == null && xmlRpcAdapter == null) {
            throw new MBeanAgentException("Agent has not been started");
        }

        htmlAdapter.stop();
        xmlRpcAdapter.stop();

        int num = 0;
        while (htmlAdapter.getState() == htmlAdapter.STOPPING &&
               !xmlRpcAdapter.isStopped())
        {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
            num++;
        }

        unregisterBeans();

        htmlAdapter = null;
        xmlRpcAdapter = null;
    }

    private void unregisterBeans()
    {
        // Get the platform MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        for (Iterator iter = beans.values().iterator(); iter.hasNext();) {
            BeanBin bin = (BeanBin) iter.next();

            try {
                // unregister basic MBean
                mbs.unregisterMBean(bin.getBeanName(this));
            } catch (JMException jme) {
                LOG.error("Couldn't unregister bean \"" + bin + "\"", jme);
            }
        }

        // unregister XML-RPC MBean
        try {
            mbs.unregisterMBean(getXmlRpcName());
        } catch (JMException jme) {
            LOG.error("Couldn't unregister XML-RPC bean", jme);
        }

        // unregister HTML MBean
        try {
            mbs.unregisterMBean(getHtmlName());
        } catch (JMException jme) {
            LOG.error("Couldn't unregister HTML bean", jme);
        }
    }
}
