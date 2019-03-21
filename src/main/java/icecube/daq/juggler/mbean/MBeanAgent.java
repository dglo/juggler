package icecube.daq.juggler.mbean;

import com.sun.jdmk.comm.HtmlAdaptorServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Set;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Logger;

/**
 * MBean container.
 */
class BeanBin
{
    /** MBean name */
    private String name;
    /** MBean object */
    private Object bean;
    /** JMX MBean name */
    private ObjectName beanName;

    /**
     * Create an MBean container
     *
     * @param name short MBean name
     * @param bean MBean object
     */
    BeanBin(String name, Object bean)
    {
        this.name = name;
        this.bean = bean;
    }

    /**
     * Get the MBean object.
     *
     * @return MBean
     */
    Object getBean()
    {
        return bean;
    }

    /**
     * Get the JMX name for this MBean.
     *
     * @param agent MBean agent
     *
     * @return JMX name
     *
     * @throws JMException if there is a problem creating the name
     */
    ObjectName getBeanName(MBeanAgent agent)
        throws JMException
    {
        if (beanName == null) {
            beanName = new ObjectName(agent.getDomain(), "name", name);
        }

        return beanName;
    }

    /**
     * Debugging representation of MBean container data.
     *
     * @return debugging string
     */
    @Override
    public String toString()
    {
        return name + ":" + bean;
    }
}

/**
 * Agent which handles all MBeans for a component.
 */
public class MBeanAgent
{
    private static final Logger LOG = Logger.getLogger(MBeanAgent.class);

    /** Mapping from short name to MBean object */
    private HashMap<String, BeanBin> beans = new HashMap<String, BeanBin>();

    /** HTML port */
    private int htmlPort = Integer.MIN_VALUE;
    /** HTML JMX name */
    private ObjectName htmlName;
    /** HTML server */
    private HtmlAdaptorServer htmlAdapter;

    /** XML-RPC port */
    private int xmlRpcPort = Integer.MIN_VALUE;
    /** XML-RPC JMX name */
    private ObjectName xmlRpcName;
    /** XML-RPC server */
    private XMLRPCServer xmlRpcAdapter;

    /**
     * Create an MBean agent.
     */
    public MBeanAgent()
    {
    }

    /**
     * Add an MBean
     *
     * @param name short MBean name
     * @param bean MBean object
     */
    public void addBean(String name, Object bean)
        throws MBeanAgentException
    {
        if (isRunning()) {
            // allow late bind of MBeans
            try {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                mbs.registerMBean(bean, new ObjectName(getDomain(),
                    "name", name));
            } catch (JMException jmx) {
                throw new MBeanAgentException(jmx);
            }
        }

        if (beans.containsKey(name)) {
            throw new MBeanAgentException("MBean \"" + name + "\" has" +
                                          " already been added to this agent");
        }

        beans.put(name, new BeanBin(name, bean));
    }

    /**
     * Find an unused IP port to be used by a new server.
     *
     * @return unused IP port
     */
    private static int findUnusedPort()
        throws MBeanAgentException
    {
        int port;

        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(null);
            port = ss.getLocalPort();
            ss.close();
        } catch (IOException ioe) {
            throw new MBeanAgentException("Couldn't search for port", ioe);
        }

        return port;
    }

    /**
     * Return the requested bean
     *
     * @return bean
     *
     * @throws MBeanAgentException if there is no bean with the specified name
     */
    public Object getBean(String name)
        throws MBeanAgentException
    {
        if (!beans.containsKey(name)) {
            throw new MBeanAgentException("Bean \"" + name +
                                          "\" does not exist");
        }

        return beans.get(name).getBean();
    }

    /**
     * Get the MBean domain.
     *
     * @return MBean domain
     */
    String getDomain()
    {
        return getClass().getName();
    }

    /**
     * Get the JMX name used to refer to the HTML server MBean.
     *
     * @return HTML server name
     */
    private ObjectName getHtmlName()
        throws MBeanAgentException
    {
        if (htmlName == null) {
            try {
                htmlName = new ObjectName(getDomain() +
                                          ":name=htmlAdapter,port=" +
                                          getHtmlPort());
            } catch (JMException jme) {
                throw new MBeanAgentException("Could not create" +
                                              " HTML MBean name", jme);
            }
        }

        return htmlName;
    }

    /**
     * Get the IP port on which the HTML server is listening.
     *
     * @return HTML port
     */
    public int getHtmlPort()
        throws MBeanAgentException
    {
        if (htmlPort == Integer.MIN_VALUE) {
            throw new MBeanAgentException("HTML port has not been set");
        }

        return htmlPort;
    }

    /**
     * Create a local monitoring object.
     *
     * @param compName component name
     * @param compNum component number
     * @param interval number of seconds between monitoring entries
     *
     * @return new local monitoring object
     */
    public LocalMonitor getLocalMonitoring(String compName, int compNum,
                                           int interval)
    {
        return new LocalMonitor(compName, compNum, interval, xmlRpcAdapter);
    }

    /**
     * Get the JMX name used to refer to the XML-RPC server MBean.
     *
     * @return XML-RPC server name
     */
    private ObjectName getXmlRpcName()
        throws MBeanAgentException
    {
        if (xmlRpcName == null) {
            try {
                xmlRpcName = new ObjectName(getDomain() +
                                            ":name=xmlRpcAdapter,port=" +
                                            getXmlRpcPort());
            } catch (JMException jme) {
                throw new MBeanAgentException("Could not create" +
                                              " XML-RPC MBean name", jme);
            }
        }

        return xmlRpcName;
    }

    /**
     * Get the IP port on which the XML-RPC server is listening.
     *
     * @return XML-RPC port
     */
    public int getXmlRpcPort()
        throws MBeanAgentException
    {
        if (xmlRpcPort == Integer.MIN_VALUE) {
            throw new MBeanAgentException("XML-RPC port has not been set");
        }

        return xmlRpcPort;
    }

    /**
     * Is an HTML or XML-RPC server running?
     *
     * @return <tt>true</tt> if either server is running
     */
    public boolean isRunning()
    {
        return (htmlAdapter != null && xmlRpcAdapter != null);
    }

    /**
     * Get list of bean names
     *
     * @return bean names
     */
    public Set<String> listBeans()
    {
        return beans.keySet();
    }

    /**
     * Register all known MBeans with the MBean server.
     */
    private void registerBeans()
    {
        // Get the platform MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        for (BeanBin bin : beans.values()) {
            // register MBean with the platform MBeanServer
            try {
                mbs.registerMBean(bin.getBean(), bin.getBeanName(this));
            } catch (JMException jme) {
                LOG.error("Couldn't register bean \"" + bin + "\"", jme);
            }
        }
    }

    /**
     * Remove an MBean.
     *
     * @param name short MBean name
     *
     * @return removed MBean object
     *
     * @throws MBeanAgentException if there is a problem
     */
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

        BeanBin bin = beans.remove(name);
        return bin.getBean();
    }

    /**
     * Set the MBean data handler to be monitored locally.
     *
     * @param moniLocal local monitoring object
     */
    public void setMonitoringData(LocalMonitor moniLocal)
    {
        moniLocal.setMonitoringData(xmlRpcAdapter);
    }

    /**
     * Start agent.
     *
     * @throws MBeanAgentException if there is a problem
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

        // register all MBeans
        registerBeans();

        htmlAdapter.start();
        xmlRpcAdapter.start(this);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Started MBean agent: HTML port " + htmlPort +
                     ", XML-RPC port " + xmlRpcPort);
        }
    }

    /**
     * Stop agent.
     *
     * @throws MBeanAgentException if the agent is not running
     */
    public void stop()
        throws MBeanAgentException
    {
        if (htmlAdapter == null || xmlRpcAdapter == null) {
            throw new MBeanAgentException("Agent has not been started");
        }

        htmlAdapter.stop();
        xmlRpcAdapter.stop();

        while (htmlAdapter.getState() == htmlAdapter.STOPPING &&
               !xmlRpcAdapter.isStopped())
        {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        unregisterBeans();

        htmlAdapter = null;
        xmlRpcAdapter = null;
    }

    /**
     * Unregister all known MBeans with the MBean server.
     */
    private void unregisterBeans()
    {
        // Get the platform MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        for (BeanBin bin : beans.values()) {
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
        } catch (MBeanAgentException mae) {
            LOG.error("Couldn't unregister XML-RPC bean", mae);
        }

        // clear cached XML-RPC data
        xmlRpcName = null;
        xmlRpcPort = Integer.MIN_VALUE;

        // unregister HTML MBean
        try {
            mbs.unregisterMBean(getHtmlName());
        } catch (JMException jme) {
            LOG.error("Couldn't unregister HTML bean", jme);
        } catch (MBeanAgentException mae) {
            LOG.error("Couldn't unregister HTML bean", mae);
        }

        // clear cached HTML data
        htmlName = null;
        htmlPort = Integer.MIN_VALUE;
    }
}
