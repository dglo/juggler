package icecube.daq.juggler.alert;

import icecube.daq.juggler.alert.Alerter.Priority;
import icecube.daq.juggler.test.MockAlerter;
import icecube.daq.juggler.test.MockAppender;
import icecube.daq.juggler.test.MockAlerter;
import icecube.daq.juggler.test.MockUTCTime;

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
        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        alerter = new MockAlerter();
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
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

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
                aq.push("Alert#" + i + "/" + j);
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

        aq.setMaxQueueSize(1);
        alerter.setSendDelay(500);

        startQueue(aq);

        aq.push("ABC");
        aq.push("DEF");

        flushQueue(aq);
        assertEquals("Bad number of log messages",
                     1, appender.getNumberOfMessages());
        assertEquals("Bad log message",
                     "Disabled alert queue containing 1 messages",
                     appender.getMessage(0));
        appender.clear();

        aq.push("GHI");

        flushQueue(aq);
        assertEquals("Bad number of log messages",
                     1, appender.getNumberOfMessages());
        assertEquals("Bad log message",
                     "Reenabled alert queue containing 0 messages (dropped 1)",
                     appender.getMessage(0));
        appender.clear();

        aq.stopAndWait();
        assertEquals("Bad number of alerts sent", 2, aq.getNumSent());
    }
}
