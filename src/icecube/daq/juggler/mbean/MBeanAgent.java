package icecube.daq.juggler.mbean;

import com.sun.jdmk.comm.HtmlAdaptorServer;

import java.lang.management.ManagementFactory;

import java.net.BindException;
import java.net.InetSocketAddress;
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
    private ObjectName htmlName;

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

    ObjectName getHtmlName(MBeanAgent agent)
        throws JMException
    {
        int port;
        try {
            port = agent.getPort();
        } catch (MBeanAgentException mbe) {
            throw new JMException(mbe.getMessage());
        }

        if (htmlName == null) {
            htmlName = new ObjectName(agent.getDomain() + ":name=" + name +
                                      ",port=" + port);
        }

        return htmlName;
    }

    public String toString()
    {
        return name + ":" + bean;
    }
}

public class MBeanAgent
{
    public static final int DEFAULT_PORT = 8000;

    private static final Log LOG = LogFactory.getLog(MBeanAgent.class);

    private int port = Integer.MIN_VALUE;
    private HashMap beans = new HashMap();

    private HtmlAdaptorServer adapter;

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

    String getDomain()
    {
        return getClass().getName();
    }

    public int getPort()
        throws MBeanAgentException
    {
        if (port == Integer.MIN_VALUE) {
            throw new MBeanAgentException("Port has not been set");
        }

        return port;
    }

    public boolean isRunning()
    {
        return (adapter != null);
    }

    private void registerBeans()
    {
        // Get the platform MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        for (Iterator iter = beans.values().iterator(); iter.hasNext();) {
            BeanBin bin = (BeanBin) iter.next();

            try {
                // register MBean with the platform MBeanServer
                mbs.registerMBean(bin.getBean(), bin.getBeanName(this));

                // Associate MBean with the HTML adapter
                mbs.registerMBean(adapter, bin.getHtmlName(this));
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
     * @throws JMException if there is a probleam registering the mbeans
     * @throws MBeanAgentException if the agent is already running
     */
    public void start()
        throws JMException, MBeanAgentException
    {
        if (adapter != null) {
            throw new MBeanAgentException("Agent is already running");
        }

        adapter = new HtmlAdaptorServer();

        ServerSocket ss;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(null);
            port = ss.getLocalPort();
            ss.close();
        } catch (Exception ex) {
            throw new MBeanAgentException("Couldn't search for port", ex);
        }

        adapter.setPort(port);

        registerBeans();

        adapter.start();
    }

    /**
     * Stop agent.
     *
     * @throws JMException if there is a probleam unregistering the mbeans
     * @throws MBeanAgentException if the agent is not running
     */
    public void stop()
        throws JMException, MBeanAgentException
    {
        if (adapter == null) {
            throw new MBeanAgentException("Agent has not been started");
        }

        adapter.stop();

        int num = 0;
        while (adapter.getState() == adapter.STOPPING) {
            try {
                Thread.sleep(200);
            } catch (Exception ex) {
                // ignore interrupts
            }
            num++;
        }

        unregisterBeans();

        adapter = null;
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

                // unregister HTML MBean
                mbs.unregisterMBean(bin.getHtmlName(this));
            } catch (JMException jme) {
                LOG.error("Couldn't unregister bean \"" + bin + "\"", jme);
            }
        }
    }
}
