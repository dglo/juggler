package icecube.daq.juggler.mbean;

import java.util.Map;

public interface MBeanData
{
    Object get(String mbeanName, String attrName)
        throws MBeanAgentException;
    Map getAttributes(String mbeanName, String[] attrNames)
        throws MBeanAgentException;
    Map<String, Object> getDictionary()
        throws MBeanAgentException;
    String[] listGetters(String mbeanName)
        throws MBeanAgentException;
    String[] listMBeans()
        throws MBeanAgentException;
}
