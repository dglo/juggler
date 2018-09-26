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

    @Override
    public ByteBuffer acquireBuffer(int i0)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void flush()
    {
        // do nothing
    }

    @Override
    public int getCurrentAquiredBuffers()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getCurrentAquiredBytes()
    {
        throw new Error("Unimplemented");
    }


    @Override
    public boolean getIsCacheBounded()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getMaxAquiredBytes()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public String getName()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getTotalBuffersAcquired()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getTotalBuffersCreated()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getTotalBuffersReturned()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getTotalBytesInCache()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public boolean isBalanced()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void receiveByteBuffer(ByteBuffer x0)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void returnBuffer(ByteBuffer x0)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void returnBuffer(int x0)
    {
        throw new Error("Unimplemented");
    }
}
