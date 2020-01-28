package icecube.daq.juggler.mbean;

/**
 * JVM memory statistics monitor.
 */
public interface MemoryStatisticsMBean
{
    long[] getMemoryStatistics();
}
