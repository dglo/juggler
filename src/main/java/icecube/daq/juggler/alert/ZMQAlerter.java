package icecube.daq.juggler.alert;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

/**
 * Handle DAQ alerts
 */
public class ZMQAlerter
    implements Alerter
{
    /** Logging object */
    private static final Log LOG = LogFactory.getLog(Alerter.class);
    /** Number of ZeroMQ I/O threads */
    private static final int NUMBER_OF_THREADS = 70;

    /** Service name */
    protected String service;

    /** I3Live host */
    private InetAddress liveAddr;
    /** I3Live port */
    private int livePort;
    /** Google JSON conversion object */
    private Gson gson = new Gson();
    /** ZeroMQ context */
    private Context context;
    /** ZeroMQ socket */
    private Socket socket;

    /**
     * Create an alerter
     */
    public ZMQAlerter()
    {
        this(DEFAULT_SERVICE);
    }

    /**
     * Create an alerter
     *
     * @param service service name
     */
    public ZMQAlerter(String service)
    {
        this.service = service;

        context = ZMQ.context(NUMBER_OF_THREADS);
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

        if (context != null) {
            context.term();
            context = null;
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
        return liveAddr != null && socket != null;
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
     * Send an alert.
     *
     * @param dateTime date and time for alert
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void send(Calendar dateTime, Priority priority, String condition,
                     String notify, Map<String, Object> vars)
        throws AlertException
    {
        if (liveAddr != null) {
            sendLive(dateTime, priority, condition, notify, vars);
        }
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
    private void sendLive(Calendar date, Priority priority, String condition,
                          String notify, Map<String, Object> vars)
        throws AlertException
    {
        HashMap payload = new HashMap();
        if (condition != null && condition.length() > 0) {
            payload.put("condition", condition);
        }
        if (notify != null && notify.length() > 0) {
            payload.put("notify", notify);
        }
        if (vars != null && vars.size() > 0) {
            payload.put("vars", vars);
        }

        HashMap map = new HashMap();
        map.put("service", service);
        map.put("varname", "alert");
        map.put("prio", priority.value());
        map.put("t", String.format("%tF %tT.%tL000", date, date, date));
        if (payload.size() > 0) {
            map.put("payload", payload);
        }

        String json = gson.toJson(map);
        byte[] bytes = json.toString().getBytes();

        socket.send(bytes, 0);
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

        if (liveAddr == null) {
            throw new AlertException("I3Live host \"" + host +
                                     "\" returned null address");
        }

        livePort = port;

        final String addr = "tcp://" + liveAddr.getHostAddress() + ":" +
            livePort;

        try {
            socket = context.socket(ZMQ.PUSH);
            socket.connect(addr);
        } catch (ZMQException ze) {
            throw new AlertException("Cannot set I3Live host \"" + host + "\"",
                                     ze);
        }

        // sockets time out after .1 second
        socket.setLinger(100);
    }
}

