package icecube.daq.juggler.component;

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

import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.Appender;
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
 * Logging configuration
 */
class LoggingConfiguration
{
    private Appender appender;
    private Handler handler;

    LoggingConfiguration(Level logLevel)
    {
        setBasic(logLevel);
    }

    LoggingConfiguration(String host, int port, Level logLevel)
        throws SocketException, UnknownHostException
    {
        if (host == null || host.length() == 0 || port <= 0) {
            setBasic(logLevel);
            System.out.println("WARNING: using STDOUT logging!");
        } else {
            appender = new DAQLogAppender(logLevel, host, port);
            handler = new DAQLogHandler(getUtilLevel(logLevel), host, port);
            System.out.println("Logging to " + host + ":" + port + ", level " +
                               logLevel);
        }
    }

    void configure()
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

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

    boolean matches(String host, int port, Level logLevel)
    {
        if (appender instanceof BasicAppender) {
            return (host == null || host.length() == 0 || port <= 0);
        }

        DAQLogAppender daqLogAppender = (DAQLogAppender) appender;
        if (!daqLogAppender.isConnected(host, port)) {
            return false;
        }

        if (!daqLogAppender.getLevel().equals(logLevel)) {
            return false;
        }

        return true;
    }

    private void setBasic(Level logLevel)
    {
        appender = new BasicAppender(logLevel);
        handler = new StreamHandler(System.out, new SimpleFormatter());
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
    private static final int PING_FREQUENCY = 1000;

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

    /** URL of configuration server */
    private URL configURL;

    /** if <tt>true</tt>, show spinners */
    private boolean showSpinner;

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
     * @param configURL configuration server URL
     *
     * @throws DAQCompException if there is a problem
     */
    public DAQCompServer(DAQComponent comp, URL configURL)
        throws DAQCompException
    {
        this.comp = comp;
        this.configURL = configURL;

        comp.start();

        try {
            runEverything(comp, configURL);
        } finally {
            comp.destroy();
        }
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

        comp.start();

        try {
            runEverything(comp, configURL);
        } finally {
            comp.destroy();
        }
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
        } else if (!rtnObj.getClass().isArray()) {
            throw new XmlRpcException("Unexpected return object [ " + rtnObj +
                                      "] (type " +
                                      rtnObj.getClass().getName() + ")");
        }

        Object[] rtnArray = (Object[]) rtnObj;
        if (rtnArray.length != 4) {
            throw new XmlRpcException("Expected 4-element array, got " +
                                      rtnArray.length + " elements");
        }

        final int compId = ((Integer) rtnArray[0]).intValue();
        final String logIP = (String) rtnArray[1];
        final int logPort = ((Integer) rtnArray[2]).intValue();
        final int tmpServerId =  ((Integer) rtnArray[3]).intValue();

        if (serverIdSet) {
            LOG.error("Overwriting server ID");
        }

        serverId = tmpServerId;
        serverIdSet = true;

        comp.setId(compId);

        try {
            setDefaultLoggingConfiguration(logIP, logPort, comp.getLogLevel());
        } catch (UnknownHostException uhe) {
            throw new XmlRpcException("Unknown log host '" + logIP + "'", uhe);
        } catch (SocketException se) {
            throw new XmlRpcException("Couldn't connect to log '" + logIP +
                                      ":" + logPort + "'", se);
        }

        resetLoggingConfiguration();
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
        if (objList == null || objList.length == 0) {
            throw new DAQCompException("Empty/null list of connections");
        }

        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        Connection[] connList = new Connection[objList.length];
        for (int i = 0; i < objList.length; i++) {
            if (!(objList[i] instanceof HashMap)) {
                throw new DAQCompException("Unexpected connect element #" +
                                           i + ": " +
                                           objList[i].getClass().getName());
            }

            HashMap map = (HashMap) objList[i];

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
     * XML-RPC method to list connector states.
     *
     * @return <tt>list of connector states</tt>
     *
     * @throws DAQCompException if component does not exist
     */
    public String[][] listConnectorStates()
        throws DAQCompException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        ArrayList list = new ArrayList();

        for (Iterator iter = comp.listConnectors(); iter.hasNext(); ) {
            DAQConnector conn = (DAQConnector) iter.next();

            list.add(new String[] {
                    conn.getType(), conn.getState().toLowerCase(),
                });
        }

        String[][] array = new String[list.size()][2];

        int i = 0;
        for (Iterator iter = list.iterator(); iter.hasNext(); ) {
            array[i++] = (String[] )iter.next();
        }

        return array;
    }

    /**
     * XML-RPC method to tell a component where to log
     *
     * @param address log host address
     * @param port log host port
     * @param levelStr log level string
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if component does not exist
     * @throws IOException if logging configuration could not be set
     */
    public String logTo(String address, int port)
        throws DAQCompException, IOException
    {
        if (comp == null) {
            throw new DAQCompException("Component not found");
        }

        setLoggingConfiguration(new LoggingConfiguration(address, port,
                                                         comp.getLogLevel()));
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
            if (!sawServer) {
                serverIdSet = false;
            }
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
        boolean usage = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].length() > 1 && args[i].charAt(0) == '-') {
                switch(args[i].charAt(1)) {
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
                    String addrStr = args[i];
                    int ic = addrStr.indexOf(':');
                    int il = addrStr.indexOf(',');
                    if (ic < 0 || il < 0) {
                        System.err.println("Bad log argument '" +
                                           addrStr + "'");
                        usage = true;
                        break;
                    }

                    String logHost  = addrStr.substring(0, ic);
                    String portStr  = addrStr.substring(ic + 1, il);
                    String levelStr = addrStr.substring(il+1);

                    Level logLevel = getLogLevel(levelStr);
                    if(logLevel == null) {
                        System.err.println("Bad log level: '"+levelStr+"'");
                        usage = true;
                        break;
                    } 
                    comp.setLogLevel(logLevel);

                    int logPort;
                    try {
                        logPort =
                            Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad log port '" +
                                           portStr + "' in '" +
                                           addrStr + "'");
                        usage = true;
                        break;
                    }

                    try {
                        setDefaultLoggingConfiguration(logHost, logPort,
                                                       logLevel);
                    } catch (UnknownHostException uhe) {
                        System.err.println("Bad log host '" +
                                           logHost + "' in '" +
                                           addrStr + "'");
                        usage = true;
                    } catch (SocketException se) {
                        System.err.println("Couldn't set logging" +
                                           " to '" + addrStr +
                                           "'");
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
                    } else {
                        System.err.println("Unknown option '" + args[i] + "'");
                        usage = true;
                    }
                    break;
                }
            } else if (args[i].length() == 0) {
                // ignore empty arguments
            } else {
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

        if (usage) {
            System.err.println("java " + comp.getClass().getName() + " " +
                               " [-S(howSpinner)]" +
                               " [-c configServerURL]" +
                               " [-d dispatchDestPath]" +
                               " [-g globalConfigPath]" +
                               " [-l logAddress:logPort,logLevel]" +
                               " [-s maxDispatchFileSize]" +
                               comp.getOptionUsage() +
                               "");
            System.exit(1);
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
            defaultLogConfig = new LoggingConfiguration(Level.INFO);
        }

        setLoggingConfiguration(defaultLogConfig);
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
        LOG.info("XML-RPC on port " + webServer.getPort());

        try {
            XmlRpcClient client = buildClient(cfgServerURL);

            while (true) {
                try {
                    sendAnnouncement(client, comp, webServer.getPort());

                    if (!monitorServer(client, comp)) {
                        break;
                    }
                } catch (Exception ex) {
                    LOG.error("Couldn't announce " + comp, ex);
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
    private void sendAnnouncement(XmlRpcClient client, DAQComponent comp,
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

        while (true) {
            try {
                announce(client, comp, addr.getHostAddress(), port);
                break;
            } catch (XmlRpcException xre) {
                if (!(xre.linkedException instanceof ConnectException)) {
                    final String errMsg = "Couldn't announce component";
                    throw new DAQCompException(errMsg, xre);
                } else {
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
    }

    /**
     * Set the default logging configuration.
     *
     * @param logIP log host address
     * @param logPort log host port
     * @param logLevel log level
     */
    private static void setDefaultLoggingConfiguration(String logIP,
                                                       int logPort,
                                                       Level logLevel)
        throws SocketException, UnknownHostException
    {
        if (defaultLogConfig == null ||
            !defaultLogConfig.matches(logIP, logPort, logLevel))
        {
            defaultLogConfig = new LoggingConfiguration(logIP, logPort,
                                                        logLevel);
        }
    }

    /**
     * Set logging to the specified configuration.
     */
    private static void setLoggingConfiguration(LoggingConfiguration logConfig)
    {
        LOG.info("Resetting logging");

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

        logConfig.configure();

        LOG.info("Logging has been reset");
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
     * XML-RPC method requesting the specified component to start a subrun.
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
