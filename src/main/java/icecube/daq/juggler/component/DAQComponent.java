package icecube.daq.juggler.component;

import icecube.daq.io.PayloadInputEngine;
import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;

import icecube.daq.payload.IByteBufferCache;

import icecube.daq.splicer.Splicer;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Generic DAQ component methods.
 */
public abstract class DAQComponent
{
    /** Component is in an unknown state. */
    public static final int STATE_UNKNOWN = 0;
    /** Component is idle. */
    public static final int STATE_IDLE = 1;
    /** Component is connecting to other components. */
    public static final int STATE_CONNECTING = 2;
    /** Component is connected to other components but not configured. */
    public static final int STATE_CONNECTED = 3;
    /** Component is configuring. */
    public static final int STATE_CONFIGURING = 4;
    /** Component is ready to run. */
    public static final int STATE_READY = 5;
    /** Component is starting to run. */
    public static final int STATE_STARTING = 6;
    /** Component is running. */
    public static final int STATE_RUNNING = 7;
    /** Component is stopping. */
    public static final int STATE_STOPPING = 8;
    /** Component is disconnecting from other components. */
    public static final int STATE_DISCONNECTING = 9;

    /** state names */
    private static final String[] STATE_NAMES = {
        "unknown",
        "idle",
        "connecting",
        "connected",
        "configuring",
        "ready",
        "starting",
        "running",
        "stopping",
        "disconnecting",
    };

    private static final Log LOG = LogFactory.getLog(DAQComponent.class);

    /** component type */
    private String name;
    /** component instance number */
    private int num;

    /** current component state */
    private int state;

    /** server-assigned ID */
    private int id = Integer.MIN_VALUE;

    /** list of I/O engines */
    private ArrayList engines = new ArrayList();
    /** has list of engines been sorted? */
    private boolean enginesSorted;

    /** hash table of byte buffer caches */
    private HashMap caches = new HashMap();

    /** hash table of MBeans */
    private HashMap mbeans = new HashMap();

    /** Port and address to log to */
    private String logAddress;
    private int logPort = 9001;

    private DAQOutputHack outputHack;

    /**
     * Create a component.
     *
     * @param name component type
     * @param num component instance number
     */
    public DAQComponent(String name, int num)
    {
        this.name = name;
        this.num = num;

        state = STATE_IDLE;
    }

    /**
     * Add a generic byte buffer cache.
     *
     * @param cache buffer cache
     */
    public final void addCache(IByteBufferCache cache)
    {
        addCache(DAQConnector.TYPE_GENERIC_CACHE, cache);
    }

    /**
     * Add a byte buffer cache for the specified data type.
     *
     * @param type buffer cache type
     * @param cache buffer cache
     */
    public final void addCache(String type, IByteBufferCache cache)
    {
        if (caches.containsKey(type)) {
            LOG.error("Overwriting buffer cache for type \"" + type + "\"");
        }

        caches.put(type, cache);
    }

    /**
     * Add an input engine with the specified type.
     *
     * @param type engine type
     * @param engine input engine
     */
    public final void addEngine(String type, PayloadInputEngine engine)
    {
        engines.add(new DAQInputConnector(type, engine));
        enginesSorted = false;
    }

    /**
     * Add an output engine with the specified type.
     *
     * @param type engine type
     * @param engine output engine
     */
    public final void addEngine(String type, PayloadOutputEngine engine)
    {
        engines.add(new DAQOutputConnector(type, engine));
        enginesSorted = false;
    }

    public final void addMBean(String name, Object mbean)
    {
        if (mbeans.containsKey(name)) {
            LOG.error("Overwriting MBean \"" + name + "\"");
        }

        mbeans.put(name, mbean);
    }

    /**
     * Add a splicer.
     *
     * @param splicer splicer
     */
    public final void addSplicer(Splicer splicer)
    {
        addSplicer(splicer, true);
    }

    /**
     * Add a splicer.
     *
     * @param splicer splicer
     * @param needStart <tt>true</tt> if splicer should be started
     */
    public final void addSplicer(Splicer splicer, boolean needStart)
    {
        engines.add(new DAQSplicer(splicer, needStart));
        enginesSorted = false;
    }

    /**
     * TODO: This method is a HACK; it should be replaced by an internal thread!
     *
     * @throws DAQCompException if there is a problem stopping the component
     */
    public final void checkRunState()
        throws DAQCompException
    {
        if (state == STATE_RUNNING) {
            if (!isRunning()) {
                state = STATE_STOPPING;
            }
        } else if (state == STATE_STOPPING) {
            if (isStopped()) {
                stopped();
                state = STATE_READY;
            }
        }
    }

    /**
     * Configure a component.
     *
     * @throws DAQCompException if the component is not in the correct state
     */
    public final void configure()
        throws DAQCompException
    {
        if (state != STATE_CONNECTED && state != STATE_READY) {
            throw new DAQCompException("Cannot configure component " + name +
                                       "#" + num + " from state " +
                                       getStateString());
        }

        state = STATE_READY;
    }

    /**
     * Configure a component using the specified configuration name.
     *
     * @param configName configuration name
     *
     * @throws DAQCompException if there is a problem configuring
     */
    public final void configure(String configName)
        throws DAQCompException
    {
        if (state != STATE_CONNECTED && state != STATE_READY) {
            throw new DAQCompException("Cannot configure component " + name +
                                       "#" + num + " from state " +
                                       getStateString());
        }

        state = STATE_CONFIGURING;

        configuring(configName);

        state = STATE_READY;
    }

    /**
     * Override this method to configure a component using the specified
     * configuration name.
     *
     * @param configName configuration name
     *
     * @throws DAQCompException if there is a problem configuring
     */
    public void configuring(String configName)
        throws DAQCompException
    {
        // Override me!
    }

    /**
     * Connect a component which has no output channels.
     *
     * @throws DAQCompException if the component is not in the correct state
     * @throws IOException if the connection failed
     */
    public final void connect()
        throws DAQCompException, IOException
    {
        if (state != STATE_IDLE) {
            throw new DAQCompException("Cannot connect component " + name +
                                       "#" + num + " from state " +
                                       getStateString());
        }

        state = STATE_CONNECTING;

        for (Iterator iter = engines.iterator(); iter.hasNext();) {
            DAQConnector dc = (DAQConnector) iter.next();
            if (!dc.isInput() && !dc.isSplicer() &&
                !((DAQOutputConnector) dc).isConnected())
            {
                throw new DAQCompException("Component " + name + "#" + num +
                                           " has unconnected " +
                                           dc.getType() + " output");
            }
        }

        state = STATE_CONNECTED;
    }

    /**
     * Connect all specified channels.
     *
     * @param list list of connections
     *
     * @throws DAQCompException if there is a problem finding a connection
     * @throws IOException if connection cannot be made
     */
    public final void connect(Connection[] list)
        throws DAQCompException, IOException
    {
        if (state != STATE_IDLE) {
            throw new DAQCompException("Cannot connect component " + name +
                                       "#" + num + " from state " +
                                       getStateString());
        }

        state = STATE_CONNECTING;

        for (int i = 0; i < list.length; i++) {
            DAQOutputConnector conn = null;

            for (Iterator iter = engines.iterator(); iter.hasNext();) {
                DAQConnector dc = (DAQConnector) iter.next();
                if (!dc.isInput() && list[i].matches(dc.getType())) {
                    if (conn != null) {
                        final String errMsg = "Component " + name + "#" + num +
                            " has multiple " + list[i].getType() + " outputs";

                        throw new DAQCompException(errMsg);
                    }

                    conn = (DAQOutputConnector) dc;
                    if (conn.isConnected()) {
                        final String errMsg = "Component " + name + "#" + num +
                            " output " + list[i].getType() +
                            " is already connected";

                        throw new DAQCompException(errMsg);
                    }
                }
            }

            if (conn == null) {
                throw new DAQCompException("Component " + name + "#" + num +
                                           " does not contain " +
                                           list[i].getType() + " output");
            }

            PayloadTransmitChannel xmitChan =
                conn.connect(getByteBufferCache(conn.getType()), list[i]);
            if (outputHack != null) {
                outputHack.createdTransmitChannel(conn.getOutputEngine(),
                                                  xmitChan);
            }
        }

        state = STATE_CONNECTED;
    }

    /**
     * Destroy all connections and threads.
     *
     * @throws DAQCompException if there is a problem
     */
    public final void destroy()
        throws DAQCompException
    {
        DAQCompException compEx = null;

        Iterator iter = engines.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            try {
                conn.destroy();
            } catch (Exception ex) {
                compEx = new DAQCompException("Couldn't destroy " + name +
                                              "#" + num + ":" + conn.getType(),
                                              ex);
            }
        }

        if (compEx != null) {
            throw compEx;
        }
    }

    /**
     * Disconnect all connections and return to idle state.
     *
     * @throws DAQCompException if the component is in the wrong state
     * @throws IOException if there is a problem disconnecting
     */
    public final void disconnect()
        throws DAQCompException, IOException
    {
        if (state != STATE_CONNECTING && state != STATE_CONNECTED &&
            state != STATE_CONFIGURING && state != STATE_READY)
        {
            throw new DAQCompException("Cannot disconnect component " + name +
                                       "#" + num + " from state " +
                                       getStateString());
        }

        state = STATE_DISCONNECTING;

        IOException ioEx = null;
        for (Iterator iter = engines.iterator(); iter.hasNext();) {
            DAQConnector dc = (DAQConnector) iter.next();
            if (!dc.isInput() && !dc.isSplicer()) {
                try {
                    ((DAQOutputConnector) dc).disconnect();
                } catch (IOException ioe) {
                    ioEx = ioe;
                }
            }
        }

        if (ioEx != null) {
            throw ioEx;
        }

        disconnected();

        state = STATE_IDLE;
    }

    /**
     * Override this method to perform any actions after all output engines
     * have been disconnected.
     *
     * @throws DAQCompException if there is a problem
     */
    public void disconnected()
        throws DAQCompException
    {
    }

    /**
     * Emergency stop of component.
     *
     * @throws DAQCompException if there is a problem
     */
    public final void emergencyStop()
        throws DAQCompException
    {
        DAQCompException compEx = null;

        Iterator iter = engines.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            try {
                conn.forcedStopProcessing();
            } catch (Exception ex) {
                compEx = new DAQCompException("Couldn't force stop " + name +
                                              "#" + num + ":" + conn.getType(),
                                              ex);
            }
        }

        if (compEx != null) {
            throw compEx;
        }
    }

    /**
     * Get the byte buffer cache for the specified data type.
     *
     * @param type data type of buffer cache
     *
     * @return byte buffer cache
     *
     * @throws DAQCompException if the cache could not be found
     */
    public final IByteBufferCache getByteBufferCache(String type)
        throws DAQCompException
    {
        IByteBufferCache genericCache = null;

        Iterator iter = caches.keySet().iterator();
        while (iter.hasNext()) {
            String keyType = (String) iter.next();

            if (keyType.equals(type)) {
                return (IByteBufferCache) caches.get(keyType);
            } else if (keyType.equals(DAQConnector.TYPE_GENERIC_CACHE)) {
                genericCache = (IByteBufferCache) caches.get(keyType);
            }
        }

        if (genericCache == null) {
            throw new DAQCompException("Could not find byte buffer cache" +
                                       " for type \"" + type + "\"");
        }

        // if no match was found for specific type, return generic cache
        return genericCache;
    }

    /**
     * Get server-assigned ID.
     *
     * @return ID
     */
    public final int getId()
    {
        return id;
    }

    /**
     * Get an input engine.
     *
     * @param type input engine type
     *
     * @return <tt>null</tt> if no matching engine was found
     */
    public final PayloadInputEngine getInputEngine(String type)
    {
        for (Iterator iter = engines.iterator(); iter.hasNext();) {
            DAQConnector dc = (DAQConnector) iter.next();
            if (dc.isInput() && !dc.isSplicer()) {
                if (dc.getType().equals(type)) {
                    return ((DAQInputConnector) dc).getInputEngine();
                }
            }
        }

        return null;
    }

    /**
     * Get component name.
     *
     * @return standard component name
     */
    public final String getName()
    {
        return name;
    }

    /**
     * Get component number.
     *
     * @return standard component number
     */
    public final int getNumber()
    {
        return num;
    }

    /**
     * Get an output engine.
     *
     * @param type output engine type
     *
     * @return <tt>null</tt> if no matching engine was found
     */
    public final PayloadOutputEngine getOutputEngine(String type)
    {
        for (Iterator iter = engines.iterator(); iter.hasNext();) {
            DAQConnector dc = (DAQConnector) iter.next();
            if (!dc.isInput() && !dc.isSplicer()) {
                if (dc.getType().equals(type)) {
                    return ((DAQOutputConnector) dc).getOutputEngine();
                }
            }
        }

        return null;
    }

    /**
     * Get a splicer.
     *
     * @param type splicer type
     *
     * @return <tt>null</tt> if no matching splicer was found
     */
    public final Splicer getSplicer(String type)
    {
        for (Iterator iter = engines.iterator(); iter.hasNext();) {
            DAQConnector dc = (DAQConnector) iter.next();
            if (dc.isSplicer()) {
                if (dc.getType().equals(type)) {
                    return ((DAQSplicer) dc).getSplicer();
                }
            }
        }

        return null;
    }

    /**
     * Get current state.
     *
     * @return current state
     */
    public final int getState()
    {
        return state;
    }

    /**
     * Get string description of current state.
     *
     * @return current state string
     */
    public final String getStateString()
    {
        return STATE_NAMES[state];
    }

    /**
     * Are I/O engines still running?
     *
     * @return <tt>true</tt> if any engine is still running
     */
    public boolean isRunning()
    {
        Iterator iter = engines.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            if (conn.isRunning()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Are all I/O engines stopped?
     *
     * @return <tt>true</tt> if all engines have stopped
     */
    public boolean isStopped()
    {
        Iterator iter = engines.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            if (!conn.isStopped()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Return an iterator for this component's I/O connections.
     *
     * @return connection iterator
     */
    public final Iterator listConnectors()
    {
        return engines.iterator();
    }

    public void monitorHack()
    {
        Iterator keyIter = mbeans.keySet().iterator();
        while (keyIter.hasNext()) {
            String key = (String) keyIter.next();

            LOG.info("MBean \"" + key + "\" => " + mbeans.get(key));
        }
    }

    /**
     * Register output engine observer.
     *
     * @param outputHack output engine observer
     */
    public final void registerOutputHack(DAQOutputHack outputHack)
    {
        this.outputHack = outputHack;
    }

    /**
     * Reset the component back to the idle state, doing any necessary cleanup.
     *
     * @throws DAQCompException if the component cannot be reset
     * @throws IOException if there is a problem disconnecting anything
     */
    final void reset()
        throws DAQCompException, IOException
    {
        if (state == STATE_RUNNING || state == STATE_STOPPING) {
            stopping();

            emergencyStop();

            for (int numTries = 0; numTries < 3 && !isStopped(); numTries++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    // ignore interrupts
                }
            }

            if (isStopped()) {
                stopped();
                state = STATE_READY;
            }
        }

        resetting();

        if (state == STATE_CONNECTING || state == STATE_CONNECTED ||
            state == STATE_CONFIGURING || state == STATE_READY)
        {
            disconnect();
        }

        if (state == STATE_IDLE) {
            return;
        }

        throw new DAQCompException("Reset from " + getStateString() +
                                   " is not implemented");
    }

    /**
     * Override this method to reset internal state after a component has
     * moved to idle.
     *
     * @throws DAQCompException if there is a problem resetting
     */
    public void resetting()
        throws DAQCompException
    {
        // Override me!
    }

    /**
     * Callback method invoked when config server dies.
     *
     * @return <tt>true</tt> if the component has destroyed itself
     */
    public final boolean serverDied()
    {
        boolean needDestroy = false;
        try {
            reset();
        } catch (DAQCompException dce) {
            dce.printStackTrace();
            needDestroy = true;
        } catch (IOException io) {
            io.printStackTrace();
            needDestroy = true;
        }

        if (needDestroy) {
            try {
                destroy();
            } catch (DAQCompException dce) {
                dce.printStackTrace();
            }
        }

        return needDestroy;
    }

    /**
     * Override this method to receive the name of the directory holding the
     * XML configuration tree.
     *
     * @param dirName directory name
     */
    public void setGlobalConfigurationDir(String dirName)
    {
        // Override me!
    }

    /**
     * Set the component ID assigned by the server.
     *
     * @param id assigned ID
     */
    public final void setId(int id)
    {
        this.id = id;
    }

    /**
     * Override this method to set the run number inside this component.
     *
     * @param runNumber run number
     */
    public void setRunNumber(int runNumber)
    {
        // Override me!
    }

    /**
     * Start background threads.
     *
     * @throws DAQCompException if input server cannot be started
     */
    public final void start()
        throws DAQCompException
    {
        DAQCompException compEx = null;

        // sort engines so they are started in the correct order
        if (!enginesSorted) {
            Collections.sort(engines, new ConnCmp());
            enginesSorted = true;
        }

        Iterator iter = engines.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            try {
                conn.start();
            } catch (IllegalThreadStateException itse) {
                compEx = new DAQCompException("Couldn't start " + name +
                                              "#" + num + ":" +
                                              conn.getType() + "; engine has" +
                                              " probably been added" +
                                              " more than once", itse);
            } catch (Exception ex) {
                compEx = new DAQCompException("Couldn't start " + name +
                                              "#" + num + ":" + conn.getType(),
                                              ex);
            }

            if (conn.isInput()) {
                try {
                    conn.startServer(getByteBufferCache(conn.getType()));

                    LOG.info(name + "#" + num + ":" +
                             conn.getType() + " listening on port " +
                             conn.getPort());
                } catch (IOException ioe) {
                    compEx = new DAQCompException("Couldn't start " + name +
                                                  "#" + num + ":" +
                                                  conn.getType() + " server",
                                                  ioe);
                }
            }
        }

        if (compEx != null) {
            throw compEx;
        }
    }

    /**
     * Start engines.
     *
     * @throws DAQCompException if there is a problem
     */
    public final void startEngines()
        throws DAQCompException
    {
        DAQCompException compEx = null;

        Iterator iter = engines.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            try {
                conn.startProcessing();
            } catch (Exception ex) {
                compEx = new DAQCompException("Couldn't start " + name +
                                              "#" + num + ":" + conn.getType(),
                                              ex);
            }
        }

        if (compEx != null) {
            throw compEx;
        }
    }

    /**
     * Start a run.
     *
     * @param runNumber run number
     *
     * @throws DAQCompException if there is a problem
     */
    public final void startRun(int runNumber)
        throws DAQCompException
    {
        if (state != STATE_READY) {
            throw new DAQCompException("Cannot start component " + name +
                                       "#" + num + " from state " +
                                       getStateString());
        }

        setRunNumber(runNumber);

        starting();

        state = STATE_RUNNING;

        startEngines();

        started();
    }

    /**
     * Override this method for actions which happen after a run has started.
     *
     * @throws DAQCompException if there is a problem starting the component
     */
    public void started()
        throws DAQCompException
    {
        // Override me!
    }

    /**
     * Override this method for actions which happen just before a run is
     * started.
     *
     * @throws DAQCompException if there is a problem starting the component
     */
    public void starting()
        throws DAQCompException
    {
        // Override me!
    }

    /**
     * Stop a run.
     *
     * @throws DAQCompException if there is a problem
     */
    public final void stopRun()
        throws DAQCompException
    {
        if (state != STATE_READY) {
            if (state != STATE_RUNNING) {
                throw new DAQCompException("Cannot stop component " + name +
                                           "#" + num + " from state " +
                                           getStateString());
            }

            state = STATE_STOPPING;
        }

        stopping();

        int attempts = 0;
        while (state == STATE_STOPPING && attempts < 5) {
            if (isStopped()) {
                stopped();
                state = STATE_READY;
            } else {
                // TODO: This should use Artur's Listener callback
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    // ignore interrupts
                }

                attempts++;
            }
        }
    }

    /**
     * Override this method to perform any clean-up after all I/O engines
     * have stopped.
     *
     * @throws DAQCompException if there is a problem stopping the component
     */
    public void stopped()
        throws DAQCompException
    {
        // Override me!
    }

    /**
     * Override this method to perform any clean-up before I/O engines
     * are stopped.
     *
     * @throws DAQCompException if there is a problem stopping the component
     */
    public void stopping()
        throws DAQCompException
    {
        // Override me!
    }

    /**
     * DAQConnector comparator.
     */
    class ConnCmp
        implements Comparator
    {
        /**
         * Compare two DAQConnectors.
         *
         * @param o1 first connector
         * @param o2 second connector
         *
         * @return the usual comparator values
         */
        public int compare(Object o1, Object o2)
        {
            if (o1 == null) {
                if (o2 == null) {
                    return 0;
                }

                return 1;
            } else if (o2 == null) {
                return -1;
            }

            if (!(o1 instanceof DAQConnector && o1 instanceof DAQConnector)) {
                String oName1 = o1.getClass().getName();
                String oName2 = o2.getClass().getName();

                return oName1.compareTo(oName2);
            }

            DAQConnector dc1 = (DAQConnector) o1;
            DAQConnector dc2 = (DAQConnector) o2;

            // if 1st object is a splicer...
            if (dc1.isSplicer()) {
                if (!dc2.isSplicer()) {
                    // if 2nd object is not a splicer, it goes before 1st
                    return 1;
                }
            } else if (dc2.isSplicer()) {
                // if 2nd obj is a splicer, it goes after 1st
                return -1;
            } else if (dc1.isInput()) {
                // 1st object is an input engine...
                if (!dc2.isInput()) {
                    // if 2nd object is not an input engine, it goes before 1st
                    return 1;
                }
            } else if (dc2.isInput()) {
                // if 2nd obj is an input engine, it goes after 1st
                return -1;
            }

            // otherwise, compare types
            return dc1.getType().compareTo(dc2.getType());
        }

        /**
         * This object is only equal to itself.
         *
         * @return <tt>true</tt> if object is being compared to itself
         */
        public boolean equals(Object obj)
        {
            return (obj == this);
        }
    }
}
