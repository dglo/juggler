package icecube.daq.juggler.component;

/**
 * DAQ component exception.
 */
public class DAQCompException
    extends Exception
{
    /**
     * Create a DAQ component exception.
     *
     * @param msg error message
     */
    public DAQCompException(String msg)
    {
        super(msg);
    }

    /**
     * Create a DAQ component exception.
     *
     * @param msg error message
     * @param thr encapsulated exception
     */
    public DAQCompException(String msg, Throwable thr)
    {
        super(msg, thr);
    }
}
