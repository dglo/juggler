package icecube.daq.juggler.mbean;

/**
 * JVM memory statistics.
 */
public class MemoryStatistics
    implements MemoryStatisticsMBean
{
    /** Memory size designators. */
    private static final String[] MEMORY_SUFFIX = {"", "K", "G", "T" };

    /** Keep pointer to runtime data. */
    private Runtime rt = Runtime.getRuntime();

    /**
     * Simple constructor.
     */
    public MemoryStatistics()
    {
    }

    /**
     * Flush unused data from garbage collector.
     */
    public void flushMemory()
    {
        rt.runFinalization();
        rt.gc();
    }

    /**
     * Format byte size as a string.
     *
     * @param bytes
     */
    private static String formatBytes(long bytes)
    {
        int sufIdx = 0;
        while (bytes > 1024 * 1024 && sufIdx < MEMORY_SUFFIX.length - 1) {
            bytes /= 1024;
            sufIdx++;
        }

        return Long.toString(bytes) + MEMORY_SUFFIX[sufIdx];
    }

    public long[] getMemoryStatistics()
    {
        long total = rt.totalMemory();
        long free = rt.freeMemory();

        return new long[] {total - free, total };
    }

    /**
     * Return description of current statistics.
     *
     * @return description of current statistics
     */
    public String toString()
    {
        long free = rt.freeMemory();
        long total = rt.totalMemory();

        return (formatBytes(total - free) + " used, " +
                formatBytes(free) + " of " +
                formatBytes(total) + " free.");
    }
}
