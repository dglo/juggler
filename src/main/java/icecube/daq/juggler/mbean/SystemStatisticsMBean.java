package icecube.daq.juggler.mbean;

import java.util.HashMap;

/**
 * System statistics monitor.
 */
public interface SystemStatisticsMBean
{
    double[] getLoadAverage();
    HashMap getAvailableDiskSpace();
}
