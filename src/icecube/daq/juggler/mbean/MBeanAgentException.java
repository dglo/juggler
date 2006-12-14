package icecube.daq.juggler.mbean;

/**
 * MBean agent exception.
 */
public class MBeanAgentException
    extends Exception
{
    /**
     * Create an MBean agent exception.
     */
    public MBeanAgentException()
    {
        super();
    }

    /**
     * Create an MBean agent exception.
     *
     * @param msg error message
     */
    public MBeanAgentException(String msg)
    {
        super(msg);
    }

    /**
     * Create an MBean agent exception.
     *
     * @param msg error message
     * @param thr encapsulated exception
     */
    public MBeanAgentException(String msg, Throwable thr)
    {
        super(msg, thr);
    }

    /**
     * Create an MBean agent exception.
     *
     * @param thr encapsulated exception
     */
    public MBeanAgentException(Throwable thr)
    {
        super(thr);
    }
}
