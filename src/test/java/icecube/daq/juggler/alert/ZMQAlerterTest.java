package icecube.daq.juggler.alert;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

class LiveRequest
{
    private static final boolean DEBUG = true;

    private String service;
    private String varname;
    private int priority;
    private Calendar date;
    private String condition;
    private String notify;
    private Map vars;

    LiveRequest(String service, String varname, ZMQAlerter.Priority priority,
                Calendar date, String condition, String notify, Map vars)
    {
        this.service = service;
        this.varname = varname;
        this.priority = priority.value();
        this.date = date;
        this.condition = condition;
        this.notify = notify;
        this.vars = vars;
    }

    private boolean compareValues(Object goodVal, Object val)
    {
        if (goodVal == null) {
            return val == null;
        }

        Class goodClass = goodVal.getClass();
        if (goodClass.equals(val.getClass())) {
            return goodVal.equals(val);
        }

        if (goodVal instanceof Number && val instanceof Number) {
            return ((Number) goodVal).doubleValue() ==
                ((Number) val).doubleValue();
        }

        throw new Error("Cannot compare " + goodVal + "<" +
                        goodVal.getClass().getName() + "> and " + val +
                        "<" + val.getClass().getName() + ">");
    }

    public boolean equals(HashMap map)
    {
        if (!map.containsKey("service")) {
            if (DEBUG) System.err.println("--- Missing service");
            return false;
        } else if (!((String) map.get("service")).equals(service)) {
            if (DEBUG)
                System.err.println("--- Service \"" + service + "\" != \"" +
                                   map.get("service") + "\"");
            return false;
        }

        if (!map.containsKey("varname")) {
            if (DEBUG) System.err.println("--- Missing varname");
            return false;
        } else if (!((String) map.get("varname")).equals(varname)) {
            if (DEBUG)
                System.err.println("--- VarName \"" + varname + "\" != \"" +
                                   map.get("varname") + "\"");
            return false;
        }

        if (!map.containsKey("prio")) {
            if (DEBUG) System.err.println("--- Missing priority");
            return false;
        } else if (((Number) map.get("prio")).intValue() != priority) {
            if (DEBUG)
                System.err.println("--- Priority \"" + priority + "\" != \"" +
                                   map.get("priority") + "\"");
            return false;
        }

        if (!map.containsKey("t")) {
            if (DEBUG) System.err.println("--- Missing date");
            return false;
        }

        final boolean expectPayload =
            (condition != null && condition.length() > 0) ||
            (notify != null && notify.length() > 0) ||
            (vars != null && vars.size() > 0);

        if (!expectPayload && map.containsKey("value"))
        {
            if (DEBUG) System.err.println("--- Unexpected value");
            return false;
        } else if (expectPayload && !map.containsKey("value")) {
            if (DEBUG) System.err.println("--- Missing value");
            return false;
        }

        if (expectPayload) {
            Map payload = (Map) map.get("value");

            if (condition == null || condition.length() == 0) {
                if (payload.containsKey("condition")) {
                    if (DEBUG) System.err.println("--- Unexpected condition");
                    return false;
                }
            } else {
                if (!payload.containsKey("condition")) {
                    if (DEBUG) System.err.println("--- Missing condition");
                    return false;
                } else if (!payload.get("condition").equals(condition)) {
                    if (DEBUG)
                        System.err.println("--- Condition \"" + condition +
                                           "\" != \"" +
                                           payload.get("condition") + "\"");
                    return false;
                }
            }

            if (notify == null || notify.length() == 0) {
                if (payload.containsKey("notify")) {
                    if (DEBUG) System.err.println("--- Unexpected notify");
                    return false;
                }
            } else {
                if (!payload.containsKey("notify")) {
                    if (DEBUG) System.err.println("--- Missing notify");
                    return false;
                } else if (!payload.get("notify").equals(notify)) {
                    if (DEBUG)
                        System.err.println("--- Notify \"" + notify +
                                           "\" != \"" + payload.get("notify") +
                                           "\"");
                    return false;
                }
            }

            Map varMap = null;
            if (vars == null || vars.size() == 0) {
                if (payload.containsKey("vars")) {
                    if (DEBUG) System.err.println("--- Unexpected vars");
                    return false;
                }
            } else {
                if (!payload.containsKey("vars")) {
                    if (DEBUG) System.err.println("--- Missing vars");
                    return false;
                } else {
                    varMap = (Map) payload.get("vars");
                }
            }

            if (varMap != null) {
                for (Object key : vars.keySet()) {
                    if (!varMap.containsKey(key)) {
                        if (DEBUG)
                            System.err.println("--- Missing var \"" + key +
                                               "\" (found " + varMap.keySet() +
                                               ")");
                        return false;
                    } else if (!compareValues(vars.get(key),
                                              varMap.get(key)))
                    {
                        if (DEBUG)
                            System.err.println("--- Var \"" + key +
                                               "\" value \"" +
                                               varMap.get(key) + "\" != \"" +
                                               vars.get(key) + "\"");
                        return false;
                    }
                }

                for (Object key : varMap.keySet()) {
                    if (!vars.containsKey(key)) {
                        if (DEBUG) System.err.println("Found extra var \"" +
                                                      key + "\"");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public String toString()
    {
        return "LiveRequest[" + service + "," + varname + "." + priority +
            "," + date + "," + condition + "," + notify + "," + vars + "]";
    }
}

class ZMQServer
    implements Runnable
{
    private static final int DEFAULT_PORT = 1234;
    private static final String DEFAULT_VARNAME = "alert";

    private Context context;
    private Socket socket;
    private int port;

    private Thread thread;
    private boolean running;

    /** Google JSON conversion object */
    private Gson gson = new Gson();

    /** List of expected requests */
    private ArrayList<LiveRequest> expected;

    /** List of errors */
    private ArrayList<String> errors;

    ZMQServer(String host)
        throws IOException
    {
        this(host, DEFAULT_PORT);
    }

    ZMQServer(String host, int port)
        throws IOException
    {
        context = ZMQ.context(10);
        socket = context.socket(ZMQ.PULL);
        socket.setLinger(0);
        socket.setReceiveTimeOut(100);

        final String addr = "tcp://" + host + ":" + port;
        socket.bind(addr);

        this.port = port;

        thread = new Thread(this);
        thread.start();
    }

    public void addError(String errMsg)
    {
        if (errors == null) {
            errors = new ArrayList<String>();
        }

        errors.add(errMsg);
    }

    public void addExpected(String service, ZMQAlerter.Priority prio,
                            Calendar date, String condition, String notify,
                            Map vars)
    {
        if (expected == null) {
            expected = new ArrayList<LiveRequest>();
        }

        expected.add(new LiveRequest(service, DEFAULT_VARNAME, prio, date,
                                     condition, notify, vars));
    }

    /**
     * Close any open files/sockets.
     */
    public void close()
        throws AlertException
    {
        AlertException ex = null;

        if (thread != null && thread.isAlive()) {
            running = false;
            try {
                thread.join();
            } catch (InterruptedException ie) {
                ex = new AlertException("Thread may not be finished", ie);
            }
        }

        if (socket != null) {
            socket.close();
            socket = null;
        }

        if (context != null) {
            context.term();
            context = null;
        }

        if (ex != null) {
            throw ex;
        }
    }

    public String getNextError()
    {
        if (errors == null || errors.size() == 0) {
            return null;
        }

        return errors.remove(0);
    }

    public int getNumberOfExpectedMessages()
    {
        if (expected == null) {
            return 0;
        }

        return expected.size();
    }

    public int getPort()
    {
        return port;
    }

    public boolean hasError()
    {
        return errors != null && errors.size() > 0;
    }

    public boolean isFinished()
    {
        return expected == null || expected.size() == 0;
    }

    @Override
    public void run()
    {
        running = true;
        while (running) {
            byte[] data = socket.recv(0);
            if (data == null) continue;

            HashMap map = gson.fromJson(new String(data), HashMap.class);
            if (map == null) {
                final String errMsg =
                    String.format("Could not unpack %d byte string \"%s\"",
                                  data.length, new String(data));
                addError(errMsg);
                continue;
            }

            boolean found = false;
            if (expected != null) {
                for (LiveRequest req : expected) {
                    if (req.equals(map)) {
                        expected.remove(req);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                addError("Unexpected request " + map);
            }
        }
    }

    public void waitForMessages()
    {
        final int numReps = 1000;
        final int sleepTime = 10;
        for (int i = 0; !hasError() && !isFinished() && i < numReps; i++) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
        if (hasError()) throw new Error(getNextError());
        if (getNumberOfExpectedMessages() != 0) {
            throw new Error("Didn't receive " + getNumberOfExpectedMessages() +
                            " expected request");
        }
    }
}

public class ZMQAlerterTest
    extends TestCase
{
    private ZMQServer server;

    public ZMQAlerterTest(String name)
    {
        super(name);
    }

    private static void addExpectedAlert(ZMQServer server, String service,
                                         ZMQAlerter.Priority prio,
                                         Calendar date, String condition,
                                         String notify,
                                         Map<String, Object> vars)
    {
        server.addExpected(service, prio, date, condition, notify, vars);
    }

    public static Test suite()
    {
        return new TestSuite(ZMQAlerterTest.class);
    }

    @Override
    protected void tearDown()
        throws AlertException
    {
        if (server != null) {
            server.close();

            if (server.hasError()) fail(server.getNextError());
            assertEquals("Not all log messages were received",
                         0, server.getNumberOfExpectedMessages());
        }
    }

    public void testSimple()
    {
        ZMQAlerter alerter = new ZMQAlerter();
        assertFalse("New Alerter should not be active", alerter.isActive());
        alerter.close();
    }

    public void testNullHost()
        throws AlertException
    {
        try {
            server = new ZMQServer("127.0.0.1");
        } catch (IOException ioe) {
            fail("Couldn't create 0MQ server: " + ioe.getMessage());
        }

        ZMQAlerter alerter = new ZMQAlerter();
        alerter.setAddress(null, server.getPort());

        final ZMQAlerter.Priority prio = ZMQAlerter.Priority.ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = null;
        final String notify = null;
        final Map vars = null;

        addExpectedAlert(server, alerter.getService(), prio, date, condition,
                         notify, vars);

        alerter.sendAlert(date, prio, condition, notify, vars);

        server.waitForMessages();

        alerter.close();
    }

    public void testBadHost()
    {
        ZMQAlerter alerter = new ZMQAlerter();

        try {
            alerter.setAddress("impossible host name", 9999);
            fail("bad host name; should not succeed");
        } catch (AlertException ae) {
            // ignore exception
        }
    }

/*
  // JZMQ allows connections to bad ports!!
    public void XXXtestBadPort()
    {
        ZMQAlerter alerter = new ZMQAlerter();

        try {
            alerter.setAddress("127.0.0.1", 9999);
            fail("bad host name; should not succeed");
        } catch (AlertException ae) {
            // ignore exception
        }
    }
*/

    public void testSendNoPayload()
        throws AlertException
    {
        try {
            server = new ZMQServer("127.0.0.1");
        } catch (IOException ioe) {
            fail("Couldn't create 0MQ server: " + ioe.getMessage());
        }

        ZMQAlerter alerter = new ZMQAlerter();
        alerter.setAddress("127.0.0.1", server.getPort());

        final ZMQAlerter.Priority prio = ZMQAlerter.Priority.ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = null;
        final String notify = null;
        final Map<String, Object> vars = null;

        addExpectedAlert(server, alerter.getService(), prio, date, condition,
                         notify, vars);

        alerter.sendAlert(date, prio, condition, notify, vars);

        server.waitForMessages();

        alerter.close();
    }

    public void testSendMinimal()
        throws AlertException
    {
        try {
            server = new ZMQServer("127.0.0.1");
        } catch (IOException ioe) {
            fail("Couldn't create 0MQ server: " + ioe.getMessage());
        }

        ZMQAlerter alerter = new ZMQAlerter();
        alerter.setAddress("127.0.0.1", server.getPort());

        final ZMQAlerter.Priority prio = ZMQAlerter.Priority.ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = "Send Minimal";
        final String notify = null;

        final Map<String, Object> vars = null;

        addExpectedAlert(server, alerter.getService(), prio, date, condition,
                         notify, vars);

        alerter.sendAlert(date, prio, condition, notify, vars);

        server.waitForMessages();

        alerter.close();
    }

    public void testSendVars()
        throws AlertException
    {
        try {
            server = new ZMQServer("127.0.0.1");
        } catch (IOException ioe) {
            fail("Couldn't create 0MQ server: " + ioe.getMessage());
        }

        ZMQAlerter alerter = new ZMQAlerter();
        alerter.setAddress("127.0.0.1", server.getPort());

        final ZMQAlerter.Priority prio = ZMQAlerter.Priority.ITS;
        final Calendar date = Calendar.getInstance();
        final String condition = "Send Vars";
        final String notify = "foo@bar.baz";

        final Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("int", 123);
        vars.put("real", 123.456);
        vars.put("str", "foo");

        addExpectedAlert(server, alerter.getService(), prio, date, condition,
                         notify, vars);

        alerter.sendAlert(date, prio, condition, notify, vars);

        server.waitForMessages();

        alerter.close();
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
