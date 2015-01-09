package icecube.daq.juggler.alert;

import icecube.daq.payload.IUTCTime;

import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Thread which queues and sends alerts
 */
public class AlertQueue
    implements Runnable
{
    private static final Log LOG = LogFactory.getLog(AlertQueue.class);

    private static final int MAX_QUEUE_SIZE = 1000000;

    private Alerter alerter;
    private String name;

    private Thread thread;
    private Deque queue = new ArrayDeque();

    private long numSent;
    private long numDropped;

    private int maxQueueSize = MAX_QUEUE_SIZE;
    private boolean queueFull;

    private boolean idle = true;
    private boolean stopping;
    private boolean stopped = true;

    public AlertQueue(Alerter alerter)
    {
        this("AlertQueue", alerter);
    }

    public AlertQueue(String name, Alerter alerter)
    {
        this.name = name;
        this.alerter = alerter;
    }

    private Map<String, Object> buildMessage(String varname,
                                             Alerter.Priority priority,
                                             Map<String, Object> values)
    {
        return buildMessage(varname, priority, Calendar.getInstance(), values);
    }

    private Map<String, Object> buildMessage(String varname,
                                             Alerter.Priority priority,
                                             IUTCTime utc,
                                             Map<String, Object> values)
    {
        return buildMessage(varname, priority, utc.toDateString(), values);
    }

    private Map<String, Object> buildMessage(String varname,
                                             Alerter.Priority priority,
                                             Calendar date,
                                             Map<String, Object> values)
    {
        final String dateStr =
            String.format("%tF %tT.%tL000", date, date, date);
        return buildMessage(varname, priority, dateStr, values);
    }

    private Map<String, Object> buildMessage(String varname,
                                             Alerter.Priority priority,
                                             String dateStr,
                                             Map<String, Object> values)
    {
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

    public long getNumDropped()
    {
        return numDropped;
    }

    public int getNumQueued()
    {
        return queue.size();
    }

    public long getNumSent()
    {
        return numSent;
    }

    public boolean isIdle()
    {
        return idle;
    }

    public boolean isStopped()
    {
        return stopped;
    }

    public void push(Object obj)
        throws AlertException
    {
        if (alerter == null) {
            throw new AlertException("Alerter has not been set");
        } else if (stopping || stopped) {
            throw new AlertException("AlertQueue has not been started");
        }

        synchronized (queue) {
            if (!queueFull) {
                // if queue is "too large", stop adding stuff
                queueFull = queue.size() >= maxQueueSize;
                if (queueFull) {
                    LOG.error("Disabled alert queue containing " +
                              queue.size() + " messages");
                }
            } else {
                // if queue has shrunk enough, resume adding stuff
                queueFull = queue.size() < maxQueueSize / 2;
                if (!queueFull) {
                    LOG.error("Reenabled alert queue containing " +
                              queue.size() + " messages (dropped " +
                              numDropped + ")");
                }
            }

            if (queueFull) {
                numDropped++;
            } else {
                queue.addLast(obj);
                queue.notify();
            }
        }
    }

    public void push(String varname, Alerter.Priority prio,
                     Map<String, Object> values)
        throws AlertException
    {
        push(buildMessage(varname, prio, values));
    }

    public void push(String varname, Alerter.Priority prio, IUTCTime utcTime,
                     Map<String, Object> values)
        throws AlertException
    {
        push(buildMessage(varname, prio, utcTime, values));
    }

    public void run()
    {
        stopped = false;
        try {
            runLoop();
        } finally {
            stopped = true;
        }
    }

    private void runLoop()
    {
        while (!stopping || queue.size() > 0) {
            Object obj;
            synchronized (queue) {
                if (queue.size() == 0) {
                    try {
                        idle = true;
                        queue.wait();
                    } catch (InterruptedException ie) {
                        LOG.error("Interrupt while waiting for alert queue",
                                  ie);
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
                // we woke up to an empty queue; go to top of loop
                continue;
            }

            try {
                alerter.sendObject(obj);
                numSent++;
            } catch (AlertException ae) {
                LOG.error("Cannot send " + obj, ae);
            }
        }

        if (alerter != null && alerter.isActive()) {
            alerter.close();
        }

        thread = null;
    }

    public void setAlerter(Alerter alerter)
    {
        if (alerter == null) {
            throw new Error("Alerter cannot be null");
        }

        this.alerter = alerter;
    }

    public void setMaxQueueSize(int size)
    {
        maxQueueSize = size;
    }

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

    public void stop()
    {
        synchronized (queue) {
            if (!stopped) {
                stopping = true;
                queue.notify();
            }
        }
    }

    public void stopAndWait()
    {
        if (!stopped) {
            stop();
            for (int i = 0; i < 1000; i++) {
                if (stopped) {
                    break;
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    break;
                }
            }

            if (!stopped) {
                throw new Error("Cannot stop " + name);
            }
        }
    }

    public String toString()
    {
        return name;
    }
}
