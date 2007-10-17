package icecube.daq.juggler.mbean;

import java.util.HashMap;

public interface MBeanData
{
    HashMap getAttributes(String mbeanName, String[] attrNames)
        throws MBeanAgentException;
    String[] listGetters(String mbeanName)
        throws MBeanAgentException;
    String[] listMBeans()
        throws MBeanAgentException;
}
