package icecube.daq.juggler.alert;

import java.util.Map;

/**
 * Handle alerts
 */
public interface Alerter
{
    /** Default service name */
    String DEFAULT_SERVICE = "pdaq";

    /** Send immediately over ITS */
    int PRIO_ITS = 1;
    /** Send quickly over email */
    int PRIO_EMAIL = 2;
    /** Cache and send via SPADE */
    int PRIO_SCP = 3;
    /** Send whenever we get a chance */
    int PRIO_DEBUG = 4;

    /**
     * Close any open files/sockets.
     */
    void close();

    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    boolean isActive();

    /**
     * Send an alert.
     *
     * @param type alert type
     * @param priority priority level
     * @param condition condition name for this alert
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    void send(String type, int priority, String condition,
              Map<String, Object> vars)
        throws AlertException;
}