package icecube.daq.juggler.component;

/**
 * This interface provides the ByteBufferCache paramaters
 */
public interface DAQCompConfig
{
    /**
     * Get byte buffer granularity.
     *
     * @return granularity
     */
    int getGranularity();

    /**
     * Get maximum numer of bytes acquired by this cache.
     *
     * @return maximum acquired bytes
     */
    long getMaxAcquireBytes();

    /**
     * Get maximum number of bytes allocated for this cache
     *
     * @return maximum number of cached bytes
     */
    long getMaxCacheBytes();
}
