package icecube.daq.juggler.alert;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Handle DAQ alerts
 */
public class UDPAlerter
    implements Alerter
{
    /** Logging object */
    private static final Log LOG = LogFactory.getLog(UDPAlerter.class);

    /** Service name */
    protected String service;

    /** I3Live host */
    private InetAddress liveAddr;
    /** I3Live port */
    private int livePort;
    /** I3Live socket connection */
    private DatagramSocket socket;

    /**
     * Create an alerter
     */
    public UDPAlerter()
    {
        this(DEFAULT_SERVICE);
    }

    /**
     * Create an alerter
     *
     * @param service service name
     */
    public UDPAlerter(String service)
    {
        this.service = service;
    }

    /**
     * Close any open files/sockets.
     */
    public void close()
    {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    /**
     * Get the service name
     *
     * @return service name
     */
    public String getService()
    {
        return service;
    }

    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    public boolean isActive()
    {
        return liveAddr != null;
    }

    /**
     * Send an alert.
     *
     * @param priority priority level
     * @param condition I3Live condition
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void send(Priority priority, String condition,
                     Map<String, Object> vars)
        throws AlertException
    {
        send(priority, condition, null, vars);
    }

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
    public void send(Priority priority, String condition, String notify,
                     Map<String, Object> vars)
        throws AlertException
    {
        send(Calendar.getInstance(), priority, condition, notify, vars);
    }

    /**
     * Send an alert to IceCube Live.
     *
     * @param date date and time for alert
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void send(Calendar date, Priority priority, String condition,
                     String notify, Map<String, Object> vars)
        throws AlertException
    {
        if (condition == null || condition.indexOf("\"") >= 0) {
            throw new AlertException("Bad alert condition \"" + condition +
                                     "\"");
        }

        String header =
            String.format("%s(alert:json) %d [%tF %tT.%tL000] \"\"\"\n",
                          service, priority.value(), date, date, date);
        StringBuilder buf = new StringBuilder(header);
        buf.append(" { \"condition\" : \"").append(condition).append("\"");
        if (notify != null && notify.length() > 0) {
            buf.append(", \"notify\" : \"").append(notify).append("\"");
        }

        if (vars != null && vars.size() > 0) {
            boolean declared = false;
            for (String key : vars.keySet()) {
                String front;
                if (!declared) {
                    front = ", \"vars\" : { \"";
                    declared = true;
                } else {
                    front = ", \"";
                }

                buf.append(front).append(key).append("\" : ");

                Object val = vars.get(key);
                if (val == null) {
                    throw new AlertException("Alert \"" + condition +
                                             "\" variable \"" + key +
                                             "\" has null value");
                } else if (val instanceof Number) {
                    buf.append(val);
                } else if (val instanceof String || val instanceof Character) {
                    buf.append("\"").append(val).append("\"");
                } else {
                    throw new AlertException("Alert \"" + condition +
                                             "\" variable \"" + key +
                                             "\" has unknown value type " +
                                             val.getClass().getName());
                }
            }

            if (declared) {
                buf.append(" }");
            }
        }

        buf.append(" }\n\"\"\"\n");

        for (int i = 0; i < 2; i++) {
            boolean retry = true;
            if (socket == null) {
                try {
                    socket = new DatagramSocket();
                } catch (SocketException se) {
                    LOG.error("Cannot create datagram socket", se);
                    break;
                }

                socket.connect(liveAddr, livePort);

                // don't bother retrying newly created sockets
                retry = false;
            }

            byte[] bytes = buf.toString().getBytes();
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            try {
                socket.send(packet);
                break;
            } catch (IOException ioe) {
                if (retry) {
                    // silently drop the log message and retry
                    socket = null;
                } else {
                    LOG.error("Cannot send \"" + condition + "\" alert", ioe);
                }
            }
        }
    }

    /**
     * Set IceCube Live host and port
     *
     * @param host - host name for IceCube Live server
     * @param port - port number for IceCube Live server
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void setAddress(String host, int port)
        throws AlertException
    {
        try {
            liveAddr = InetAddress.getByName(host);
        } catch (UnknownHostException uhe) {
            throw new AlertException("Cannot set I3Live host \"" + host + "\"",
                                     uhe);
        }

        livePort = port;
    }
}

