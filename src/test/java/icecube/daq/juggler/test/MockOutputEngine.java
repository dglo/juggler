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

    public PayloadTransmitChannel addDataChannel(WritableByteChannel x0,
                                                 IByteBufferCache x1)
    {
        throw new Error("Unimplemented");
    }

    public PayloadTransmitChannel connect(IByteBufferCache cache,
                                          WritableByteChannel chan, int srcId)
        throws IOException
    {
        connected = true;
        return null;
    }

    public void destroyProcessor()
    {
        destroyed = true;
    }

    public void disconnect()
        throws IOException
    {
        connected = false;
    }

    public void forcedStopProcessing()
    {
        running = false;
    }

    public OutputChannel getChannel()
    {
        throw new Error("Unimplemented");
    }

    public String getPresentState()
    {
        return state;
    }

    public long[] getRecordsSent()
    {
        throw new Error("Unimplemented");
    }

    public boolean isConnected()
    {
        return connected;
    }

    public boolean isDestroyed()
    {
        return destroyed;
    }

    public boolean isRunning()
    {
        return running;
    }

    public boolean isStopped()
    {
        return !running;
    }

    public void registerComponentObserver(DAQComponentObserver x0)
    {
        throw new Error("Unimplemented");
    }

    public void sendLastAndStop()
    {
        running = false;
    }

    public void setState(String state)
    {
        this.state = state;
    }

    public void start()
    {
        // do nothing
    }

    public void startProcessing()
    {
        running = true;
    }
}
