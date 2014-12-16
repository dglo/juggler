package icecube.daq.juggler.alert;

import icecube.daq.payload.IUTCTime;

import java.util.Calendar;
import java.util.Map;

/**
 * Handle alerts
 */
public interface Alerter
{
    /** Default service name */
    String DEFAULT_SERVICE = "pdaq";

    /** Alert priority */
    public enum Priority {
        /** Send immediately over ITS */
        ITS(1),
        /** Send quickly over email */
        EMAIL(2),
        /** Cache and send via SPADE */
        SCP(3),
        /** Send whenever we get a chance */
        DEBUG(4);

        private int value;

        Priority(int value)
        {
            this.value = value;
        }

        public int value()
        {
            return value;
        }
    };

    /**
     * Close any open files/sockets.
     */
    void close();

    /**
     * Get the service name
     *
     * @return service name
     */
    String getService();

    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    boolean isActive();

    /**
     * Send an alert.
     *
     * @param priority priority level
     * @param condition I3Live condition
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    void sendAlert(Priority priority, String condition,
                   Map<String, Object> vars)
        throws AlertException;

    /**
     * Send an alert.
     *
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    void sendAlert(Priority priority, String condition, String notify,
                   Map<String, Object> vars)
        throws AlertException;

    /**
     * Send an alert.
     *
     * @param dateTime date and time for message
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    void sendAlert(Calendar dateTime, Priority priority, String condition,
                   String notify, Map<String, Object> vars)
        throws AlertException;

    /**
     * Send an alert.
     *
     * @param utcTime DAQ time
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    void sendAlert(IUTCTime utcTime, Priority priority, String condition,
                   String notify, Map<String, Object> vars)
        throws AlertException;

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param values map of names to values
     */
    void send(String varname, Priority priority, Map<String, Object> values)
        throws AlertException;

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param dateTime date and time for message
     * @param values map of names to values
     */
    void send(String varname, Priority priority, Calendar dateTime,
              Map<String, Object> values)
        throws AlertException;

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param utcTime DAQ time
     * @param values map of names to values
     */
    void send(String varname, Priority priority, IUTCTime utcTime,
              Map<String, Object> values)
        throws AlertException;

    /**
     * Send a Java object (as a JSON string) to a 0MQ server.
     *
     * @param obj object to send
     */
    void sendObject(Object obj)
        throws AlertException;

    /**
     * Set monitoring server host and port
     *
     * @param host - server host name
     * @param port - server port number
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    void setAddress(String host, int port)
        throws AlertException;
}
