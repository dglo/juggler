package icecube.daq.juggler.toybox;

import icecube.daq.juggler.component.DAQCompConfig;

public class ToyConfig
    implements DAQCompConfig
{
    private int granularity;
    private long maxCacheBytes;
    private long maxAcquireBytes;

    public ToyConfig(int granularity, long maxCacheBytes, long maxAcquireBytes)
    {
        this.granularity = granularity;
        this.maxCacheBytes = maxCacheBytes;
        this.maxAcquireBytes = maxAcquireBytes;
    }

    public int getGranularity()
    {
        return granularity;
    }

    public long getMaxAcquireBytes()
    {
        return maxAcquireBytes;
    }

    public long getMaxCacheBytes()
    {
        return maxCacheBytes;
    }
}
