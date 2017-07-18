package icecube.daq.juggler.alert;

import icecube.daq.common.MockAppender;
import icecube.daq.juggler.alert.Alerter.Priority;
import icecube.daq.juggler.test.MockAlerter;
import icecube.daq.juggler.test.MockUTCTime;
import icecube.daq.util.LocatePDAQ;

import java.io.File;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;

import org.junit.*;
import static org.junit.Assert.*;

public class AlertQueueTest
{
    private static final MockAppender appender =
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        new MockAppender(org.apache.log4j.Level.WARN).setVerbose(false);

    private MockAlerter alerter = new MockAlerter();

    private void flushQueue(AlertQueue aq)
    {
        if (!aq.isStopped()) {
            for (int i = 0; i < 1000; i++) {
                if (aq.isIdle() && aq.getNumQueued() == 0) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    break;
                }
            }

            if (aq.getNumQueued() > 0) {
                throw new Error("Cannot flush " + aq + "; " +
                                aq.getNumQueued() + " alerts queued");
            }
        }
    }

    @Before
    public void setUp()
        throws Exception
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        alerter = new MockAlerter();

        // ensure LocatePDAQ uses the test version of the config directory
        File configDir =
            new File(getClass().getResource("/config").getPath());
        if (!configDir.exists()) {
            throw new IllegalArgumentException("Cannot find config" +
                                               " directory under " +
                                               getClass().getResource("/"));
        }

        System.setProperty(LocatePDAQ.CONFIG_DIR_PROPERTY,
                           configDir.getAbsolutePath());
    }

    private void startQueue(AlertQueue aq)
    {
        if (aq.isStopped()) {
            aq.start();
            for (int i = 0; i < 1000; i++) {
                if (aq.isIdle()) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    break;
                }
            }

            if (!aq.isIdle()) {
                throw new Error("Cannot start " + aq);
            }
        }
    }

    @After
    public void tearDown()
        throws Exception
    {
        System.clearProperty(LocatePDAQ.CONFIG_DIR_PROPERTY);

        appender.assertNoLogMessages();

        //assertEquals("Bad number of alert messages",
        //             0, alerter.countAllAlerts());
    }

    @Test
    public void testBasic()
        throws AlertException
    {
        AlertQueue aq = new AlertQueue(alerter);
        assertTrue("AlertQueue should be stopped", aq.isStopped());
        assertTrue("AlertQueue should not be idle", aq.isIdle());

        // should be safe to stop a stopped queue
        aq.stopAndWait();

        startQueue(aq);

        // should be safe to start an already running queue
        startQueue(aq);

        // queue up some alerts
        int numExpected = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 30; j++) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("alert", String.format("%d/%d", i, j));
                aq.push(map);
                numExpected++;
            }

            for (int w = 0; w < 20; w++) {
                if (aq.isIdle()) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        aq.stopAndWait();

        assertEquals("Bad number of alerts queued", 0, aq.getNumQueued());
        assertEquals("Bad number of alerts dropped", 0, aq.getNumDropped());
        assertEquals("Bad number of alerts sent", numExpected,
                     aq.getNumSent());
        assertEquals("Bad number of alerts sent", numExpected,
                     alerter.getNumSent());
    }

    @Test
    public void testBasicPushVariants()
        throws AlertException
    {
        AlertQueue aq = new AlertQueue(alerter);
        assertTrue("AlertQueue should be stopped", aq.isStopped());
        assertTrue("AlertQueue should not be idle", aq.isIdle());

        startQueue(aq);

        aq.push("abc", Priority.ITS, null);
        aq.push("cde", Priority.ITS, new MockUTCTime(12345L), null);

        aq.stopAndWait();
        assertEquals("Bad number of alerts sent", 2, aq.getNumSent());
    }

    @Test
    public void testMaxQueue()
        throws AlertException
    {
        AlertQueue aq = new AlertQueue(alerter);
        assertTrue("AlertQueue should be stopped", aq.isStopped());
        assertTrue("AlertQueue should not be idle", aq.isIdle());

        final int maxSize = 4;

        aq.setMaxQueueSize(maxSize);
        alerter.setSendDelay(500);

        startQueue(aq);

        // track number of alerts sent
        int sent = 0;

        // queue maximum number of alerts
        for (int i = 0; i < maxSize; i++) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("alert", i);
            aq.push(map);
            sent++;
        }

        // keep pushing alerts until we see the overflow error
        for (int i = maxSize; i < maxSize * 2; i++) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("alert", i);
            aq.push(map);
            sent++;
            if (appender.getNumberOfMessages() > 0) {
                break;
            }
        }

        // should only receive one error
        final String front = "Disabled alert queue " +
            AlertQueue.DEFAULT_NAME + " containing ";
        appender.assertLogMessage(front);
        appender.assertNoLogMessages();

        // wait for queue to be emptied
        flushQueue(aq);

        // push into empty queue
        HashMap<String, Object> finalMap = new HashMap<String, Object>();
        finalMap.put("Valid", "yes");
        aq.push(finalMap);
        sent++;

        // wait for the log message
        for (int i = 0; i < 100 && appender.getNumberOfMessages() == 0; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        // the previous push should cause a 'reenabled' log message
        final String reMsg = "Reenabled alert queue " +
            AlertQueue.DEFAULT_NAME + " containing 0 messages (dropped " +
            aq.getNumDropped() + ")";
        appender.assertLogMessage(reMsg);
        appender.assertNoLogMessages();

        // empty the queue again and stop
        flushQueue(aq);
        aq.stopAndWait();

        // make sure we got the expected number of alerts
        assertEquals("Bad number of alerts sent",
                     sent - aq.getNumDropped(), aq.getNumSent());
    }
}
