package icecube.daq.juggler.component;

import icecube.daq.juggler.mock.MockAppender;

import java.io.IOException;

import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

    /** URL of configuration server */
    private URL configURL;

    /** List of components */
    private static ArrayList list = new ArrayList();

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
        processArgs(comp, args);

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
            if (conn.isSplicer()) {
                continue;
            }

            Object[] row = new Object[3];
            row[0] = conn.getType();
            row[1] = conn.isInput() ? Boolean.TRUE : Boolean.FALSE;
            row[2] = new Integer(conn.getPort());

            connList.add(row);
        }

        Object[] params = new Object[] {
            comp.getName(), new Integer(comp.getNumber()),
            host, new Integer(port), connList.toArray(),
        };

        Object rtnObj = client.execute("rpc_register_component", params);
        if (!(rtnObj instanceof Integer)) {
            throw new XmlRpcException("Unexpected return object [ " + rtnObj +
                                      "] (type " +
                                      (rtnObj == null ? "<null>" :
                                       rtnObj.getClass().getName()) + ")");
        }

        comp.setId(((Integer) rtnObj).intValue());
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
     * Create a URL from the specified string.
     *
     * @param urlStr string to convert to URL
     *
     * @return URL
     *
     * @throws DAQCompException if the URL is invalid
     */
    private static URL buildURL(String urlStr)
        throws DAQCompException
    {
        try {
            return new URL(urlStr);
        } catch (MalformedURLException mue) {
            throw new DAQCompException("Bad URL \"" + urlStr + "\"", mue);
        }
    }

    /**
     * XML-RPC method to 'configure' a component which needs no configuration.
     *
     * @param id component ID
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if no component matches the specified ID
     */
    public String configure(int id)
        throws DAQCompException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }

        comp.configure();

        return "OK";
    }

    /**
     * XML-RPC method to configure a component using
     * the specified configuration name.
     *
     * @param id component ID
     * @param configName configuration name
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if no component matches the specified ID
     */
    public String configure(int id, String configName)
        throws DAQCompException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }

        comp.configure(configName);

        return "OK";
    }

    /**
     * XML-RPC method to 'connect' a component with no output connections.
     *
     * @param id component ID
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if no component matches the specified ID
     * @throws IOException if there is a problem creating a connection
     */
    public String connect(int id)
        throws DAQCompException, IOException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }

        comp.connect();

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
        Spinner spinner = new Spinner("-\\|/");
        
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
        
            spinner.print();
        }
        
        return !compDestroyed;
    }

    /**        
     * XML-RPC method to tell a component where to log
     *
     * @param id component ID
     * @param address log host address
     * @param port log host port
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if no component matches the specified ID
     */
    public String logTo(int id, String address, int port)
        throws DAQCompException, IOException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }
        comp.logTo(address, port);
        return "OK";
    }

    /**
     * XML-RPC method to connect the component using the specified
     * connection descriptions.
     *
     * @param id component ID
     * @param objList list of connections
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if no component matches the specified ID
     * @throws IOException if there is a problem creating a connection
     */
    public String connect(int id, Object[] objList)
        throws DAQCompException, IOException
    {
        if (objList == null || objList.length == 0) {
            throw new DAQCompException("Empty/null list of connections");
        }

        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }

        Connection[] connList = new Connection[objList.length];
        for (int i = 0; i < objList.length; i++) {
            if (!(objList[i] instanceof HashMap)) {
                throw new DAQCompException("Unexpected connect element #" +
                                           i + ": " +
                                           objList[i].getClass().getName());
            }

            connList[i] = new Connection((HashMap) objList[i]);
        }

        comp.connect(connList);

        return "OK";
    }

    /**
     * Get the specified component.
     *
     * @param id component ID
     *
     * @returns <tt>null</tt> if component was not found
     */
    private static DAQComponent getComponent(int id)
    {
        Iterator iter = list.iterator();
        while (iter.hasNext()) {
            DAQComponent comp = (DAQComponent) iter.next();
            if (comp.getId() == id) {
                return comp;
            }
        }

        return null;
    }

    /**
     * XML-RPC method to return component state.
     *
     * @param id component ID
     *
     * @return component state
     *
     * @throws DAQCompException if no component matches the specified ID
     */
    public String getState(int id)
        throws DAQCompException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }

        return comp.getStateString();
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
        boolean success;
        try {
            client.execute("rpc_ping", NO_PARAMS);
            success = true;
        } catch (XmlRpcException xre) {
            if (!(xre.linkedException instanceof ConnectException)) {
                xre.printStackTrace();
            }

            success = false;
        }

        return success;
    }

    /**
     * Process command-line arguments.
     *
     * @return requested DAQ component
     */
    private void processArgs(DAQComponent comp, String[] args)
    {
        boolean usage = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].length() > 1 && args[i].charAt(0) == '-') {
                switch(args[i].charAt(1)) {
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
                case 'g':
                    i++;
                    String glDir = args[i];
                    comp.setGlobalConfigurationDir(glDir);
                    break;
                case 'l':
                    i++;
                    String addrStr = args[i];
                    int ic = addrStr.indexOf(':');
                    String logAddress = addrStr.substring(0, ic);
                    try {
                        int logPort = Integer.parseInt(addrStr.substring(ic+1));
                        comp.logTo(logAddress, logPort);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad port argument in: \"" +
                                           addrStr + "\"");
                        usage = true;
                    }
                                    
                    break;
                default:
                    System.err.println("Unknown option '" + args[i] + "'");
                    usage = true;
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
            final String urlStr = "http://localhost:8080";
            try {
                configURL = new URL(urlStr);
            } catch (MalformedURLException mue) {
                System.err.println("Bad configuration URL \"" + urlStr + "\"");
                usage = true;
            }
        }

        if (usage) {
            System.err.println("java " + comp.getClass().getName() + " " +
                               " [-c configServerURL]" +
                               " [-g globalConfigPath]" +
                               " [-l logAddress:logPort]" +
                               "");
            System.exit(1);
        }
    }

//    private void logToDefault() {
//            BasicConfigurator.resetConfiguration();
//
//            if(logAddress != null) {
//                    //System.out.println("Will log to port " + logPort + " on " + logAddress);
//                    try {
//                            BasicConfigurator.configure(new DAQLogAppender(Level.INFO,
//                                            logAddress, 
//                                            logPort));
//                    } catch(Exception e) {
//                            System.err.println(e);
//                            System.exit(-1);
//                    }
//                    LOG.info("Started catch-all logging at "+logAddress+":"+logPort);
//            } else {
//                    BasicConfigurator.configure(new MockAppender(Level.INFO));
//            }
//    }

    /**
     * XML-RPC method to reset the specified component back to the idle state.
     *
     * @param id component ID
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if there is a problem
     * @throws IOException if there is a problem cleaning up I/O
     */
    public String reset(int id)
        throws DAQCompException, IOException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }

        comp.reset();

        return "OK";
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
                sendAnnouncement(client, comp, webServer.getPort());

                list.add(comp);

                if (!monitorServer(client, comp)) {
                    break;
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
        InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException uhe) {
            throw new DAQCompException("Couldn't get local host name", uhe);
        }

        Spinner spinner = new Spinner("?*");
        while (true) {
            try {
                announce(client, comp, addr.getHostAddress(), port);
                break;
            } catch (XmlRpcException xre) {
                if (!(xre.linkedException instanceof ConnectException)) {
                    final String errMsg = "Couldn't announce component";
                    throw new DAQCompException(errMsg, xre);
                } else {
                    spinner.print();

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
     * XML-RPC method telling the specified component to start a run.
     *
     * @param id component ID
     * @param runNumber run number
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if no component matches the specified ID
     * @throws IOException if there was a problem starting the component
     */
    public String startRun(int id, int runNumber)
        throws DAQCompException, IOException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
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
     * XML-RPC method telling the specified component to stop current run.
     *
     * @param id component ID
     *
     * @return <tt>"OK"</tt>
     *
     * @throws DAQCompException if no component matches the specified ID
     * @throws IOException if there was a problem starting the component
     */
    public String stopRun(int id)
        throws DAQCompException, IOException
    {
        DAQComponent comp = getComponent(id);
        if (comp == null) {
            throw new DAQCompException("Component#" + id + " not found");
        }

        comp.stopRun();
        return "OK";
    }
}
