package icecube.daq.juggler.mbean;

import java.util.HashMap;
//import java.util.TreeMap;

/**
 * System statistics monitor.
 */
public interface SystemStatisticsMBean
{
    double[] getLoadAverage();
    HashMap getAvailableDiskSpace();
    //TreeMap<String, String> getNetworkIO();
}
