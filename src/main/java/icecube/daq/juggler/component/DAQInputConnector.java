package icecube.daq.juggler.component;

import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.PayloadInputEngine;

import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;

/***
 * DAQ input connector.
 */
public class DAQInputConnector
    extends DAQConnector
{
    /** input engine. */
    private DAQComponentInputProcessor engine;

    /**
     * Create a DAQ input connector.
     *
     * @param type connector type
     * @param engine input engine
     */
    DAQInputConnector(String type, DAQComponentInputProcessor engine)
    {
        super(type);

        this.engine = engine;
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
     * Get input engine associated with this connector.
     *
     * @return input engine
     */
    public PayloadInputEngine getInputEngine()
    {
        return (PayloadInputEngine) engine;
    }

    /**
     * Get connector port.
     *
     * @return port
     */
    public int getPort()
    {
        return engine.getServerPort();
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
     * Is this an input engine?
     *
     * @return <tt>true</tt>
     */
    public boolean isInput()
    {
        return true;
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
     * Start server socket for this engine.
     *
     * @param bufCache buffer cache manager
     *
     * @throws IOException if there is a problem
     */
    public void startServer(IByteBufferCache bufCache)
        throws IOException
    {
        engine.startServer(bufCache);
    }

    /**
     * String representation.
     *
     * @return debugging string
     */
    public String toString()
    {
        return "InConn[" + getType() + "=>" + engine + "]";
    }
}
