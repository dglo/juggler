package icecube.daq.juggler.component;

import icecube.daq.common.IDAQAppender;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.log.BasicAppender;
import icecube.daq.log.DAQLogAppender;
import icecube.daq.log.DAQLogHandler;
import icecube.daq.log.LoggingOutputStream;
import icecube.daq.util.FlasherboardConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.webserver.WebServer;

/**
 * Print a series of characters in succession in order to animate a 'spinner'.
 *
 * A typical use is <tt>new Spinner("\\|/-")</tt> followed by a loop containing
 * <tt>spin.print()</tt>
 */
class Spinner
{
    /** string of spinner 'cels' */
    private String str;
    /** current offset */
    private int index;

    /**
     * Create a spinner.
     *
     * @param str string of characters to animate
     */
    Spinner(String str)
    {
        this.str = str;

        index = 0;
    }

    /**
     * Print next character in the sequence.
     */
    public void print()
    {
        System.err.print(str.charAt(index) + "\r");
        index = (index + 1) % str.length();
    }
}

/**
 * Logging options
 */
class LogOptions
{
    private String logHost;
    private int logPort;
    private Level logLevel;
    private String liveHost;
    private int livePort;
    private Level liveLevel;
    private boolean isSet;

    boolean isSet()
    {
        return isSet;
    }

    /**
     * Get logging level for the specified string.
     *
     * @param levelStr one of 'off', 'fatal', 'error', 'warn', 'debug', or
     *                 'all' (in increasing verbosity)
     *
     * @return <tt>null</tt> if the string was not a valid level
     */
    private static Level getLogLevel(String levelStr)
    {
        Level logLevel;
        if (levelStr.equalsIgnoreCase("off") ||
            levelStr.equalsIgnoreCase("none"))
        {
            logLevel = Level.OFF;
        } else if (levelStr.equalsIgnoreCase("fatal")) {
            logLevel = Level.FATAL;
        } else if (levelStr.equalsIgnoreCase("error")) {
            logLevel = Level.ERROR;
        } else if (levelStr.equalsIgnoreCase("warn")) {
            logLevel = Level.WARN;
        } else if (levelStr.equalsIgnoreCase("info")) {
            logLevel = Level.INFO;
        } else if (levelStr.equalsIgnoreCase("debug")) {
            logLevel = Level.DEBUG;
            //} else if (levelStr.equalsIgnoreCase("trace")) {
            //    logLevel = Level.TRACE;
        } else if (levelStr.equalsIgnoreCase("all")) {
            logLevel = Level.ALL;
        } else {
            logLevel = null;
        }

        return logLevel;
    }

    boolean process(String addrStr, boolean isLive)
    {
        int ic = addrStr.indexOf(':');
        int il = addrStr.indexOf(',');
        if (ic < 0 && il < 0) {
            System.err.println("Bad log argument '" + addrStr + "'");
            return false;
        }

        String levelStr;
        if (il < 0) {
            levelStr = "ERROR";
            il = addrStr.length();
        } else {
            levelStr = addrStr.substring(il+1);
        }

        Level level = getLogLevel(levelStr);
        if(level == null) {
            System.err.println("Bad log level: '" + levelStr + "'");
            return false;
        }

        String host;
        int port;

        if (ic < 0) {
            host = null;
            port = 0;
        } else {
            if (ic == 0) {
                host = "localhost";
            } else {
                host = addrStr.substring(0, ic);
            }

            String portStr  = addrStr.substring(ic + 1, il);

            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.err.println("Bad log port '" + portStr + "' in '" +
                                   addrStr + "'");
                return false;
            }
        }

        if (isLive) {
            liveHost = host;
            livePort = port;
            liveLevel = level;
        } else {
            logHost = host;
            logPort = port;
            logLevel = level;
        }

        if (logLevel != null && liveLevel != null &&
            !logLevel.equals(liveLevel))
        {
            System.err.println("I3Live logging level " + liveLevel +
                               " does not match pDAQ logging level " +
                               logLevel);
            return false;
        }

        isSet = true;
        return true;
    }

    boolean configure(DAQCompServer server, DAQComponent comp)
    {
        comp.setLogLevel(logLevel);
        try {
            server.setDefaultLoggingConfiguration(logLevel, logHost, logPort,
                                                  liveHost, livePort);
        } catch (UnknownHostException uhe) {
            System.err.println("Bad log host '" + logHost + "' or live host '" +
                               liveHost + "'");
            return false;
        } catch (SocketException se) {
            System.err.println("Couldn't set logging to '" + logHost +
                               "' or '" + liveHost + "'");
            return false;
        }

        try {
            comp.getAlerter().setLive(liveHost, livePort);
        } catch (AlertException ae) {
            System.err.println("Couldn't set alerter to '" + liveHost +
                               ":" + livePort + "'");
        }

        return true;
    }
}

/**
 * Logging configuration
 */
class LoggingConfiguration
{
    private String compName;
    private Level logLevel;
    private String logHost;
    private int logPort;
    private String liveHost;
    private int livePort;
    private IDAQAppender appender;
    private Handler handler;

    LoggingConfiguration(String compName, Level logLevel)
        throws SocketException, UnknownHostException
    {
        this(compName, logLevel, null, 0, null, 0);
    }

    LoggingConfiguration(String compName, Level logLevel, String logHost,
                         int logPort, String liveHost, int livePort)
        throws SocketException, UnknownHostException
    {
        this.compName = compName;
        this.logLevel = logLevel;
        this.logHost = logHost;
        this.logPort = logPort;
        this.liveHost = liveHost;
        this.livePort = livePort;

        if ((logHost == null || logHost.length() == 0 || logPort <= 0) &&
            (liveHost == null || liveHost.length() == 0 || livePort <= 0))
        {
            setBasic();
            System.out.println("WARNING: using STDOUT logging!");
        } else {
            appender =
                new DAQLogAppender(compName, logLevel, logHost, logPort,
                                   liveHost, livePort);
            handler =
                new DAQLogHandler(compName, getUtilLevel(logLevel), logHost,
                                  logPort, liveHost, livePort);
        }
    }

    LoggingConfiguration(IDAQAppender appender, Handler handler, Level logLevel)
    {
        this.compName = null;
        this.logHost = null;
        this.logPort = -1;
        this.liveHost = null;
        this.livePort = -1;
        this.logLevel = logLevel;
        this.appender = appender;
        this.handler = handler;
    }

    void configure()
        throws SocketException
    {
        BasicConfigurator.resetConfiguration();

        if (!appender.isConnected()) {
            appender.reconnect();
        }

        BasicConfigurator.configure(appender);

        Log log = LogFactory.getLog(getClass());

        if (log instanceof Log4JLogger) {
            ((Log4JLogger) log).getLogger().getRootLogger().setLevel(logLevel);
        } else {
            log.error("Cannot reset log level for " +
                      log.getClass().getName());
        }

        // find base logger
        Logger baseLogger = Logger.getLogger("");
        while (baseLogger.getParent() != null) {
            baseLogger = baseLogger.getParent();
        }

        // clear out default handlers
        Handler[] hList = baseLogger.getHandlers();
        for (int i = 0; i < hList.length; i++) {
            baseLogger.removeHandler(hList[i]);
        }

        baseLogger.addHandler(handler);
    }

    private java.util.logging.Level getUtilLevel(Level level)
    {
        if (level.equals(Level.ALL)) {
            return java.util.logging.Level.ALL;
        }

        if (level.equals(Level.OFF)) {
            return java.util.logging.Level.OFF;
        }

        if (level.toInt() > Level.WARN.toInt()) {
            return java.util.logging.Level.SEVERE;
        }

        if (level.toInt() > Level.INFO.toInt()) {
            return java.util.logging.Level.WARNING;
        }

        if (level.toInt() > Level.DEBUG.toInt()) {
            return java.util.logging.Level.INFO;
        }

        return java.util.logging.Level.FINE;
    }

    boolean matches(Level logLevel, String logHost, int logPort,
                    String liveHost, int livePort)
    {
        if (appender instanceof BasicAppender) {
            return ((logHost == null || logHost.length() == 0 ||
                     logPort <= 0) ||
                    (liveHost == null || liveHost.length() == 0 ||
                     livePort <= 0));
        }

        IDAQAppender daqApp = appender;

        if (!daqApp.getLevel().equals(logLevel)) {
            return false;
        }

        if (!daqApp.isConnected(logHost, logPort, liveHost, livePort)) {
            return false;
        }

        return true;
    }

    void setBasic()
    {
        appender = new BasicAppender(logLevel);
        handler = new StreamHandler(System.out, new SimpleFormatter());
    }

    public String toString()
    {
        return compName + ":" +
            (logHost == null ? "" : "DAQ[" + logHost + ":" + logPort + "]") +
            (liveHost == null ? "" : "I3Live[" + liveHost + ":" + livePort +
             "]") + "@" + logLevel;
    }
}

/**
 * Server code which wraps around a DAQ component.
 */
public class DAQCompServer
{
    /**
     * Frequency (in milliseconds) that config server is 'pinged' to
     * check that it's still alive.
     */
    private static final int PING_FREQUENCY = 10000;

    /** XML-RPC parameter list for server ping */
    private static final Object[] NO_PARAMS = new Object[0];

    /** Logger for most output */
    private static final Log LOG = LogFactory.getLog(DAQCompServer.class);

    /** Set to <tt>true</tt> to redirect standard output to logging stream */
    private static final boolean REDIRECT_STDOUT = false;
    /** Original standard output stream */
    private static final PrintStream STDOUT = System.out;
    /** Standard output logging stream */
    private static PrintStream outLogStream;

    /** Set to <tt>true</tt> to redirect standard error to logging stream */
    private static final boolean REDIRECT_STDERR = true;
    /** Original standard error stream */
    private static final PrintStream STDERR = System.err;
    /** Standard error logging stream */
    private static PrintStream errLogStream;

    /** Component being served. */
    private static DAQComponent comp;

    /** Server ID (used for rpc_ping) */
    private static int serverId;
    /** <tt>true</tt> if server ID has been set */
    private static boolean serverIdSet;

    /** default logging configuration. */
    private static LoggingConfiguration defaultLogConfig;

    /** if <tt>true</tt>, show spinners */
    private static boolean showSpinner;

    /** URL of configuration server */
    private URL configURL;

    /**
     * XML-RPC stub
     */
    public DAQCompServer()
    {
    }

    /**
     * Wrap a DAQ component.
     *
     * @param comp component being wrapped
     * @param args command-line arguments
     *
     * @throws DAQCompException if there is a problem
     */
    public DAQCompServer(DAQComponent comp, String[] args)
        throws DAQCompException
    {
        this.comp = comp;
        comp.setLogLevel(Level.INFO);

        processArgs(comp, args);

        resetLoggingConfiguration();
    }

    /**
     * Register the component with the config server.
     *
     * @param client object used to communicate with config server
     * @param comp DAQ component being registered
     * @param host local host name
     * @param port local port to which config server should connect
     */
    private static void announce(XmlRpcClient client, DAQComponent comp,
                                 String host, int port)
        throws XmlRpcException
    {
        Map rtnMap = sendRegistration(client, comp, host, port);
        if (rtnMap.size() != 6) {
            throw new XmlRpcException("Expected 6-element hashmap, got " +
                                      rtnMap.size() + " elements");
        }

        final int compId = ((Integer) rtnMap.get("id")).intValue();
        final String logIP = (String) rtnMap.get("logIP");
        final int logPort = ((Integer) rtnMap.get("logPort")).intValue();
        final String liveIP = (String) rtnMap.get("liveIP");
        final int livePort = ((Integer) rtnMap.get("livePort")).intValue();
        final int tmpServerId =  ((Integer) rtnMap.get("serverId")).intValue();

        if (serverId != tmpServerId) {
            setServerId(tmpServerId);
        }

        comp.setId(compId);

        try {
            initializeLogging(logIP, logPort, liveIP, livePort);
        } catch (UnknownHostException uhe) {
            throw new XmlRpcException("Unknown log host '" + logIP +
                                      "' or live host '" + liveIP + "'", uhe);
        } catch (SocketException se) {
            throw new XmlRpcException("Couldn't connect to log '" + logIP +
                                      ":" + logPort + "' or live '" + liveIP +
                                      ":" + livePort + "'", se);
        }
    }

    /**
     * Build the object used to communicate with the config server.
     *
     * @param cfgServerURL configuration server URL
     *
     * @return communication object
     */
    private XmlRpcClient buildClient(URL cfgServerURL)
        throws DAQCompException
    {
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(cfgServerURL);

        XmlRpcClient client = new XmlRpcClient();
        client.setConfig(config);

        return client;
    }

    /**
     * XML-RPC method telling the event builder to begin packaging events for
     * the specified subrun.
     *
     * @param subrunNumber subrun number
     * @param timeStr string representing time of first good hit in subrun
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     */
    public String commitSubrun(int subrunNumber, String timeStr)
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        if (timeStr.endsWith("L")) {
            timeStr = timeStr.substring(0, timeStr.length() - 1);
        }

        long startTime;
        try {
            startTime = Long.parseLong(timeStr);
        } catch (NumberFormatException nfe) {
            throw new DAQCompException("Bad time '" + timeStr +
                                       "' for subrun " + subrunNumber);
        }

        comp.commitSubrun(subrunNumber, startTime);

        return "OK";
    }

    /**
     * XML-RPC method to 'configure' a component which needs no configuration.
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     *
     * @deprecated this should no longer happen!
     */
    public String configure()
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.configure();

        return "OK";
    }

    /**
     * XML-RPC method to configure a component using
     * the specified configuration name.
     *
     * @param configName configuration name
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     */
    public String configure(String configName)
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.configure(configName);

        return "OK";
    }

    /**
     * XML-RPC method to 'connect' a component with no output connections.
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     * @throws IOException if there is a problem creating a connection
     */
    public String connect()
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.connect();

        return "OK";
    }

    /**
     * XML-RPC method to connect the component using the specified
     * connection descriptions.
     *
     * @param objList list of connections
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     * @throws IOException if there is a problem creating a connection
     */
    public String connect(Object[] objList)
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        if (objList == null || objList.length == 0) {
            throw new DAQCompException("Empty/null list of connections");
        }

        Connection[] connList = new Connection[objList.length];
        for (int i = 0; i < objList.length; i++) {
            if (!(objList[i] instanceof Map)) {
                throw new DAQCompException("Unexpected connect element #" +
                                           i + ": " +
                                           objList[i].getClass().getName());
            }

            Map map = (Map) objList[i];

            String type = (String) map.get("type");
            String compName = (String) map.get("compName");
            int compNum = ((Integer) map.get("compNum")).intValue();
            String host = (String) map.get("host");
            int port = ((Integer) map.get("port")).intValue();

            connList[i] = new Connection(type, compName, compNum, host, port);
        }

        comp.connect(connList);

        return "OK";
    }

    /**
     * XML-RPC method to destroy a component.
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     */
    public String destroy()
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.destroy();

        return "OK";
    }

    /**
     * XML-RPC method forcing the specified component to stop current run.
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     * @throws IOException if there was a problem stopping the component
     */
    public String forcedStop()
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.forcedStop();
        return "OK";
    }

    /**
     * XML-RPC method to get the number of subrun events from a component.
     *
     * @param subrun subrun number
     *
     * @return number of events for the subrun
     *
     * @throws DAQCompException if component or subrun does not exist
     */
    public String getEvents(int subrun)
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        long evts = comp.getEvents(subrun);
        return Long.toString(evts) + "L";
    }

    /**
     * XML-RPC method to return component state.
     *
     * @return component state
     *
     * @throws DAQCompException if component does not exist
     */
    public String getState()
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        return comp.getStateString();
    }

    /**
     * XML-RPC method requesting the specified component's version information.
     *
     * @return <tt>a string containing the svn version info</tt>
     *
     * @throws DAQCompException if component does not exist
     */
    public String getVersionInfo()
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        return comp.getVersionInfo();
    }

    /**
     * Initialize the logging system of this component.
     *
     * @param logIP DAQ host name/address
     * @param logPort DAQ port number
     * @param logIP I3Live host name/address
     * @param logPort I3Live port number
     *
     * @throws SocketException if the host address is not valid
     * @throws SocketException if there is a problem connecting to the logger
     */
    public static void initializeLogging(String logIP, int logPort,
                                         String liveIP, int livePort)
        throws SocketException, UnknownHostException
    {
        setDefaultLoggingConfiguration(comp.getLogLevel(), logIP, logPort,
                                       liveIP, livePort);
        resetLoggingConfiguration();
    }

    /**
     * XML-RPC method to list connector states.
     *
     * @return <tt>list of connector states</tt>
     *
     * @throws DAQCompException if component does not exist
     */
    public HashMap[] listConnectorStates()
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        ArrayList list = new ArrayList();

        for (Iterator iter = comp.listConnectors(); iter.hasNext(); ) {
            DAQConnector conn = (DAQConnector) iter.next();

            HashMap map = new HashMap();
            map.put("type", conn.getType());
            map.put("state", conn.getState().toLowerCase());
            list.add(map);
        }

        HashMap[] array = new HashMap[list.size()];
        return (HashMap[]) list.toArray(array);
    }

    /**
     * XML-RPC method to tell a component where to log
     *
     * @param logHost DAQ log host address
     * @param logPort DAQ log host port
     * @param liveHost I3Live log host address
     * @param livePort I3Live log host port
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     * @throws IOException if logging configuration could not be set
     */
    public String logTo(String logHost, int logPort, String liveHost,
                        int livePort)
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        setLoggingConfiguration(new LoggingConfiguration(comp.getName(),
                                                         comp.getLogLevel(),
                                                         logHost, logPort,
                                                         liveHost, livePort));

        return "OK";
    }

    /**
     * Check that the server is still alive.
     *
     * @param client object used to communicate with config server
     * @param comp component to notify if server dies
     *
     * @return <tt>false</tt> if component was destroyed
     */
    private boolean monitorServer(XmlRpcClient client, DAQComponent comp)
    {
        Spinner spinner;
        if (showSpinner) {
            spinner = new Spinner("-\\|/");
        } else {
            spinner = null;
        }

        int numFails = 0;
        boolean compDestroyed = false;

        // wait for the end of time
        while (true) {
            try {
                Thread.sleep(PING_FREQUENCY);
            } catch (InterruptedException ex) {
                // ignore interrupts
            }

            if (pingServer(client)) {
                numFails = 0;
            } else if (++numFails >= 3) {
                compDestroyed = comp.serverDied();
                break;
            }

            if (spinner != null) {
                spinner.print();
            }
        }

        return !compDestroyed;
    }

    /**
     * XML-RPC method to respond to server that this component is still alive.
     *
     * @return 'ping' response
     */
    public String ping()
    {
        return "OK";
    }

    /**
     * Check that the server is still alive.
     *
     * @param client object used to communicate with config server
     *
     * @return <tt>true</tt> if the server responds to the request
     */
    private static boolean pingServer(XmlRpcClient client)
    {
        boolean sawServer;
        try {
            Object obj = client.execute("rpc_ping", NO_PARAMS);

            int val = ((Integer) obj).intValue();

            sawServer = (val == serverId);
        } catch (XmlRpcException xre) {
            if (!(xre.linkedException instanceof ConnectException)) {
                xre.printStackTrace();
            }

            sawServer = false;
        }

        return sawServer;
    }

    /**
     * XML-RPC method requesting the specified component to prepare for a
     * subrun.
     *
     * @param subrunNumber subrun number
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     */
    public String prepareSubrun(int subrunNumber)
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.prepareSubrun(subrunNumber);

        return "OK";
    }

    /**
     * Process command-line arguments.
     */
    private void processArgs(DAQComponent comp, String[] args)
    {
        LogOptions logOpt = new LogOptions();

        boolean usage = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].length() > 1 && args[i].charAt(0) == '-') {
                switch(args[i].charAt(1)) {
                case 'L':
                    i++;
                    if (!logOpt.process(args[i], true)) {
                        usage = true;
                    }
                    break;
                case 'M':
                    i++;

                    int secs;
                    try {
                        secs = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad monitoring interval '" +
                                           args[i] + "'");
                        usage = true;
                        break;
                    }

                    comp.enableLocalMonitoring(secs);
                    break;
                case 'S':
                    showSpinner = true;
                    break;
                case 'c':
                    i++;
                    String urlStr = args[i];
                    if (!urlStr.startsWith("http://")) {
                        urlStr = "http://" + urlStr;
                    }

                    try {
                        configURL = new URL(urlStr);
                    } catch (MalformedURLException mue) {
                        System.err.println("Bad configuration URL \"" +
                                           urlStr + "\"");
                        usage = true;
                    }
                    break;
                case 'd':
                    i++;
                    comp.setDispatchDestStorage(args[i]);
                    break;
                case 'g':
                    i++;
                    String glDir = args[i];
                    comp.setGlobalConfigurationDir(glDir);
                    break;
                case 'l':
                    i++;
                    if (!logOpt.process(args[i], false)) {
                        usage = true;
                    }
                    break;
                case 's':
                    i++;
                    long maxFileSize = 0;
                    try {
                        maxFileSize = Long.parseLong(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad file size = " + args[i]);
                        usage = true;
                        break;
                    }
                    comp.setMaxFileSize(maxFileSize);
                    break;
                default:
                    int numHandled = comp.handleOption(args[i],
                                                       (i + 1 == args.length ?
                                                        null : args[i + 1]));
                    if (numHandled > 0) {
                        i += numHandled - 1;
                    } else if (numHandled == 0) {
                        System.err.println("Unknown option '" + args[i] + "'");
                        usage = true;
                    }
                    break;
                }
            } else if (args[i].length() > 0) {
                System.err.println("Unknown argument '" + args[i] + "'");
                usage = true;
            }
        }

        if (configURL == null) {
            try {
                configURL = new URL("http", "localhost", 8080, "");
            } catch (MalformedURLException mue) {
                System.err.println("Couldn't build local configuration URL");
                usage = true;
            }
        }

        if (logOpt.isSet()) {
            logOpt.configure(this, comp);
        }

        if (usage) {
            String usageMsg = "java " + comp.getClass().getName() + " " +
                " [-M localMoniSeconds]" +
                " [-S(howSpinner)]" +
                " [-c configServerURL]" +
                " [-d dispatchDestPath]" +
                " [-g globalConfigPath]" +
                " [-l logAddress:logPort,logLevel]" +
                " [-s maxDispatchFileSize]" +
                comp.getOptionUsage();
            throw new IllegalArgumentException(usageMsg);
        }
    }

    /**
     * XML-RPC method to reset the specified component back to the idle state.
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if there is a problem
     * @throws IOException if there is a problem cleaning up I/O
     */
    public String reset()
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        resetLoggingConfiguration();

        comp.reset();

        return "OK";
    }

    /**
     * XML-RPC method to reset the specified component's logging back to
     * the default logger.
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if there is a problem
     */
    public String resetLogging()
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        resetLoggingConfiguration();

        return "OK";
    }

    /**
     * Reset logging to the default configuration.
     */
    private static void resetLoggingConfiguration()
    {
        if (defaultLogConfig == null) {
            System.err.println("WARNING: null default logging configuration!");
            try {
                defaultLogConfig =
                    new LoggingConfiguration(comp.getName(), Level.INFO);
            } catch (SocketException sex) {
                throw new Error("Unexpected exception", sex);
            } catch (UnknownHostException uhe) {
                throw new Error("Unexpected exception", uhe);
            }
        }

        setLoggingConfiguration(defaultLogConfig);
    }

    /**
     * Reset the statically cached values.
     * This is only meant for unit testing.
     */
    public static void resetStaticValues()
    {
        comp = null;
        serverId = 0;
        serverIdSet = false;
        defaultLogConfig = null;
        showSpinner = false;
    }

    /**
     * Main loop for component.
     *
     * @param comp component being served
     * @param cfgServerURL configuration server URL
     *
     * @throws DAQCompException if there is a problem
     */
    private void runEverything(DAQComponent comp, URL cfgServerURL)
        throws DAQCompException
    {
        WebServer webServer = startServer();
        if (LOG.isDebugEnabled()) {
            LOG.debug("XML-RPC on port " + webServer.getPort());
        }

        try {
            XmlRpcClient client = buildClient(cfgServerURL);

            while (true) {
                try {
                    sendAnnouncement(client, comp, webServer.getPort());
                } catch (Exception ex) {
                    LOG.error("Couldn't announce " + comp, ex);
                    continue;
                }

                try {
                    if (!monitorServer(client, comp)) {
                        break;
                    }
                } catch (Exception ex) {
                    LOG.error("Couldn't monitor " + comp, ex);
                    continue;
                }
            }
        } finally {
            webServer.shutdown();
        }
    }

    /**
     * Register this component with the config server.
     *
     * @param client object used to communicate with config server
     * @param comp DAQ component being registered
     * @param port local port to which config server should connect
     */
    private static void sendAnnouncement(XmlRpcClient client, DAQComponent comp,
                                         int port)
        throws DAQCompException
    {
        Spinner spinner;
        if (showSpinner) {
            spinner = new Spinner("?*");
        } else {
            spinner = null;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException uhe) {
            throw new DAQCompException("Couldn't get local host name", uhe);
        }

        // keep trying until announce() succeeds
        while (true) {
            try {
                announce(client, comp, addr.getHostAddress(), port);
                break;
            } catch (XmlRpcException xre) {
                if (!(xre.linkedException instanceof ConnectException)) {
                    final String errMsg = "Couldn't announce component";
                    throw new DAQCompException(errMsg, xre);
                }

                if (spinner != null) {
                    spinner.print();
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    // ignore interrupts
                }
            }
        }
    }

    /**
     * Send registration data to the DAQ control server.
     *
     * @param client XML-RPC client
     * @param comp DAQ component
     * @param host local host name/address
     * @param port local XML-RPC port number
     *
     * @return array containing component ID, logging host, logging port,
     *         I3Live flag, and new server ID
     *
     * @throws XmlRpcException if the registration message could not be sent
     */
    private static Map sendRegistration(XmlRpcClient client, DAQComponent comp,
                                        String host, int port)
        throws XmlRpcException
    {
        ArrayList connList = new ArrayList();

        for (Iterator iter = comp.listConnectors(); iter.hasNext();) {
            DAQConnector conn = (DAQConnector) iter.next();
            if (!conn.isInput() && !conn.isOutput()) {
                continue;
            }

            String type = conn.getType();
            if (type.equals(DAQConnector.TYPE_SELF_CONTAINED)) {
                continue;
            }

            Object[] row = new Object[3];
            row[0] = type;
            row[1] = conn.isInput() ? Boolean.TRUE : Boolean.FALSE;
            row[2] = new Integer(conn.getPort());

            connList.add(row);
        }

        Object[] params = new Object[] {
            comp.getName(), new Integer(comp.getNumber()),
            host, new Integer(port), new Integer(comp.getMBeanXmlRpcPort()),
            connList.toArray(),
        };

        Object rtnObj = client.execute("rpc_register_component", params);
        if (rtnObj == null) {
            throw new XmlRpcException("rpc_register_component returned null");
        } else if (!(rtnObj instanceof Map)) {
            throw new XmlRpcException("Unexpected return object [ " + rtnObj +
                                      "] (type " +
                                      rtnObj.getClass().getName() + ")");
        }

        return (Map) rtnObj;
    }

    /**
     * Set the default logging configuration.
     *
     * @param logIP log host address
     * @param logPort log host port
     * @param logLevel log level
     * @param isLiveLog <tt>true</tt> if this is an I3Live log
     */
    static void setDefaultLoggingConfiguration(Level logLevel,
                                               String logIP, int logPort,
                                               String liveIP, int livePort)
        throws SocketException, UnknownHostException
    {
        if (defaultLogConfig == null ||
            !defaultLogConfig.matches(logLevel, logIP, logPort, liveIP,
                                      livePort))
        {
            defaultLogConfig =
                new LoggingConfiguration(comp.getName(), logLevel, logIP,
                                         logPort, liveIP, livePort);
        }
    }

    /**
     * Set the default logging configuration for unit testing.
     *
     * @param appender mock appender
     * @param handler mock handler
     * @param logLevel log level
     */
    public static void setDefaultLoggingConfiguration(IDAQAppender appender,
                                                      Handler handler)
    {
        defaultLogConfig =
            new LoggingConfiguration(appender, handler, Level.WARN);
    }

    /**
     * Set logging to the specified configuration.
     */
    private static void setLoggingConfiguration(LoggingConfiguration logConfig)
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resetting logging");
        }

        if (logConfig.equals(defaultLogConfig)) {
            if (REDIRECT_STDOUT && !STDOUT.equals(System.out)) {
                System.out.flush();
                System.setOut(STDOUT);
            }
            if (REDIRECT_STDERR && !STDERR.equals(System.err)) {
                System.err.flush();
                System.setErr(STDERR);
            }
        } else {
            if (REDIRECT_STDOUT && STDOUT.equals(System.out)) {
                if (outLogStream == null) {
                    Log outLog = LogFactory.getLog("STDOUT");
                    LoggingOutputStream tmpStream =
                        new LoggingOutputStream(outLog, Level.INFO);
                    outLogStream = new PrintStream(tmpStream);
                }
                System.out.flush();
                System.setOut(outLogStream);
            }
            if (REDIRECT_STDERR && STDERR.equals(System.err)) {
                if (errLogStream == null) {
                    Log errLog = LogFactory.getLog("STDERR");
                    LoggingOutputStream tmpStream =
                        new LoggingOutputStream(errLog, Level.ERROR);
                    errLogStream = new PrintStream(tmpStream);
                }
                System.err.flush();
                System.setErr(errLogStream);
            }
        }

        try {
            logConfig.configure();
        } catch (SocketException se) {
            if (logConfig != defaultLogConfig) {
                setLoggingConfiguration(defaultLogConfig);
            } else {
                logConfig.setBasic();
                try {
                    logConfig.configure();
                    System.out.println("Fell back to basic logging");
                } catch (SocketException se2) {
                    System.out.println("Could not fall back to basic logging");
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Logging has been reset");
        }
    }

    /**
     * Set the internal server ID, used to check responses to rpc_pings.
     *
     * @param newId new server ID
     */
    private static void setServerId(int newId)
    {
        if (serverIdSet) {
            LOG.error("Changing server ID from " + serverId + " to " + newId);
        }

        serverId = newId;
        serverIdSet = true;
    }

    /**
     * XML-RPC method requesting the specified component to start a run.
     *
     * @param runNumber run number
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     * @throws IOException if there was a problem starting the component
     */
    public String startRun(int runNumber)
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.startRun(runNumber);

        return "OK";
    }

    /**
     * Start the XML-RPC server for the specified component.
     *
     * @throws DAQCompException if there is a problem
     */
    private WebServer startServer()
        throws DAQCompException
    {
        int port;

        ServerSocket ss;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(null);
            port = ss.getLocalPort();
            ss.close();
        } catch (Exception ex) {
            throw new DAQCompException("Couldn't search for port", ex);
        }

        WebServer webServer;
        while (true) {
            webServer = new WebServer(port);
            try {
                webServer.start();
                break;
            } catch (BindException be) {
                System.err.println("Port " + port + " is in use");
                port++;
            } catch (IOException ioe) {
                throw new DAQCompException("Couldn't start web server", ioe);
            }
        }

        XmlRpcServer server = webServer.getXmlRpcServer();

        PropertyHandlerMapping propMap = new PropertyHandlerMapping();
        try {
            propMap.addHandler("xmlrpc", getClass());
        } catch (XmlRpcException xre) {
            throw new DAQCompException("Couldn't handle component XML-RPC",
                                       xre);
        }
        server.setHandlerMapping(propMap);

        XmlRpcServerConfigImpl serverConfig =
            (XmlRpcServerConfigImpl) server.getConfig();
        serverConfig.setEnabledForExtensions(true);
        serverConfig.setContentLengthOptional(false);

        return webServer;
    }

    /**
     * Run the server.
     */
    public void startServing()
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component has not been set");
        }

        comp.start();

        try {
            runEverything(comp, configURL);
        } finally {
            comp.destroy();
        }
    }

    /**
     * XML-RPC method requesting the specified component to start a subrun.
     * Note that an empty list signals the end of the subrun.
     *
     * @param subrunNumber subrun number
     * @param rawData Python-formatted subrun data
     *
     * @return start time
     *
     * @throws DAQCompException if component does not exist
     */
    public String startSubrun(List rawData)
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        if (rawData == null) {
            throw new DAQCompException("Null list of flashers");
        }

        ArrayList<FlasherboardConfiguration> data =
            new ArrayList<FlasherboardConfiguration>();

        int n = 0;
        for (Iterator iter = rawData.iterator(); iter.hasNext(); n++) {
            Object[] array = (Object[]) iter.next();

            if (array.length != 6) {
                throw new DAQCompException("Configuration entry #" +
                                           n + " has only " + array.length +
                                           " fields");
            }

            try {
                String mbid = (String) array[0];

                int[] vals = new int[5];
                for (int i = 0; i < vals.length; i++) {
                    vals[i] = ((Integer) array[i + 1]).intValue();
                }

                data.add(new FlasherboardConfiguration(mbid, vals[0], vals[1],
                                                       vals[2], vals[3],
                                                       vals[4]));
            } catch (Exception ex) {
                throw new DAQCompException("Couldn't build config array", ex);
            }
        }

        return Long.toString(comp.startSubrun(data)) + "L";
    }

    /**
     * XML-RPC method requesting the specified component to stop current run.
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     * @throws IOException if there was a problem stopping the component
     */
    public String stopRun()
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        comp.stopRun();
        return "OK";
    }
}
