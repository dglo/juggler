package icecube.daq.juggler.alert;

/**
 * Alert exception
 */
public class AlertException
    extends Exception
{
    /**
     * Create an alert exception.
     *
     * @param msg error message
     */
    public AlertException(String msg)
    {
        super(msg);
    }

    /**
     * Create an alert exception.
     *
     * @param thr encapsulated exception
     */
    public AlertException(Throwable thr)
    {
        super(thr);
    }

    /**
     * Create an alert exception.
     *
     * @param msg error message
     * @param thr encapsulated exception
     */
    public AlertException(String msg, Throwable thr)
    {
        super(msg, thr);
    }
}