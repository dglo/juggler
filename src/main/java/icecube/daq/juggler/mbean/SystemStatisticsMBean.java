package icecube.daq.juggler.mbean;

import java.util.HashMap;
import java.util.TreeMap;

/**
 * System statistics monitor.
 */
public interface SystemStatisticsMBean
{
    HashMap getAvailableDiskSpace();
    double[] getLoadAverage();
    //TreeMap<String, String> getNetworkIO();
    //HashMap getProcessMemory();
}
