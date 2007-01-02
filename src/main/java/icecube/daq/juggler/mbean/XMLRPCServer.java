package icecube.daq.juggler.mbean;

import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;

import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.xmlrpc.XmlRpcException;

import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;

import org.apache.xmlrpc.webserver.WebServer;

/**
 * XML-RPC adapter for JMX MBeans.
 */
class XMLRPCServer
    implements MBeanRegistration, NotificationListener, XMLRPCServerMBean
{
    private static final Log LOG = LogFactory.getLog(XMLRPCServer.class);

    private static Object mbean;

    private static ObjectName delegateName;

    private ObjectName name;
    private MBeanServer server;

    private int port = Integer.MIN_VALUE;
    private WebServer webServer;

    private HashMap beans = new HashMap();

    public XMLRPCServer()
    {
    }

    private static Object fixAttribute(Object obj)
    {
        String attrName;
        Object attrVal;

        if (obj != null && obj instanceof Attribute) {
            Attribute attr = (Attribute) obj;

            attrName = attr.getName();
            attrVal = attr.getValue();
        } else {
            attrName = "?unnamed?";
            attrVal = obj;
        }

        if (attrVal == null) {
            return null;
        }

        if (attrVal instanceof Byte) {
            return new Integer(((Byte) attrVal).intValue());
        } else if (attrVal instanceof Character) {
            char[] array = new char[] { ((Character) attrVal).charValue() };
            return new String(array);
        } else if (attrVal instanceof Short) {
            return new Integer(((Short) attrVal).intValue());
        } else if (attrVal instanceof Long) {
            long lVal = ((Long) attrVal).longValue();
            if (lVal < (long) Integer.MIN_VALUE ||
                lVal > (long) Integer.MAX_VALUE)
            {
                return attrVal.toString() + "L";
            }

            return new Integer((int) lVal);
        } else if (attrVal instanceof Float) {
            return new Double(((Float) attrVal).doubleValue());
        }

        return attrVal;
    }

    Object get(String mbeanName, String attrName)
        throws MBeanAgentException
    {
        if (!beans.containsKey(mbeanName)) {
            throw new MBeanAgentException("Unknown MBean \"" + mbeanName +
                                          "\"");
        }

        ObjectName objName = (ObjectName) beans.get(mbeanName);

        try {
            return fixAttribute(server.getAttribute(objName, attrName));
        } catch (JMException jme) {
            throw new MBeanAgentException("Couldn't get MBean \"" + mbeanName +
                                          "\" attribute \"" + attrName + "\"",
                                          jme);
        }
    }

    Object[] getList(String mbeanName, String[] attrNames)
        throws MBeanAgentException
    {
        if (!beans.containsKey(mbeanName)) {
            throw new MBeanAgentException("Unknown MBean \"" + mbeanName +
                                          "\"");
        }

        ObjectName objName = (ObjectName) beans.get(mbeanName);

        Iterator iter;
        try {
            iter = server.getAttributes(objName, attrNames).iterator();
        } catch (JMException jme) {
            String nameStr = null;
            for (int i = 0; i < attrNames.length; i++) {
                if (nameStr == null) {
                    nameStr = "\"" + attrNames[i] + "\"";
                } else {
                    nameStr += ", \"" + attrNames[i] + "\"";
                }
            }
            throw new MBeanAgentException("Couldn't get MBean \"" + mbeanName +
                                          "\" attributes [" + nameStr + "]",
                                          jme);
        }

        Object[] vals = new Object[attrNames.length];
        while (iter.hasNext()) {
            Attribute attr = (Attribute) iter.next();

            for (int i = 0; i < attrNames.length; i++) {
                if (attrNames[i].equals(attr.getName())) {
                    vals[i] = fixAttribute(attr);
                    break;
                }
            }
        }

        return vals;
    }

    public void handleNotification(Notification notification,
                                   Object handback)
    {
        if (!(notification instanceof MBeanServerNotification)) {
            LOG.info("Ignoring notification class " +
                     notification.getClass().getName());
            return;
        }

        MBeanServerNotification mNote = (MBeanServerNotification) notification;
        ObjectName beanObjName = mNote.getMBeanName();

        final String regType =
            MBeanServerNotification.REGISTRATION_NOTIFICATION;
        final String unregType =
            MBeanServerNotification.UNREGISTRATION_NOTIFICATION;

        if (mNote.getType().equals(regType)) {
            try {
                registerBean(beanObjName);
            } catch (JMException jme) {
                LOG.error("Couldn't register " +
                          beanObjName.getKeyProperty("name"), jme);
            }
        } else if (mNote.getType().equals(unregType)) {
            unregisterBean(beanObjName);
        } else {
            LOG.error("Unrecognized notification type " + mNote.getType());
        }
    }

    public String[] listGetters(String mbeanName)
        throws MBeanAgentException
    {
        if (!beans.containsKey(mbeanName)) {
            throw new MBeanAgentException("Unknown MBean \"" + mbeanName +
                                          "\"");
        }

        ObjectName objName = (ObjectName) beans.get(mbeanName);

        MBeanAttributeInfo[] attrInfo;
        try {
            attrInfo = server.getMBeanInfo(objName).getAttributes();
        } catch (JMException jme) {
            throw new MBeanAgentException("Couldn't get MBean \"" + mbeanName +
                                          "\" info", jme);
        }

        int numGetters = 0;
        for (int i = 0; i < attrInfo.length; i++) {
            if (attrInfo[i].isReadable() || attrInfo[i].isIs()) {
                numGetters++;
            }
        }

        String[] names = new String[numGetters];
        for (int i = 0, n = 0; i < attrInfo.length; i++) {
            if (attrInfo[i].isReadable() || attrInfo[i].isIs()) {
                names[n++] = attrInfo[i].getName();
            }
        }

        return names;
    }

    public String[] listMBeans()
        throws MBeanAgentException
    {
        String[] list = new String[beans.size()];

        Iterator iter = beans.keySet().iterator();
        for (int i = 0; iter.hasNext(); i++) {
            list[i] = (String) iter.next();
        }

        return list;
    }

    public boolean isStopped()
    {
        return false;
    }

    public void postDeregister()
    {
        // do nothing
    }

    public void postRegister(Boolean registrationDone)
    {
        if (delegateName == null) {
            try {
                delegateName = new ObjectName("JMImplementation",
                                              "type", "MBeanServerDelegate");
            } catch (JMException jme) {
                jme.printStackTrace();
                return;
            }
        }

        Object context = null;
        try {
            server.addNotificationListener(delegateName, this, null, context);
        } catch (InstanceNotFoundException infe) {
            infe.printStackTrace();
        }
    }

    public void preDeregister()
    {
        Object context = null;
        try {
            server.removeNotificationListener(delegateName, this);
        } catch (JMException jme) {
            jme.printStackTrace();
        }
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name)
    {
        this.name = name;
        this.server = server;

        return name;
    }

    private void registerBean(ObjectName beanObjName)
        throws JMException
    {
        String key = (String) beanObjName.getKeyProperty("name");
        if (beans.containsKey(key)) {
            ObjectName oldObjName = (ObjectName) beans.get(key);

            if (!beanObjName.equals(oldObjName)) {
                LOG.error("Overwriting MBean \"" + key + "\" objectName \"" +
                          oldObjName + "\" with \"" + beanObjName + "\"");
            }
        }

        beans.put(key, beanObjName);
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public void start()
        throws MBeanAgentException
    {
        if (port <= 0) {
            throw new MBeanAgentException("Bad XML-RPC port " + port);
        } else if (webServer != null) {
            throw new MBeanAgentException("XML-RPC server is already running");
        }

        MBeanHandler.setServer(this);

        webServer = new WebServer(port);

        XmlRpcServer xmlRpcServer = webServer.getXmlRpcServer();

        PropertyHandlerMapping phm = new PropertyHandlerMapping();
        try {
            phm.addHandler("mbean", MBeanHandler.class);
        } catch (XmlRpcException xre) {
            throw new MBeanAgentException("Could not set XML-RPC" +
                                          " server mapping", xre);
        }

        xmlRpcServer.setHandlerMapping(phm);

        XmlRpcServerConfigImpl serverConfig =
            (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
        serverConfig.setEnabledForExtensions(true);
        serverConfig.setContentLengthOptional(false);

        try {
            webServer.start();
        } catch (IOException ioe) {
            throw new MBeanAgentException("Could not start XML-RPC server",
                                          ioe);
        }
    }

    public void stop()
        throws MBeanAgentException
    {
        if (webServer == null) {
            throw new MBeanAgentException("XML-RPC server is not running");
        }

        webServer.shutdown();
        webServer = null;

        MBeanHandler.clearServer(this);
    }

    private void unregisterBean(ObjectName beanObjName)
    {
        String key = (String) beanObjName.getKeyProperty("name");
        beans.remove(key);
        LOG.info("Removed bean " + key);
    }
}
