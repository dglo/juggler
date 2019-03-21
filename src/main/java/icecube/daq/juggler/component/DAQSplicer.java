package icecube.daq.juggler.component;

import icecube.daq.splicer.Splicer;

/***
 * DAQ splicer.
 */
public class DAQSplicer
    extends DAQConnector
{
    /** splicer. */
    private Splicer splicer;

    /**
     * Create a DAQ splicer 'connector'.
     *
     * @param splicer splicer
     */
    DAQSplicer(Splicer splicer)
    {
        super("splicer", false);

        this.splicer = splicer;
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
        splicer.dispose();
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
        // cannot forceably stop splicer
    }

    /**
     * Get number of active channels.
     *
     * @return number of channels
     */
    @Override
    public int getNumberOfChannels()
    {
        return splicer.getStrandCount();
    }

    /**
     * Get splicer associated with this connector.
     *
     * @return splicer
     */
    public Splicer getSplicer()
    {
        return splicer;
    }

    /**
     * Get current splicer state.
     *
     * @return state string
     */
    @Override
    public String getState()
    {
        return splicer.getState().name();
    }

    /**
     * Is this connector running?
     *
     * @return <tt>true</tt> if this connector is running
     */
    @Override
    public boolean isRunning()
    {
        return (splicer.getState() != Splicer.State.STOPPED);
    }

    /**
     * Is this a splicer?
     *
     * @return <tt>true</tt>
     */
    @Override
    public boolean isSplicer()
    {
        return true;
    }

    /**
     * Is this connector stopped?
     *
     * @return <tt>true</tt> if this connector is stopped
     */
    @Override
    public boolean isStopped()
    {
        return (splicer.getState() == Splicer.State.STOPPED);
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
        // do nothing
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
        // do nothing
    }

    /**
     * String representation.
     *
     * @return debugging string
     */
    @Override
    public String toString()
    {
        return "Splicer[" + getType() + "=>" + splicer + "]";
    }
}
