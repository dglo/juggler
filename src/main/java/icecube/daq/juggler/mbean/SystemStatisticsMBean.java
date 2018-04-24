package icecube.daq.juggler.mbean;

import java.util.Map;
import java.util.TreeMap;

/**
 * System statistics monitor.
 */
public interface SystemStatisticsMBean
{
    Map getAvailableDiskSpace();
    Map<String, long[]> getCPUStatistics();
    double[] getLoadAverage();
    //TreeMap<String, String> getNetworkIO();
    //HashMap getProcessMemory();
}
