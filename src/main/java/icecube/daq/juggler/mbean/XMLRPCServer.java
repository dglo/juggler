package icecube.daq.juggler.mbean;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
import org.apache.xmlrpc.webserver.DAQWebServer;
import org.apache.xmlrpc.webserver.XmlRpcStatisticsServer;
import org.apache.xmlrpc.webserver.WebServer;

/**
 * XML-RPC adapter for JMX MBeans.
 */
class XMLRPCServer
    implements MBeanData, MBeanRegistration, NotificationListener,
               XMLRPCServerMBean
{
    private static final Log LOG = LogFactory.getLog(XMLRPCServer.class);

    private static ObjectName delegateName;

    private MBeanServer server;

    private int port = Integer.MIN_VALUE;
    private WebServer webServer;

    private HashMap<String, ObjectName> beans =
        new HashMap<String, ObjectName>();

    private static Object fixArray(Object array)
    {
        boolean forceString = false;

        final int len = Array.getLength(array);
        if (len == 0) {
            Class compType = array.getClass().getComponentType();
            if (compType == long.class) {
                compType = Integer.class;
            }
            return Array.newInstance(compType, len);
        }

        Object newArray = null;
        for (int i = 0; i < len; i++) {
            Object elem = fixValue(Array.get(array, i));

            if (newArray == null) {
                newArray = Array.newInstance(elem.getClass(), len);
            }

            try {
                Array.set(newArray, i, (elem == null || !forceString ? elem :
                                        elem.toString()));
            } catch (IllegalArgumentException ill) {
                String[] objArray = new String[len];
                for (int j = 0; j < i; j++) {
                    Object obj = Array.get(newArray, j);

                    String objStr;
                    if (obj == null) {
                        objStr = null;
                    } else {
                        objStr = obj.toString();
                    }
                    objArray[j] = objStr;
                }
                objArray[i] = elem.toString();
                forceString = true;
                newArray = objArray;
            }
        }

        return newArray;
    }

    private static AbstractMap fixMap(AbstractMap map)
    {
        for (Map.Entry entry : (Set<Map.Entry>)map.entrySet()) {
            entry.setValue(fixValue(entry.getValue()));
        }

        return map;
    }

    private static Object fixAttribute(Object obj)
    {
        if (obj == null || !(obj instanceof Attribute)) {
            return fixValue(obj);
        }

        return fixValue(((Attribute) obj).getValue());
    }

    private static Object fixValue(Object val)
    {
        if (val == null) {
            return null;
        }

        if (val.getClass().isArray()) {
            return fixArray(val);
        } else if (val instanceof Byte) {
            return Integer.valueOf(((Byte) val).intValue());
        } else if (val instanceof Character) {
            char[] array = new char[] {((Character) val).charValue() };
            return new String(array);
        } else if (val instanceof Short) {
            return Integer.valueOf(((Short) val).intValue());
        } else if (val instanceof Long) {
            long lVal = ((Long) val).longValue();
            if (lVal < (long) Integer.MIN_VALUE ||
                lVal > (long) Integer.MAX_VALUE)
            {
                return val.toString();
            }

            return Integer.valueOf((int) lVal);
        } else if (val instanceof Float) {
            return Double.valueOf(((Float) val).doubleValue());
        } else if (val instanceof AbstractMap) {
            return fixMap((AbstractMap) val);
        }

        return val;
    }

    @Override
    public Object get(String mbeanName, String attrName)
        throws MBeanAgentException
    {
        if (!beans.containsKey(mbeanName)) {
            throw new MBeanAgentException("Unknown MBean \"" + mbeanName +
                                          "\"");
        }

        ObjectName objName = beans.get(mbeanName);

        Object attrVal;
        try {
            attrVal = server.getAttribute(objName, attrName);
        } catch (JMException jme) {
            throw new MBeanAgentException("Couldn't get MBean \"" + mbeanName +
                                          "\" attribute \"" + attrName + "\"",
                                          jme);
        }

        return fixAttribute(attrVal);
    }

    public HashMap getAttributes(String mbeanName, String[] attrNames)
        throws MBeanAgentException
    {
        if (!beans.containsKey(mbeanName)) {
            throw new MBeanAgentException("Unknown MBean \"" + mbeanName +
                                          "\"");
        }

        ObjectName objName = beans.get(mbeanName);

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

        HashMap map = new HashMap();
        while (iter.hasNext()) {
            Attribute attr = (Attribute) iter.next();

            for (int i = 0; i < attrNames.length; i++) {
                if (attrNames[i].equals(attr.getName())) {
                    Object val;
                    try {
                        val = fixAttribute(attr);
                    } catch (IllegalArgumentException ill) {
                        LOG.error("Couldn't fix MBean " + mbeanName +
                                  " attribute " + attr.getName());
                        throw ill;
                    }
                    if (val != null) {
                        map.put(attr.getName(), val);
                    }
                    break;
                }
            }
        }

        return map;
    }

    @Override
    public Map<String, Object> getDictionary()
        throws MBeanAgentException
    {
        HashMap<String, Object> allData = new HashMap<String, Object>();

        for (String mbeanName : beans.keySet()) {
            final String[] attrNames = listGetters(mbeanName);

            Map attrs = getAttributes(mbeanName, attrNames);
            allData.put(mbeanName, attrs);
        }

        return allData;
    }

    Object[] getList(String mbeanName, String[] attrNames)
        throws MBeanAgentException
    {
        if (!beans.containsKey(mbeanName)) {
            throw new MBeanAgentException("Unknown MBean \"" + mbeanName +
                                          "\"");
        }

        ObjectName objName = beans.get(mbeanName);

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
                    try {
                        vals[i] = fixAttribute(attr);
                    } catch (IllegalArgumentException ill) {
                        LOG.error("Couldn't fix MBean " + mbeanName +
                                  " attribute " + attr.getName());
                        throw ill;
                    }
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
            if (LOG.isInfoEnabled()) {
                LOG.info("Ignoring notification class " +
                         notification.getClass().getName());
            }
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

    @Override
    public String[] listGetters(String mbeanName)
        throws MBeanAgentException
    {
        if (!beans.containsKey(mbeanName)) {
            throw new MBeanAgentException("Unknown MBean \"" + mbeanName +
                                          "\"");
        }

        ObjectName objName = beans.get(mbeanName);

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

    @Override
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

    @Override
    public void postDeregister()
    {
        // do nothing
    }

    @Override
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

    @Override
    public void preDeregister()
    {
        try {
            server.removeNotificationListener(delegateName, this);
        } catch (JMException jme) {
            jme.printStackTrace();
        }
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name)
    {
        this.server = server;

        return name;
    }

    private void registerBean(ObjectName beanObjName)
        throws JMException
    {
        String key = (String) beanObjName.getKeyProperty("name");
        if (beans.containsKey(key)) {
            ObjectName oldObjName = beans.get(key);

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

    public void start(MBeanAgent agent)
        throws MBeanAgentException
    {
        if (port <= 0) {
            throw new MBeanAgentException("Bad XML-RPC port " + port);
        } else if (webServer != null) {
            throw new MBeanAgentException("XML-RPC server is already running");
        }

        MBeanHandler.setServer(this);

        DAQWebServer tmpServer = new DAQWebServer("MBean", port);

        XmlRpcStatisticsServer xmlRpcServer =
            (XmlRpcStatisticsServer) tmpServer.getXmlRpcServer();

        agent.addBean("xmlrpcServer", xmlRpcServer);

        webServer = tmpServer;

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
        if (LOG.isInfoEnabled()) {
            LOG.info("Removed bean " + key);
        }
    }
}
