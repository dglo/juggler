package icecube.daq.juggler.alert;

import icecube.daq.juggler.test.LogReader;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class DAQAlerterTest
    extends TestCase
{
    private LogReader logRdr;

    public DAQAlerterTest(String name)
    {
        super(name);
    }

    private static void addExpectedAlert(LogReader logRdr, int prio,
                                         Calendar date, String condition,
                                         String desc, Map<String, Object> vars)
    {
        final String startStr =
            String.format("pdaq(alert:json) %d [%tF %tT.%tL000] \"\"\"\n" +
                          " { \"condition\" : \"%s\", \"desc\" : \"%s\"," +
                          " \"notify\" : \"\"", prio, date, date, date,
                          condition, desc);

        StringBuilder buf = new StringBuilder(startStr);

        if (vars != null && vars.size() > 0) {
            boolean first = true;
            for (String key : vars.keySet()) {
                if (first) {
                    first = false;
                    buf.append(", \"vars\" : { \"");
                } else {
                    buf.append(", \"");
                }
                buf.append(key).append("\" : ");

                Object val = vars.get(key);
                if (val instanceof String) {
                    buf.append('"').append(val).append('"');
                } else {
                    buf.append(val);
                }
            }
            if (!first) {
                buf.append(" }");
            }
        }

        buf.append(" }\n\"\"\"");

        logRdr.addExpected(buf.toString());
    }

    public static Test suite()
    {
        return new TestSuite(DAQAlerterTest.class);
    }

    protected void tearDown()
    {
        if (logRdr != null) {
            logRdr.close();

            if (logRdr.hasError()) fail(logRdr.getNextError());
            assertEquals("Not all log messages were received",
                         0, logRdr.getNumberOfExpectedMessages());
        }
    }

    public void testSimple()
    {
        DAQAlerter alerter = new DAQAlerter();
        assertFalse("New Alerter should not be active", alerter.isActive());
        alerter.close();
    }

    public void testBadLiveHost()
    {
        DAQAlerter alerter = new DAQAlerter();

        try {
            alerter.setLive("impossible host name", 9999);
            fail("bad host name; should not succeed");
        } catch (AlertException ae) {
            // ignore exception
        }
    }

    public void testSendBadCondition()
        throws AlertException
    {
        try {
            logRdr = new LogReader("Alert");
        } catch (IOException ioe) {
            fail("Couldn't create log reader: " + ioe.getMessage());
        }

        final int prio = DAQAlerter.PRIO_ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = "Bad \" Condition";
        final String desc = "";

        final Map<String, Object> vars = null;

        DAQAlerter alerter = new DAQAlerter();
        alerter.setLive("localhost", logRdr.getPort());

        try {
            alerter.send(prio, date, condition, desc, vars);
            fail("Bad condition should not succeed");
        } catch (AlertException ae) {
            assertTrue("Unexpected exception: " + ae,
                       ae.getMessage().startsWith("Bad alert condition "));
        }
    }

    public void testSendBadValue()
        throws AlertException
    {
        try {
            logRdr = new LogReader("Alert");
        } catch (IOException ioe) {
            fail("Couldn't create log reader: " + ioe.getMessage());
        }

        final int prio = DAQAlerter.PRIO_ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = "Bad Value";
        final String desc = "foo";

        final String varName = "bad";

        final Map<String, Object> vars = new HashMap<String, Object>();
        vars.put(varName, new HashMap());

        DAQAlerter alerter = new DAQAlerter();
        alerter.setLive("localhost", logRdr.getPort());

        try {
            alerter.send(prio, date, condition, desc, vars);
            fail("Unknown value should not succeed");
        } catch (AlertException ae) {
            assertTrue("Unexpected exception: " + ae,
                       ae.getMessage().startsWith("Alert \"" + condition +
                                                  "\" variable \"" + varName +
                                                  "\" has unknown value"));
        }
    }

    public void testSendNullValue()
        throws AlertException
    {
        try {
            logRdr = new LogReader("Alert");
        } catch (IOException ioe) {
            fail("Couldn't create log reader: " + ioe.getMessage());
        }

        final int prio = DAQAlerter.PRIO_ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = "Bad Value";
        final String desc = "";

        final String varName = "nullVar";

        final Map<String, Object> vars = new HashMap<String, Object>();
        vars.put(varName, null);

        DAQAlerter alerter = new DAQAlerter();
        alerter.setLive("localhost", logRdr.getPort());

        try {
            alerter.send(prio, date, condition, desc, vars);
            fail("Unknown value should not succeed");
        } catch (AlertException ae) {
            assertTrue("Unexpected exception: " + ae,
                       ae.getMessage().startsWith("Alert \"" + condition +
                                                  "\" variable \"" + varName +
                                                  "\" has null value"));
        }
    }

    public void testSendMinimal()
        throws AlertException
    {
        try {
            logRdr = new LogReader("Alert");
        } catch (IOException ioe) {
            fail("Couldn't create log reader: " + ioe.getMessage());
        }

        final int prio = DAQAlerter.PRIO_ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = "Test Alert";
        final String desc = "Testing DAQ alerts";

        final Map<String, Object> vars = null;

        addExpectedAlert(logRdr, prio, date, condition, desc, vars);

        DAQAlerter alerter = new DAQAlerter();
        alerter.setLive("localhost", logRdr.getPort());

        alerter.send(prio, date, condition, desc, vars);

        logRdr.waitForMessages();

        alerter.close();
    }

    public void testSendVars()
        throws AlertException
    {
        try {
            logRdr = new LogReader("Alert");
        } catch (IOException ioe) {
            fail("Couldn't create log reader: " + ioe.getMessage());
        }

        final int prio = DAQAlerter.PRIO_ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = "Test Alert";
        final String desc = "Test Alert";

        final Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("int", 123);
        vars.put("real", 123.456);
        vars.put("str", "foo");

        addExpectedAlert(logRdr, prio, date, condition, desc, vars);

        DAQAlerter alerter = new DAQAlerter();
        alerter.setLive("localhost", logRdr.getPort());

        alerter.send(prio, date, condition, desc, vars);

        logRdr.waitForMessages();

        alerter.close();
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
