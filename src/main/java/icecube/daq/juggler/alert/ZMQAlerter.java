package icecube.daq.juggler.alert;

import com.google.gson.Gson;

import icecube.daq.payload.IUTCTime;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

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
    private static final Logger LOG = Logger.getLogger(ZMQAlerter.class);
    /** Number of 0MQ I/O threads */
    private static final int NUMBER_OF_THREADS = 5;

    /** Service name */
    protected String service;

    /** 0MQ host */
    private InetAddress zmqHost;
    /** 0MQ port */
    private int zmqPort;
    /** Cached 0MQ URL */
    private String zmqURL;

    /** Google JSON conversion object */
    private Gson gson = new Gson();
    /** 0MQ context */
    private Context context;
    /** 0MQ socket */
    private Socket socket;
    /** Have we logged an error after the socket was closed? */
    private boolean socketWarned;

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
    @Override
    public void close()
    {
        synchronized (this) {
            if (socket != null) {
                socket.close();
                socket = null;
                socketWarned = false;
            }

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
    @Override
    public String getService()
    {
        return service;
    }

    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    @Override
    public boolean isActive()
    {
        return zmqHost != null && context != null;
    }

    private HashMap<String, Object> makeAlertValues(String condition,
                                                    String notify,
                                                    Map<String, Object> vars)
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
        return values;
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
        final String dateStr =
            String.format("%tF %tT.%tL000", date, date, date);
        send(varname, priority, dateStr, values);
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param utcTime DAQ time
     * @param values map of variable names to values
     */
    public void send(String varname, Priority priority, IUTCTime utcTime,
                     Map<String, Object> values)
        throws AlertException
    {
        send(varname, priority, utcTime.toDateString(), values);
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param date date and time for message
     * @param values map of variable names to values
     */
    private void send(String varname, Priority priority, String dateStr,
                      Map<String, Object> values)
        throws AlertException
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("service", service);
        map.put("varname", varname);
        map.put("prio", priority.value());
        map.put("t", dateStr);
        if (values.size() > 0) {
            map.put("value", values);
        }

        sendObject(map);
    }

    /**
     * Send a Java object (as a JSON string) to a 0MQ server.
     *
     * @param obj object to send
     */
    @Override
    public void sendObject(Object obj)
        throws AlertException
    {
        String json;
        synchronized (gson) {
            json = gson.toJson(obj);
        }

        byte[] bytes = json.toString().getBytes();

        synchronized (this) {
            if (socket == null) {
                if (!socketWarned) {
                    LOG.error("Cannot send alert; socket has been closed");
                    socketWarned = true;
                }
            } else {
                try {
                    socket.send(bytes, 0);
                } catch (ZMQException ze) {
                    final String msg =
                        String.format("Cannot send \"%s\" to 0MQ host \"%s\"",
                                      obj, zmqURL);
                    throw new AlertException(msg, ze);
                }
            }
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
        send("alert", priority, date, makeAlertValues(condition, notify,
                                                      vars));
    }

    /**
     * Send an alert to IceCube Live.
     *
     * @param utcTime DAQ time
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(IUTCTime utcTime, Priority priority,
                          String condition, String notify,
                          Map<String, Object> vars)
        throws AlertException
    {
        send("alert", priority, utcTime, makeAlertValues(condition, notify,
                                                         vars));
    }

    /**
     * Set 0MQ host and port
     *
     * @param host - host name for 0MQ server
     * @param port - port number for 0MQ server
     * @throws AlertException if there is a problem with one of the parameters
     */
    @Override
    public void setAddress(String host, int port)
        throws AlertException
    {
        try {
            zmqHost = InetAddress.getByName(host);
        } catch (UnknownHostException uhe) {
            throw new AlertException("Cannot set 0MQ host \"" + host + "\"",
                                     uhe);
        }

        if (zmqHost == null) {
            throw new AlertException("0MQ host \"" + host +
                                     "\" returned null address");
        }

        synchronized (this) {
            zmqPort = port;

            zmqURL = "tcp://" + zmqHost.getHostAddress() + ":" +
                zmqPort;

            if (context == null) {
                throw new AlertException("Context is null" +
                                         " (alerter was closed?)");
            }

            try {
                socket = context.socket(ZMQ.PUSH);
            } catch (ZMQException ze) {
                throw new AlertException("Cannot create 0MQ socket", ze);
            }

            socket.connect(zmqURL);

            // sockets time out after .1 second
            socket.setLinger(100);

            // haven't yet whined about socket being closed
            socketWarned = false;
        }
    }

    /**
     * Return debugging string
     *
     * @return debugging string
     */
    @Override
    public String toString()
    {
        return String.format("ZMQAlerter[%s]",
                             zmqURL == null ? "" : zmqURL);
    }
}
