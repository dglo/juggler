package icecube.daq.juggler.alert;

import com.google.gson.Gson;

import icecube.daq.payload.IUTCTime;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

/**
 * Handle alerts
 */
public class ZMQThreadedAlerter
    extends Thread
    implements Alerter
{
    /** Number of ZeroMQ I/O threads */
    private static final int NUMBER_OF_THREADS = 5;

    /** Service name */
    protected String service;

    /** Google JSON conversion object */
    private Gson gson = new Gson();

    private BlockingQueue fMsgQueue;

    private volatile String fLiveAddr;
    private volatile boolean running;

    /**
     * Create an alerter
     */
    public ZMQThreadedAlerter()
    {
        this(DEFAULT_SERVICE);
    }

    /**
     * Create an alerter
     *
     * @param service service name
     */
    public ZMQThreadedAlerter(String service)
    {
        this.service = service;
	this.fMsgQueue = new LinkedBlockingQueue();
	this.fLiveAddr = null;
	this.running=false;
    }


    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    @Override
    public boolean isActive()
    {
        return running;
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
        throw new Error("Unimplemented");
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
        throw new Error("Unimplemented");
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

	HashMap map = new HashMap();
	map.put("service", service);
	map.put("varname", "alert");
	map.put("prio", priority.value());
	map.put("t", String.format("%tF %tT.%tL000", date, date, date));
	if (values.size() > 0) {
	    map.put("value", values);
	}

	sendObject(map);

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
	if (fLiveAddr==null) {
	    throw new AlertException("sendLive called before setAddr!");
	}

	if (!running) {
	    throw new AlertException("alerter already closed");
	}

	String json = gson.toJson(obj);

	synchronized(this) {
	    try {
		fMsgQueue.put(json);
	    } catch (InterruptedException e) {
		// the message queue size is
		// MAX_INT
	    }
	    this.notify();
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
     * Close any open files/sockets.
     */
    @Override
    public void close()
    {
	// multiple threads can call alert
	// and I see no guarentee that alert will not
	// be called after close, so make
	// that operation thread safe
	synchronized(this) {
	    this.running=false;
	    this.notify();
	}

	try {
	    this.join();
	} catch (InterruptedException e) {
	    // pass
	}
    }


    /**
     * Set IceCube Live host and port
     *
     * @param host - host name for IceCube Live server
     * @param port - port number for IceCube Live server
     * @throws AlertException if there is a problem with one of the parameters
     */
    @Override
    public void setAddress(String host, int port)
        throws AlertException
    {
	/** I3Live host */
	InetAddress liveAddr;
	/** I3Live port */
	int livePort;

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


    @Override
    public void run() {
	boolean update_socket=false;

	running = true;

	Context context = ZMQ.context(NUMBER_OF_THREADS);
	Socket socket = null;
	String myAddr = null;

	try {
	    while(true) {

		synchronized(this) {
		    try {
			this.wait();
		    } catch (InterruptedException e) {
			// ignore in this case
		    }

		    if (!this.running) {
			return;
		    }

		    if (fLiveAddr==null) {
			// loop back and wait again
			continue;
		    }

		    if (myAddr == null || !myAddr.equals(fLiveAddr)) {
			myAddr=fLiveAddr;
			update_socket=true;
		    }
		}

		if(update_socket) {
		    if (socket!=null) {
			socket.close();
		    }

		    socket = context.socket(ZMQ.PUSH);
		    socket.connect(myAddr);
		    socket.setLinger(100);
		    update_socket=false;
		}

		String msg = (String)fMsgQueue.poll();
		while(msg!=null) {
		    // got an item from the queue
		    byte[] bytes = msg.toString().getBytes();
		    socket.send(bytes,0);

		    msg = (String)fMsgQueue.poll();
		}
	    }
	} finally {
	    if (socket!=null) {
		socket.close();
		socket=null;
	    }
	    if(context!=null) {
		context.term();
		context = null;
	    }
	}
    }

}
