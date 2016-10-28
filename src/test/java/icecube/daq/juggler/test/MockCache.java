package icecube.daq.juggler.test;

import icecube.daq.payload.IByteBufferCache;

import java.nio.ByteBuffer;

public class MockCache
    implements IByteBufferCache
{
    private String type;

    public MockCache(String type)
    {
        this.type = type;
    }

    public String getType()
    {
        return type;
    }

    public ByteBuffer acquireBuffer(int i0)
    {
        throw new Error("Unimplemented");
    }

    public void flush()
    {
        // do nothing
    }

    public int getCurrentAquiredBuffers()
    {
        throw new Error("Unimplemented");
    }

    public long getCurrentAquiredBytes()
    {
        throw new Error("Unimplemented");
    }


    public boolean getIsCacheBounded()
    {
        throw new Error("Unimplemented");
    }

    public long getMaxAquiredBytes()
    {
        throw new Error("Unimplemented");
    }

    public String getName()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersAcquired()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersCreated()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersReturned()
    {
        throw new Error("Unimplemented");
    }

    public long getTotalBytesInCache()
    {
        throw new Error("Unimplemented");
    }

    public boolean isBalanced()
    {
        throw new Error("Unimplemented");
    }

    public void receiveByteBuffer(ByteBuffer x0)
    {
        throw new Error("Unimplemented");
    }

    public void returnBuffer(ByteBuffer x0)
    {
        throw new Error("Unimplemented");
    }

    public void returnBuffer(int x0)
    {
        throw new Error("Unimplemented");
    }
}
