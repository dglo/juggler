package icecube.daq.juggler.mbean;

import java.util.HashMap;

/**
 * System statistics monitor.
 */
public interface SystemStatisticsMBean
{
    public double[] getLoadAverage();
    public HashMap getAvailableDiskSpace();
}
