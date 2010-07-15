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
    public void forcedStopProcessing()
        throws Exception
    {
        // cannot forceably stop splicer
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
    public String getState()
    {
        return splicer.getStateString();
    }

    /**
     * Is this connector running?
     *
     * @return <tt>true</tt> if this connector is running
     */
    public boolean isRunning()
    {
        return (splicer.getState() != Splicer.STOPPED);
    }

    /**
     * Is this a splicer?
     *
     * @return <tt>true</tt>
     */
    public boolean isSplicer()
    {
        return true;
    }

    /**
     * Is this connector stopped?
     *
     * @return <tt>true</tt> if this connector is stopped
     */
    public boolean isStopped()
    {
        return (splicer.getState() == Splicer.STOPPED);
    }

    /**
     * Start background threads.
     *
     * @throws Exception if there is a problem
     */
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
    public String toString()
    {
        return "Splicer[" + getType() + "=>" + splicer + "]";
    }
}
