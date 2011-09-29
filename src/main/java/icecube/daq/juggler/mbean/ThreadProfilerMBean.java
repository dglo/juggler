package icecube.daq.juggler.mbean;

import java.util.HashMap;

/**
 * MBean methods for JVM thread statistics monitor.
 */
public interface ThreadProfilerMBean
{
    HashMap<String, Long> getThreadCPUTime();
    HashMap<String, Long> getThreadInfo();
    //HashMap<String, Long> getThreadUserTime();
}
