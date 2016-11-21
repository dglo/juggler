package icecube.daq.juggler.alert;

import icecube.daq.payload.IUTCTime;

/*
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
*/
import java.util.Map;

/**
 * Thread which queues and sends alerts
 */
public interface IAlertQueue
{
    /**
     * Add <tt>obj</tt> to the queue of alerts to be sent
     *
     * @param obj alert to be sent
     *
     * @throws AlertException if there is a problem with the alerter or
     *                        the thread is stopped
     */
    void push(Map<String, Object> obj)
        throws AlertException;

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
    void push(String varname, Alerter.Priority prio,
              Map<String, Object> values)
        throws AlertException;

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
    void push(String varname, Alerter.Priority prio, IUTCTime utcTime,
              Map<String, Object> values)
        throws AlertException;
}
