package icecube.daq.juggler.component;

import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.SourceIdRegistry;

import java.io.IOException;

import java.net.InetSocketAddress;

import java.nio.channels.SocketChannel;

/***
 * DAQ output connector.
 */
public class DAQOutputConnector
    extends DAQConnector
{
    /** output engine. */
    private PayloadOutputEngine engine;

    /**
     * Create a DAQ output connector.
     *
     * @param type connector type
     * @param engine output engine
     */
    DAQOutputConnector(String type, PayloadOutputEngine engine)
    {
        super(type);

        this.engine = engine;
    }

    /**
     * Connect to a remote input server.
     *
     * @param bufMgr buffer manager to be used by the new transmit channel
     * @param conn connection description
     *
     * @return new transmit channel
     *
     * @throws IOException if there was a problem
     */
    public PayloadTransmitChannel connect(IByteBufferCache bufMgr,
                                          Connection conn)
        throws IOException
    {
        InetSocketAddress addr =
            new InetSocketAddress(conn.getHost(), conn.getPort());

        SocketChannel chan = SocketChannel.open(addr);
        chan.configureBlocking(false);

        final String name = conn.getComponentName();
        final int num = conn.getComponentNumber();

        final int srcId =
            SourceIdRegistry.getSourceIDFromNameAndId(name, num);
        return engine.connect(bufMgr, chan, srcId);
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
     * Destroy this connector.
     *
     * @throws Exception if there was a problem
     */
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
     * Force engine to stop processing data.
     *
     * @throws Exception if there is a problem
     */
    public void forcedStopProcessing()
        throws Exception
    {
        engine.forcedStopProcessing();
    }

    /**
     * Get output engine associated with this connector.
     *
     * @return output engine
     */
    public PayloadOutputEngine getOutputEngine()
    {
        return engine;
    }

    /**
     * Get current engine state.
     *
     * @return state string
     */
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
     * Is this connector running?
     *
     * @return <tt>true</tt> if this connector is running
     */
    public boolean isRunning()
    {
        return engine.isRunning();
    }

    /**
     * Is this connector stopped?
     *
     * @return <tt>true</tt> if this connector is stopped
     */
    public boolean isStopped()
    {
        return engine.isStopped();
    }

    /**
     * Start background threads.
     *
     * @throws Exception if there is a problem
     */
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
    public String toString()
    {
        return "OutConn[" + getType() + "=>" + engine + "]";
    }
}
