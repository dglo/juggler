package icecube.daq.juggler.component;

import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.OutputChannel;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.SourceIdRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;

/***
 * DAQ output connector.
 */
public class DAQOutputConnector
    extends DAQConnector
{
    /** output engine. */
    private DAQComponentOutputProcess engine;
    /** <tt>true</tt> if allowed to connect to multiple input engines */
    private boolean allowMulti;

    /**
     * Create a DAQ output connector.
     *
     * @param type connector type
     * @param engine output engine
     */
    DAQOutputConnector(String type, DAQComponentOutputProcess engine)
    {
        this(type, engine, false);
    }

    /**
     * Create a DAQ output connector.
     *
     * @param type connector type
     * @param engine output engine
     * @param allowMultipleConnections <tt>true</tt> if this output connector
     *                                 can connect to multiple input engines
     */
    DAQOutputConnector(String type, DAQComponentOutputProcess engine,
                       boolean allowMultipleConnections)
    {
        this(type, engine, allowMultipleConnections, false);
    }

    /**
     * Create a DAQ output connector.
     *
     * @param type connector type
     * @param engine output engine
     * @param allowMultipleConnections <tt>true</tt> if this output connector
     *                                 can connect to multiple input engines
     */
    DAQOutputConnector(String type, DAQComponentOutputProcess engine,
                       boolean allowMultipleConnections, boolean optional)
    {
        super(type, optional);

        this.engine = engine;
        this.allowMulti = allowMultipleConnections;
    }

    /**
     * Connect to a remote input server.
     *
     * @param bufMgr buffer manager to be used by the new transmit channel
     * @param conn connection description
     *
     * @return new output channel
     *
     * @throws IOException if there was a problem
     */
    public OutputChannel connect(IByteBufferCache bufMgr, Connection conn)
        throws IOException
    {
        DAQComponentOutputProcess.ChannelRequirements channelRequirement =
                engine.getChannelRequirement();

        InetSocketAddress addr =
            new InetSocketAddress(conn.getHost(), conn.getPort());

        SocketChannel chan;
        try {
            chan = SocketChannel.open(addr);
        } catch (UnresolvedAddressException uae) {
            throw new IllegalArgumentException("Unresolved address " +
                                               conn.getHost() + ":" +
                                               conn.getPort(), uae);
        }

        switch (channelRequirement)
        {
            case NON_BLOCKING:
                chan.configureBlocking(false);
                break;
            case BLOCKING:
                chan.configureBlocking(true);
                break;
            default:
                throw new Error("Unknown enum: " + channelRequirement);
        }
        final String name = conn.getComponentName();
        final int num = conn.getComponentNumber();

        final int srcId =
            SourceIdRegistry.getSourceIDFromNameAndId(name, num % 1000);
        return engine.connect(bufMgr, chan, srcId);
    }

    /**
     * Are multiple connections from this engine allowed?
     *
     * @return <tt>true</tt> if this output engine can connect to multiple
     *         input engines
     */
    public boolean allowMultipleConnections()
    {
        return allowMulti;
    }

    /**
     * Destroy this connector.
     *
     * @throws Exception if there was a problem
     */
    @Override
    public void destroy()
        throws Exception
    {
        if (!engine.isDestroyed()) {
            if (!engine.isStopped()) {
                engine.forcedStopProcessing();
            }

            engine.destroyProcessor();
        }
    }

    /**
     * Disconnect output engine from remote input server(s).
     *
     * @throws IOException if there is a problem
     */
    public void disconnect()
        throws IOException
    {
        engine.disconnect();
    }

    /**
     * Force engine to stop processing data.
     *
     * @throws Exception if there is a problem
     */
    @Override
    public void forcedStopProcessing()
        throws Exception
    {
        engine.forcedStopProcessing();
    }

    /**
     * Get number of active channels.
     *
     * @return number of channels
     */
    @Override
    public int getNumberOfChannels()
    {
        return engine.getNumberOfChannels();
    }

    /**
     * Get current engine state.
     *
     * @return state string
     */
    @Override
    public String getState()
    {
        return engine.getPresentState();
    }

    /**
     * Is the output engine connected to an input engine?
     *
     * @return <tt>true</tt> if the output engine is connected
     */
    public boolean isConnected()
    {
        return engine.isConnected();
    }

    /**
     * Is this an output engine?
     *
     * @return <tt>true</tt>
     */
    @Override
    public boolean isOutput()
    {
        return true;
    }

    /**
     * Is this connector running?
     *
     * @return <tt>true</tt> if this connector is running
     */
    @Override
    public boolean isRunning()
    {
        return engine.isRunning();
    }

    /**
     * Is this connector stopped?
     *
     * @return <tt>true</tt> if this connector is stopped
     */
    @Override
    public boolean isStopped()
    {
        return engine.isStopped();
    }

    /**
     * Start background threads.
     *
     * @throws Exception if there is a problem
     */
    @Override
    public void start()
        throws Exception
    {
        engine.start();
    }

    /**
     * Start processing data.
     *
     * @throws Exception if there is a problem
     */
    @Override
    public void startProcessing()
        throws Exception
    {
        engine.startProcessing();
    }

    /**
     * String representation.
     *
     * @return debugging string
     */
    @Override
    public String toString()
    {
        return "OutConn[" + getType() + "=>" + engine + "]";
    }
}
