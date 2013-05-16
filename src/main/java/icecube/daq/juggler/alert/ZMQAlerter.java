package icecube.daq.juggler.alert;

import com.google.gson.Gson;

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
    private static final Log LOG = LogFactory.getLog(ZMQAlerter.class);
    /** Number of ZeroMQ I/O threads */
    private static final int NUMBER_OF_THREADS = 70;

    /** Service name */
    protected String service;

    /** I3Live host */
    private InetAddress liveAddr;
    /** I3Live port */
    private int livePort;

    // string zmq live address representation
    private String fLiveAddr;

    /** Google JSON conversion object */
    private Gson gson = new Gson();
    /** ZeroMQ context */
    private Context context;

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
        // multiple threads can call alert
        // and I see no guarentee that alert will not
        // be called after close, so make
        // that operation thread safe
        synchronized(this) {
            if (context != null) {
                context.term();
                context = null;
            }
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
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param values map of variable names to values
     */
    public void send(String varname, Priority priority,
                     Map<String, Object> values)
        throws AlertException
    {
        send(varname, priority, Calendar.getInstance(), values);
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param date date and time for message
     * @param values map of variable names to values
     */
    public void send(String varname, Priority priority, Calendar date,
                     Map<String, Object> values)
        throws AlertException
    {
        if (fLiveAddr == null) {
            throw new AlertException("Address has not been set");
        }

        HashMap map = new HashMap();
        map.put("service", service);
        map.put("varname", varname);
        map.put("prio", priority.value());
        map.put("t", String.format("%tF %tT.%tL000", date, date, date));
        if (values.size() > 0) {
            map.put("value", values);
        }

        String json = gson.toJson(map);
        byte[] bytes = json.toString().getBytes();

        Socket socket;
        String addr;

        /*
         * ZeroMQ sockets are not thread safe so create/connect/send/close on
         * each alert.
         * These should be fairly rare, so performance isn't a huge issue.
         */

        try {
            synchronized(this) {
                if (context == null) {
                    final String msg = "sendLive called with a null context";
                    throw new AlertException(msg);
                }

                socket = context.socket(ZMQ.PUSH);

                addr = fLiveAddr;
            }
        } catch (ZMQException ze) {
            throw new AlertException("Cannot create ZeroMQ socket", ze);
        }

        if (addr == null) {
            throw new AlertException("sendLive called before setAddr!");
        }

        try {
            socket.connect(addr);

            // sockets time out after .1 second
            socket.setLinger(100);

            socket.send(bytes, 0);
        } catch (ZMQException ze) {
            final String msg = String.format("Cannot send \"%s\" to I3Live" +
                                             " host \"%s\"", varname, addr);
            throw new AlertException(msg, ze);
        } finally {
            socket.close();
        }
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
    public void sendAlert(Priority priority, String condition,
                          Map<String, Object> vars)
        throws AlertException
    {
        sendAlert(priority, condition, null, vars);
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
    public void sendAlert(Priority priority, String condition, String notify,
                          Map<String, Object> vars)
        throws AlertException
    {
        sendAlert(Calendar.getInstance(), priority, condition, notify, vars);
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
    public void sendAlert(Calendar date, Priority priority, String condition,
                          String notify, Map<String, Object> vars)
        throws AlertException
    {
        HashMap values = new HashMap();
        if (condition != null && condition.length() > 0) {
            values.put("condition", condition);
        }
        if (notify != null && notify.length() > 0) {
            values.put("notify", notify);
        }
        if (vars != null && vars.size() > 0) {
            values.put("vars", vars);
        }

        send("alert", priority, date, values);
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

        synchronized(this) {
            fLiveAddr = "tcp://" + liveAddr.getHostAddress() + ":" +
                livePort;
        }
    }

    /**
     * Return debugging string
     *
     * @return debugging string
     */
    public String toString()
    {
        return String.format("ZMQAlerter[%s]",
                             fLiveAddr == null ? "" : fLiveAddr);
    }
}
