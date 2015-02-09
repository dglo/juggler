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
