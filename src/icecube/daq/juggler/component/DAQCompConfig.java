package icecube.daq.juggler.component;

/**
 * This interface provides the ByteBufferCache paramaters 
 */
public interface DAQCompConfig {

    public int getGranularity();

    public long getMaxCacheBytes();

    public long getMaxAcquireBytes();

}
