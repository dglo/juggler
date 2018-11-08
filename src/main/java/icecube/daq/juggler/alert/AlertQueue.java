package icecube.daq.juggler.alert;

import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.impl.UTCTime;

import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Thread which queues and sends alerts
 */
public class AlertQueue
    implements Runnable, IAlertQueue
{
    public static final String DEFAULT_NAME = "AlertQueue";

    private static final Logger LOG = Logger.getLogger(AlertQueue.class);

    private static final int MAX_QUEUE_SIZE = 1000000;

    private Alerter alerter;
    private String name;

    private Thread thread;
    private Deque queue = new ArrayDeque();

    private long numSent;
    private long numDropped;

    private int maxQueueSize = MAX_QUEUE_SIZE;
    private boolean queueFull;

    private volatile boolean idle = true;
    private volatile boolean stopping;
    private volatile boolean stopped = true;

    /**
     * Create an AlertQueue with a thread named <tt>"AlertQueue"</tt>
     *
     * @param alerter source of alerts
     */
    public AlertQueue(Alerter alerter)
    {
        this(DEFAULT_NAME, alerter);
    }

    /**
     * Create a named AlertQueue
     *
     * @param name thread name
     *
     * @param alerter source of alerts
     */
    public AlertQueue(String name, Alerter alerter)
    {
        this.name = name;
        this.alerter = alerter;
    }

    private Map<String, Object> buildMessage(String varname,
                                             Alerter.Priority priority,
                                             IUTCTime utc,
                                             Map<String, Object> values)
    {
        final String dateStr = utc.toDateString();

        String service;
        if (alerter != null) {
            service = alerter.getService();
        } else {
            service = Alerter.DEFAULT_SERVICE;
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("service", service);
        map.put("varname", varname);
        map.put("prio", priority.value());
        map.put("t", dateStr);
        if (values != null && values.size() > 0) {
            map.put("value", values);
        }
        return map;
    }

    /**
     * Get number of alerts dropped while the queue was full
     *
     * @return number of dropped alerts
     */
    public long getNumDropped()
    {
        return numDropped;
    }

    /**
     * Get number of alerts queued for delivery
     *
     * @return number of queued alerts
     */
    public int getNumQueued()
    {
        return queue.size();
    }

    /**
     * Get number of alerts successfully sent
     *
     * @return number of sent alerts
     */
    public long getNumSent()
    {
        return numSent;
    }

    /**
     * Is the thread idle?
     *
     * @return <tt>true</tt> if the thread is waiting for alerts
     */
    public boolean isIdle()
    {
        return idle;
    }

    /**
     * Is the thread stopped?
     *
     * @return <tt>true</tt> if the thread is not running
     */
    @Override
    public boolean isStopped()
    {
        return stopped;
    }

    /**
     * Add <tt>obj</tt> to the queue of alerts to be sent
     *
     * @param map alert to be sent
     *
     * @throws AlertException if there is a problem with the alerter or
     *                        the thread is stopped
     */
    @Override
    public void push(Map<String, Object> map)
        throws AlertException
    {
        if (alerter == null) {
            throw new AlertException("Alerter has not been set");
        } else if (stopping || stopped) {
            throw new AlertException("Alert queue " + name +
                                     " has not been started");
        }

        synchronized (queue) {
            if (!queueFull) {
                // if queue is "too large", stop adding stuff
                queueFull = queue.size() >= maxQueueSize;
                if (queueFull) {
                    LOG.error("Disabled alert queue " + name + " containing " +
                              queue.size() + " messages");
                }
            } else {
                // if queue has shrunk enough, resume adding stuff
                queueFull = queue.size() >= maxQueueSize / 2;
                if (!queueFull) {
                    LOG.error("Reenabled alert queue " + name +
                              " containing " + queue.size() +
                              " messages (dropped " + numDropped + ")");
                }
            }

            if (queueFull) {
                numDropped++;
            } else {
                queue.addLast(map);
                queue.notify();
            }
        }
    }

    /**
     * Add this alert to the queue of alerts to be sent
     *
     * @param varname alert name
     * @param prio alert priority
     * @param values alert values
     *
     * @throws AlertException if there is a problem with the alerter or
     *                        the thread is stopped
     */
    @Override
    public void push(String varname, Alerter.Priority prio,
                     Map<String, Object> values)
        throws AlertException
    {
        push(varname, prio, new UTCTime(), values);
    }

    /**
     * Add this alert to the queue of alerts to be sent
     *
     * @param varname alert name
     * @param prio alert priority
     * @param utcTime alert time
     * @param values alert values
     *
     * @throws AlertException if there is a problem with the alerter or
     *                        the thread is stopped
     */
    @Override
    public void push(String varname, Alerter.Priority prio, IUTCTime utcTime,
                     Map<String, Object> values)
        throws AlertException
    {
        if (utcTime == null) {
            utcTime = new UTCTime();
        }

        push(buildMessage(varname, prio, utcTime, values));
    }

    /**
     * Run the thread
     */
    @Override
    public void run()
    {
        stopped = false;
        try {
            runLoop();
        } finally {
            stopped = true;
        }
    }

    /**
     * Main thread loop
     */
    private void runLoop()
    {
        while (!stopping || queue.size() > 0) {
            Object obj;
            synchronized (queue) {
                if (queue.size() == 0 && !stopping) {
                    try {
                        idle = true;
                        queue.wait();
                    } catch (InterruptedException ie) {
                        LOG.error("Interrupt while waiting for alert queue " +
                                  name, ie);
                    }
                    idle = false;
                }

                if (queue.size() == 0) {
                    obj = null;
                } else {
                    obj = queue.removeFirst();
                }
            }

            if (obj == null) {
                // we woke up to an empty queue or were stopped when the
                // queue was empty; go to top of loop
                continue;
            }

            try {
                alerter.sendObject(obj);
                numSent++;
            } catch (AlertException ae) {
                LOG.error("Alert queue " + name + " cannot send " + obj, ae);
            }
        }

        if (alerter != null && alerter.isActive()) {
            alerter.close();
        }

        thread = null;
    }

    /**
     * Set the object responsible for sending alerts
     *
     * @param alerter alerter object
     *
     * @throws Error if alerter is null
     */
    public void setAlerter(Alerter alerter)
    {
        if (alerter == null) {
            throw new Error("Alerter cannot be null");
        }

        this.alerter = alerter;
    }

    /**
     * Set the maximum queue size.
     * NOTE: This is only used by unit tests
     *
     * @param size maximum queue size
     */
    public void setMaxQueueSize(int size)
    {
        maxQueueSize = size;
    }

    /**
     * Start the thread if it isn't already running
     */
    @Override
    public void start()
    {
        synchronized (queue) {
            if (!stopping && stopped) {
                stopping = false;
                stopped = false;

                thread = new Thread(this);
                thread.setName(name);
                thread.start();
            }
        }
    }

    /**
     * Stop the thread if it's running
     */
    @Override
    public void stop()
    {
        synchronized (queue) {
            if (!stopped) {
                stopping = true;
                queue.notify();
            }
        }
    }

    /**
     * Tell the thread to stop and wait until it is fully stopped.
     * NOTE: This is currently only used by unit tests.
     *
     * @throws Error if the thread was not stopped
     */
    public void stopAndWait()
    {
        synchronized (queue) {
            if (!stopped) {
                stopping = true;
                queue.notify();

                for (int i = 0; i < 1000; i++) {
                    if (stopped) {
                        break;
                    }

                    try {
                        queue.wait(100);
                    } catch (InterruptedException ie) {
                        continue;
                    }
                }
            }
        }

        if (!stopped) {
            throw new Error("Cannot stop " + name + "; queue has " +
                            queue.size() + " alerts, stopping is " +
                            stopping);
        }
    }

    /**
     * Thread name
     */
    @Override
    public String toString()
    {
        return name;
    }
}
