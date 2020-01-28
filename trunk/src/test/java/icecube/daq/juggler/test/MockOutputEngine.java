package icecube.daq.juggler.test;

import icecube.daq.io.DAQComponentObserver;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.OutputChannel;
import icecube.daq.io.PayloadTransmitChannel;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MockOutputEngine
    implements DAQComponentOutputProcess
{
    private final Log LOG = LogFactory.getLog(MockOutputEngine.class);

    private boolean connected;
    private boolean running;
    private boolean destroyed;
    private String state;

    public MockOutputEngine()
    {
    }

    public PayloadTransmitChannel addDataChannel(WritableByteChannel chan,
                                                 IByteBufferCache bufMgr,
                                                 String name)
    {
        return addDataChannel(chan, bufMgr, name, Integer.MAX_VALUE);
    }

    public PayloadTransmitChannel addDataChannel(WritableByteChannel chan,
                                                 IByteBufferCache bufMgr,
                                                 String name,
                                                 int maxDepth)
    {
        throw new Error("Unimplemented");
    }

    public PayloadTransmitChannel connect(IByteBufferCache cache,
                                          WritableByteChannel chan, int srcId)
        throws IOException
    {
        return connect(cache, chan, srcId, Integer.MAX_VALUE);
    }

    public PayloadTransmitChannel connect(IByteBufferCache cache,
                                          WritableByteChannel chan, int srcId,
                                          int maxDepth)
        throws IOException
    {
        connected = true;
        return null;
    }

    @Override
    public void destroyProcessor()
    {
        destroyed = true;
    }

    @Override
    public void disconnect()
        throws IOException
    {
        connected = false;
    }

    @Override
    public void forcedStopProcessing()
    {
        running = false;
    }

    @Override
    public OutputChannel getChannel()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getNumberOfChannels()
    {
        return 1;
    }

    @Override
    public String getPresentState()
    {
        return state;
    }

    @Override
    public long getRecordsSent()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getTotalRecordsSent()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public boolean isConnected()
    {
        return connected;
    }

    @Override
    public boolean isDestroyed()
    {
        return destroyed;
    }

    @Override
    public boolean isRunning()
    {
        return running;
    }

    @Override
    public boolean isStopped()
    {
        return !running;
    }

    @Override
    public void registerComponentObserver(DAQComponentObserver x0)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void sendLastAndStop()
    {
        running = false;
    }

    public void setState(String state)
    {
        this.state = state;
    }

    @Override
    public void start()
    {
        // do nothing
    }

    @Override
    public void startProcessing()
    {
        running = true;
    }
}
