package icecube.daq.juggler.component;

import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.MultiOutputEngine;
import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.io.SimpleReader;
import icecube.daq.io.SingleOutputEngine;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.io.SpliceableSimpleReader;
import icecube.daq.juggler.mbean.LocalMonitor;
import icecube.daq.juggler.mbean.MBeanAgent;
import icecube.daq.juggler.mbean.MBeanAgentException;
import icecube.daq.juggler.mbean.MBeanWrapper;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.splicer.Splicer;
import icecube.daq.util.FlasherboardConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;

/**
 * Generic DAQ component methods.
 *
 * The correct order for running a component is:<br>
 * <br>
 * <ol>
 * <li>connect()
 * <li>configure()
 * <li>startRun()
 * <li>stopRun()
 * </ol>
 *
 * @version $Id: DAQComponent.java 3678 2008-12-02 15:11:08Z dglo $
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
    /** Component has been destroyed. */
    public static final int STATE_DESTROYED = 10;
    /** Component is being forced to stop. */
    public static final int STATE_FORCING_STOP = 11;
    /** Component is being destroyed. */
    public static final int STATE_DESTROYING = 12;
    /** Component is being destroyed. */
    public static final int STATE_RESETTING = 13;

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
        "destroyed",
        "forcingStop",
        "destroying",
        "resetting",
    };

    private static final Log LOG = LogFactory.getLog(DAQComponent.class);

    /** Methods names for PayloadReader MBean */
    private static final String[] inputReaderMethods = new String[] {
        "BytesReceived",
        "RecordsReceived",
        "TotalRecordsReceived",
    };

    /** Methods names for SpliceablePayloadInputReader MBean */
    private static final String[] spliceableInputReaderMethods = new String[] {
        "BytesReceived",
        "RecordsReceived",
        "StrandDepth",
        "TotalRecordsReceived",
        "TotalStrandDepth",
    };

    /** Methods names for PayloadOutputEngine MBean */
    private static final String[] outputEngineMethods = new String[] {
        "BytesSent",
        "Depth",
        "RecordsSent",
    };

    /** Methods names for SingleOutputEngine MBean */
    private static final String[] singleEngineMethods = new String[] {
        "BytesSent",
        "Depth",
        "RecordsSent",
    };

    /** Methods names for MultiOutputEngine MBean */
    private static final String[] multiEngineMethods = new String[] {
        "BytesSent",
        "Depth",
        "RecordsSent",
    };

    /** Methods names for SimpleOutputEngine MBean */
    private static final String[] simpleEngineMethods = new String[] {
        "Depth",
        "RecordsSent",
    };

    /** component type */
    private String name;
    /** component instance number */
    private int num;

    /** server-assigned ID */
    private int id = Integer.MIN_VALUE;

    /** server-assigned log level. */
    private Level logLevel = Level.INFO;

    /** list of connectors */
    private ArrayList connectors = new ArrayList();
    /** has list of connectors been sorted? */
    private boolean connSorted;

    /** hash table of byte buffer caches */
    private HashMap caches = new HashMap();

    /** MBean manager */
    private MBeanAgent mbeanAgent;

    /** Local monitoring, is enabled */
    private LocalMonitor moniLocal;

    private StateTask stateTask;

    class StateTask
        implements Runnable
    {
        /** current component state */
        private int state;

        private boolean running;
        private boolean caughtError;
        private int prevState;
        private boolean stateChanged;
        private boolean calledStopping;

        private Connection[] connectList;
        private String configName;
        private int startNumber;

        StateTask()
        {
            state = STATE_UNKNOWN;
        }

        private void changeState(int newState)
        {
            caughtError = false;
            prevState = state;
            state = newState;
            stateChanged = true;
            notify();
        }

        /**
         * Configure a component using the specified configuration name.
         *
         * @param cfgName configuration name
         *
         * @throws DAQCompException if the component is not in the correct state
         */
        void configure(String cfgName)
            throws DAQCompException
        {
            if (state != STATE_CONNECTED && state != STATE_READY) {
                throw new DAQCompException("Cannot configure component " +
                                           getName() + " from state " +
                                           getStateString());
            }

            synchronized (this) {
                configName = cfgName;
                changeState(STATE_CONFIGURING);
            }
        }

        /**
         * Connect a component which has no output channels.
         *
         * @throws DAQCompException if the component is not in the correct state
         */
        void connect(Connection[] list)
            throws DAQCompException
        {
            if (state != STATE_IDLE) {
                throw new DAQCompException("Cannot connect component " +
                                           getName() + " from state " +
                                           getStateString());
            }

            synchronized (this) {
                connectList = list;
                changeState(STATE_CONNECTING);
            }
        }

        /**
         * Destroy all connections and threads.
         *
         * @throws DAQCompException if there is a problem
         */
        void destroy()
            throws DAQCompException
        {
            if (state == STATE_DESTROYED) {
                return;
            }

            synchronized (this) {
                changeState(STATE_DESTROYING);
            }
        }

        /**
         * Disconnect all connections and return to idle state.
         *
         * @throws DAQCompException if the component is in the wrong state
         */
        void disconnect()
            throws DAQCompException
        {
            if (state == STATE_IDLE) {
                // allow idle component to be 'disconnected'
                return;
            }

            if (state != STATE_CONNECTING && state != STATE_CONNECTED &&
                state != STATE_CONFIGURING && state != STATE_READY &&
                state != STATE_DISCONNECTING)
            {
                throw new DAQCompException("Cannot disconnect component " +
                                           getName() + " from state " +
                                           getStateString());
            }

            synchronized (this) {
                changeState(STATE_DISCONNECTING);
            }
        }

        private synchronized void doConfigure()
            throws DAQCompException
        {
            if (configName != null) {
                configuring(configName);
            }

            state = STATE_READY;
        }

        private synchronized void doConnect()
            throws DAQCompException
        {
            for (Iterator iter = connectors.iterator(); iter.hasNext();) {
                DAQConnector dc = (DAQConnector) iter.next();
                if (dc.isOutput() &&
                    !((DAQOutputConnector) dc).isConnected())
                {
                    state = STATE_IDLE;
                    throw new DAQCompException("Component " + getName() +
                                               " has unconnected " +
                                               dc.getType() + " output");
                }
            }

            state = STATE_CONNECTED;
        }

        private synchronized void doConnect(Connection[] list)
            throws DAQCompException, IOException
        {
            DAQCompException compEx = null;
            for (int i = 0; compEx == null && i < list.length; i++) {
                DAQOutputConnector conn = null;

                for (Iterator iter = connectors.iterator(); iter.hasNext();) {
                    DAQConnector dc = (DAQConnector) iter.next();
                    if (dc.isOutput() && list[i].matches(dc.getType())) {
                        if (conn != null) {
                            final String errMsg = "Component " + getName() +
                                " has multiple " + list[i].getType() +
                                " outputs";

                            compEx = new DAQCompException(errMsg);
                            break;
                        }

                        conn = (DAQOutputConnector) dc;
                        if (conn.isConnected() &&
                            !conn.allowMultipleConnections())
                        {
                            final String errMsg = "Component " + getName() +
                                " output " + list[i].getType() +
                                " is already connected";

                            compEx = new DAQCompException(errMsg);
                            break;
                        }
                    }
                }

                if (conn == null) {
                    compEx = new DAQCompException("Component " + getName() +
                                                  " does not contain " +
                                                  list[i].getType() +
                                                  " output");
                    break;
                }

                try {
                    conn.connect(getByteBufferCache(conn.getType()), list[i]);
                } catch (IOException ioe) {
                    compEx = new DAQCompException("Cannot connect " + conn +
                                                  " to connection #" + i +
                                                  ": " + list[i], ioe);
                    break;
                }
            }

            if (compEx != null) {
                try {
                    doDisconnect();
                } catch (Throwable thr) {
                    LOG.warn("Couldn't disconnect after failed connect", thr);
                }

                state = STATE_IDLE;

                throw compEx;
            }

            state = STATE_CONNECTED;
        }

        private void doDestroy()
            throws DAQCompException
        {
            DAQCompException compEx = null;

            Iterator iter = connectors.iterator();
            while (iter.hasNext()) {
                DAQConnector conn = (DAQConnector) iter.next();

                try {
                    conn.destroy();
                } catch (Exception ex) {
                    compEx = new DAQCompException("Couldn't destroy " +
                                                  getName() + ":" +
                                                  conn.getType(), ex);
                }
            }

            if (mbeanAgent != null) {
                try {
                    mbeanAgent.stop();
                } catch (MBeanAgentException mae) {
                    compEx = new DAQCompException("Couldn't stop MBean agent",
                                                  mae);
                }
            }

            if (stateTask != null) {
                stateTask.stop();
                stateTask = null;
            }

            state = STATE_DESTROYED;

            if (compEx != null) {
                throw compEx;
            }
        }

        private void doDisconnect()
            throws DAQCompException, IOException
        {
            IOException ioEx = null;
            for (Iterator iter = connectors.iterator(); iter.hasNext();) {
                DAQConnector dc = (DAQConnector) iter.next();
                if (dc.isOutput()) {
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
        }

        /**
         * Emergency stop of component.
         *
         * @return <tt>true</tt> if all connectors have stopped
         *
         * @throws DAQCompException if there is a problem
         */
        private boolean doForcedStop()
            throws DAQCompException
        {

            DAQCompException compEx = null;

            Iterator iter = connectors.iterator();
            while (iter.hasNext()) {
                DAQConnector conn = (DAQConnector) iter.next();

                // skip stopped connectors
                if (conn.isStopped()) {
                    continue;
                }

                try {
                    conn.forcedStopProcessing();
                } catch (Exception ex) {
                    compEx = new DAQCompException("Couldn't force stop " +
                                                  getName() + ":" +
                                                  conn.getType(), ex);
                }
            }

            if (compEx != null) {
                throw compEx;
            }

            return isStopped();
        }

        private void doReset()
            throws DAQCompException, IOException
        {
            if (prevState == STATE_STARTING || prevState == STATE_RUNNING ||
                prevState == STATE_STOPPING || prevState == STATE_FORCING_STOP)
            {
                if (!calledStopping) {
                    stopping();
                }

                if (doForcedStop()) {
                    stopped();
                    prevState = STATE_READY;
                }
            }

            resetting();

            if (prevState == STATE_CONNECTING || prevState == STATE_CONNECTED ||
                prevState == STATE_CONFIGURING || prevState == STATE_READY ||
                prevState == STATE_DISCONNECTING)
            {
                doDisconnect();

                state = STATE_IDLE;
            } else {
                state = prevState;
            }

            flushCaches();

            if (state != STATE_IDLE) {
                throw new DAQCompException("Reset from " + getStateString() +
                                           " is not implemented");
            }
        }

        private void doStartRun()
            throws DAQCompException
        {
            // haven't yet called stopping() for this run
            calledStopping = false;

            setRunNumber(startNumber);

            starting();

            startEngines();

            started();

            state = STATE_RUNNING;
        }

        private void doStopRun()
            throws DAQCompException
        {
            if (!calledStopping) {
                stopping();
                calledStopping = true;
            }

            if (state == STATE_STOPPING && isStopped()) {
                stopped();
                state = STATE_READY;
            }
        }

        /**
         * Flush buffer caches
         */
        private void flushCaches()
        {
            Iterator iter = caches.values().iterator();
            while (iter.hasNext()) {
                ((IByteBufferCache) iter.next()).flush();
            }
        }

        /**
         * Emergency stop of component.
         *
         * @throws DAQCompException if there is a problem
         */
        void forcedStop()
            throws DAQCompException
        {
            if (state == STATE_READY) {
                return;
            }

            if (state != STATE_RUNNING && state != STATE_STOPPING &&
                state != STATE_FORCING_STOP)
            {
                throw new DAQCompException("Cannot force-stop component " +
                                           getName() + " from state " +
                                           getStateString());
            }

            synchronized (this) {
                changeState(STATE_FORCING_STOP);
            }
        }

        /**
         * Get component name.
         *
         * @return name
         */
        public String getName()
        {
            if (num == 0 && !name.endsWith("Hub")) {
                return name;
            }

            return name + "#" + num;
        }

        /**
         * Get current state.
         *
         * @return current state
         */
        int getState()
        {
            return state;
        }

        /**
         * Get string description of current state.
         *
         * @return current state string
         */
        String getStateString()
        {
            return STATE_NAMES[state];
        }

        public boolean isError()
        {
            return caughtError;
        }

        public boolean isRunning()
        {
            return running;
        }

        /**
         * Reset the component back to the idle state, doing any necessary
         * cleanup.
         *
         * @throws DAQCompException if the component cannot be reset
         * @throws IOException if there is a problem disconnecting anything
         */
        final void reset()
            throws DAQCompException, IOException
        {
            synchronized (this) {
                changeState(STATE_RESETTING);
            }
        }

        public void run()
        {
            state = STATE_IDLE;

            running = true;
            while (running) {
                synchronized (this) {
                    if (stateChanged) {
                        stateChanged = false;
                    } else {
                        try {
                            if (state == STATE_STOPPING) {
                                wait(100);
                            } else {
                                wait();
                            }
                        } catch (InterruptedException ie) {
                            continue;
                        }
                    }
                }

                boolean success;
                switch (state) {
                case STATE_CONFIGURING:
                    try {
                        doConfigure();
                        success = true;
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Configure failed", dce);
                        success = false;
                    }

                    if (!success) {
                        // revert to the previous state if configure failed
                        state = prevState;
                    }

                    break;
                case STATE_CONNECTED:
                    // nothing to be done
                    break;
                case STATE_CONNECTING:
                    try {
                        if (connectList == null) {
                            doConnect();
                        } else {
                            doConnect(connectList);
                        }
                        success = true;
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Connect failed", dce);
                        success = false;
                    } catch (IOException ioe) {
                        caughtError = true;
                        LOG.error("Connect failed", ioe);
                        success = false;
                    }

                    if (!success) {
                        try {
                            doDisconnect();
                            state = STATE_IDLE;
                        } catch (DAQCompException dce) {
                            LOG.error("Couldn't disconnect after" +
                                      " failed connect", dce);
                        } catch (IOException ioe) {
                            LOG.error("Couldn't disconnect after" +
                                      " failed connect", ioe);
                        }
                    }
                    break;
                case STATE_DESTROYING:
                    try {
                        doDestroy();
                        success = true;
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Destroy failed", dce);
                        success = false;
                    }

                    if (!success) {
                        // pretend we were destroyed
                        state = STATE_DESTROYED;
                    }

                    break;
                case STATE_DESTROYED:
                    running = false;
                    break;
                case STATE_DISCONNECTING:
                    try {
                        doDisconnect();
                        state = STATE_IDLE;
                        success = true;
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Disconnect failed", dce);
                        success = false;
                    } catch (IOException ioe) {
                        caughtError = true;
                        LOG.error("Disconnect failed", ioe);
                        success = false;
                    }

                    if (!success) {
                        // revert to the previous state if disconnect failed
                        state = prevState;
                    }

                    break;
                case STATE_FORCING_STOP:
                    try {
                        success = doForcedStop();
                        if (success) {
                            stopped();
                            state = STATE_READY;
                        }
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Forced stop failed", dce);
                        success = false;
                    }

                    if (!success) {
                        // revert to the previous state if forced stop failed
                        state = prevState;
                    }

                    break;
                case STATE_IDLE:
                    // nothing to be done
                    break;
                case STATE_READY:
                    // nothing to be done
                    break;
                case STATE_RESETTING:
                    try {
                        doReset();
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Reset failed", dce);
                    } catch (IOException ioe) {
                        caughtError = true;
                        LOG.error("Reset failed", ioe);
                    }

                    break;
                case STATE_RUNNING:
                    // nothing to be done
                    break;
                case STATE_STARTING:
                    try {
                        doStartRun();
                        success = true;
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Start run failed", dce);
                        success = false;
                    }

                    if (!success) {
                        // revert to the previous state if startRun failed
                        state = prevState;
                    }

                    break;
                case STATE_STOPPING:
                    try {
                        doStopRun();
                        success = true;
                    } catch (DAQCompException dce) {
                        caughtError = true;
                        LOG.error("Stop run failed", dce);
                        success = false;
                    }

                    if (!success) {
                        // revert to the previous state if stopRun failed
                        state = prevState;
                    }

                    break;
                default:
                    LOG.error("StateTask not handling " + getStateString());
                    break;
                }
            }
        }

        void startRun(int runNumber)
            throws DAQCompException
        {
            if (state != STATE_READY) {
                throw new DAQCompException("Cannot start component " +
                                           getName() + " from state " +
                                           getStateString());
            }

            synchronized (this) {
                startNumber = runNumber;
                changeState(STATE_STARTING);
            }
        }

        void stop()
        {
            running = false;
            synchronized (this) {
                this.notify();
            }
        }

        /**
         * Stop a run.
         *
         * @throws DAQCompException if there is a problem
         */
        void stopRun()
            throws DAQCompException
        {
            if (state != STATE_READY) {
                if (state != STATE_RUNNING) {
                    throw new DAQCompException("Cannot stop component " +
                                               getName() + " from state " +
                                               getStateString());
                }
            }

            synchronized (this) {
                changeState(STATE_STOPPING);
            }
        }

        public String toString()
        {
            return "StateTask[" + STATE_NAMES[state] +
                "(prev=" + STATE_NAMES[prevState] + ")," +
                (running ? "" : "!") + "running," +
                (caughtError ? "" : "!") + "caughtError," +
                (stateChanged ? "" : "!") + "stateChanged," +
                (calledStopping ? "" : "!") + "calledStopping," +
                "config=" + configName + "," +
                "startNum=" + startNumber +
                "]";
        }
    }

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

        stateTask = new StateTask();

        Thread thread = new Thread(stateTask);
        thread.setName("StateTask");
        thread.start();

        while (!stateTask.isRunning()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
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
     * Add a buffer cache for the specified data type.
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
     * Add a connector.
     *
     * @param conn connector
     */
    public final void addConnector(DAQConnector conn)
    {
        connectors.add(conn);
        connSorted = false;
    }

    /**
     * Add an input engine with the specified type.
     *
     * @param type engine type
     * @param engine input engine
     *
     * @throws DAQCompException if 'enableMonitoring' is <tt>true</tt> but
     *                          'engine' is not a PayloadReader
     */
    public final void addEngine(String type, DAQComponentInputProcessor engine)
    {
        addConnector(new DAQInputConnector(type, engine));
    }

    /**
     * Add an output engine with the specified type.
     *
     * @param type engine type
     * @param engine output engine
     */
    public final void addEngine(String type, DAQComponentOutputProcess engine)
    {
        addEngine(type, engine, false);
    }

    /**
     * Add an output engine with the specified type.
     *
     * @param type engine type
     * @param engine output engine
     * @param allowMultipleConnections <tt>true</tt> if this output connector
     *                                 can connect to multiple input connectors
     */
    public final void addEngine(String type, DAQComponentOutputProcess engine,
                                boolean allowMultipleConnections)
    {
        addConnector(new DAQOutputConnector(type, engine,
                                            allowMultipleConnections));
    }

    /**
     * Add an MBean.
     *
     * @param name short MBean name
     * @param mbean MBean object
     */
    public final void addMBean(String name, Object mbean)
    {
        if (mbeanAgent == null) {
            mbeanAgent = new MBeanAgent();
        }

        try {
            mbeanAgent.addBean(name, mbean);
        } catch (MBeanAgentException mae) {
            LOG.error("Couldn't add MBean \"" + name + "\"", mae);
        }
    }

    /**
     * Add an input engine with the specified type,
     * and supply a monitoring MBean .
     *
     * @param type engine type
     * @param engine input engine
     *
     * @throws Error if 'engine' is not a PayloadReader
     */
    public final void addMonitoredEngine(String type,
                                         DAQComponentInputProcessor engine)
    {
        addConnector(new DAQInputConnector(type, engine));

        if (engine instanceof SpliceableSimpleReader) {
            addMBean(type, new MBeanWrapper(engine,
                                            spliceableInputReaderMethods));
        } else if (engine instanceof SimpleReader) {
            addMBean(type, new MBeanWrapper(engine, inputReaderMethods));
        } else if (engine instanceof SpliceablePayloadReader) {
            addMBean(type, new MBeanWrapper(engine,
                                            spliceableInputReaderMethods));
        } else if (engine instanceof PayloadReader) {
            addMBean(type, new MBeanWrapper(engine, inputReaderMethods));
        } else {
            throw new Error("Cannot monitor " + engine.getClass().getName());
        }
    }

    /**
     * Add an output engine with the specified type,
     * and supply a monitoring MBean.
     *
     * @param type engine type
     * @param engine output engine
     *
     * @throws Error if 'engine' is not a PayloadOutputEngine
     */
    public final void addMonitoredEngine(String type,
                                         DAQComponentOutputProcess engine)
    {
        addMonitoredEngine(type, engine, false);
    }

    /**
     * Add an output engine with the specified type,
     * and supply a monitoring MBean .
     *
     * @param type engine type
     * @param engine output engine
     * @param allowMultipleConnections <tt>true</tt> if this output connector
     *                                 can connect to multiple input connectors
     *
     * @throws Error if 'engine' is not a PayloadOutputEngine
     */
    public final void addMonitoredEngine(String type,
                                         DAQComponentOutputProcess engine,
                                         boolean allowMultipleConnections)
    {
        addConnector(new DAQOutputConnector(type, engine,
                                            allowMultipleConnections));

        if (engine instanceof PayloadOutputEngine) {
            addMBean(type, new MBeanWrapper(engine, outputEngineMethods));
        } else if (engine instanceof SingleOutputEngine) {
            addMBean(type, new MBeanWrapper(engine, singleEngineMethods));
        } else if (engine instanceof MultiOutputEngine) {
            addMBean(type, new MBeanWrapper(engine, multiEngineMethods));
        } else if (engine instanceof SimpleOutputEngine) {
            addMBean(type, new MBeanWrapper(engine, simpleEngineMethods));
        } else {
            throw new Error("Cannot monitor " + engine.getClass().getName());
        }
    }

    /**
     * Add a splicer.
     *
     * @param splicer splicer
     */
    public final void addSplicer(Splicer splicer)
    {
        addConnector(new DAQSplicer(splicer));
    }

    /**
     * Begin packaging events for the specified subrun.
     *
     * @param subrunNumber subrun number
     * @param startTime time of first good hit in subrun
     */
    public void commitSubrun(int subrunNumber, long startTime)
    {
        // override in event builder component
    }

    /**
     * Configure a component.
     *
     * @throws DAQCompException if the component is not in the correct state
     *
     * @deprecated this should no longer happen!
     */
    public final void configure()
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.configure(null);
    }

    /**
     * Configure a component using the specified configuration name.
     *
     * @param name configuration name
     *
     * @throws DAQCompException if the component is not in the correct state
     */
    public final void configure(String name)
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.configure(name);
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
     */
    public final void connect()
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.connect(null);
    }

    /**
     * Connect all specified channels.
     *
     * @param list list of connections
     *
     * @throws DAQCompException if there is a problem finding a connection
     */
    public final void connect(Connection[] list)
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.connect(list);
    }

    /**
     * Destroy all connections and threads.
     *
     * @throws DAQCompException if there is a problem
     */
    public final void destroy()
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.destroy();
    }

    /**
     * Disconnect all connections and return to idle state.
     *
     * @throws DAQCompException if the component is in the wrong state
     */
    public final void disconnect()
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.disconnect();
    }

    /**
     * Override this method to perform any actions after all output connectors
     * have been disconnected.
     *
     * @throws DAQCompException if there is a problem
     */
    public void disconnected()
        throws DAQCompException
    {
        // Override me!
    }

    /**
     * Enable local monitoring.
     *
     * @param interval number of seconds between monitoring entries
     */
    public void enableLocalMonitoring(int interval)
    {
        if (mbeanAgent == null) {
            throw new Error("MBean agent is null");
        }

        moniLocal = mbeanAgent.getLocalMonitoring(getName(), getNumber(),
                                                  interval);
    }

    /**
     * Emergency stop of component.
     *
     * @throws DAQCompException if there is a problem
     */
    public final void forcedStop()
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.forcedStop();
    }

    /**
     * Get the buffer cache for the specified data type.
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
     * Get the number of events for the given subrun.
     * NOTE: This should only be implemented by the event builder component.
     *
     * @param subrun subrun number
     *
     * @return -1L
     */
    public long getEvents(int subrun)
        throws DAQCompException
    {
        return -1L;
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
     * Get the log level for this component.
     *
     * @return log level
     */
    public Level getLogLevel()
    {
        return logLevel;
    }

    /**
     * Get MBean XML-RPC server port for this component.
     *
     * @return <tt>0</tt> if there is no MBean server for this component
     */
    public final int getMBeanXmlRpcPort()
    {
        int port;
        if (mbeanAgent == null) {
            port = 0;
        } else {
            try {
                port = mbeanAgent.getXmlRpcPort();
            } catch (MBeanAgentException mbe) {
                LOG.error("Couldn't get MBean server XML-RPC port", mbe);
                port = Integer.MIN_VALUE;
            }
        }


        return port;
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
     * Return the usage message for any options handled in
     * <tt>handleOption()</tt>.
     *
     * @return usage string
     */
    public String getOptionUsage()
    {
        return "";
    }

    /**
     * Get current state.
     *
     * @return current state
     */
    public final int getState()
    {
        if (stateTask == null) {
            return STATE_DESTROYED;
        }

        return stateTask.getState();
    }

    /**
     * Get string description of current state.
     *
     * @return current state string
     */
    public final String getStateString()
    {
        if (stateTask == null) {
            return STATE_NAMES[STATE_DESTROYED];
        }

        return stateTask.getStateString();
    }

    /**
     * Return this component's version id.
     *
     * @return version id string
     */
    public abstract String getVersionInfo();

    /**
     * Handle a command-line option.
     *
     * @param arg0 first argument string
     * @param arg1 second argument string (or <tt>null</tt> if none available)
     *
     * @return 0 if the argument string was not used
     *         1 if only the first argument string was used
     *         2 if both argument strings were used
     */
    public int handleOption(String arg0, String arg1)
    {
        return 0;
    }

    /**
     * Is there an error from the last request?
     *
     * @return <tt>true</tt> if the last request generated an error
     */
    public boolean isError()
    {
        return stateTask != null && stateTask.isError();
    }

    /**
     * Are connectors still running?
     *
     * @return <tt>true</tt> if any engine is still running
     */
    public boolean isRunning()
    {
        Iterator iter = connectors.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            if (conn.isRunning()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Are all connectors stopped?
     *
     * @return <tt>true</tt> if all connectors have stopped
     */
    public boolean isStopped()
    {
        Iterator iter = connectors.iterator();
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
        return connectors.iterator();
    }

    /**
     * Prepare for the subrun by marking events untrustworthy.
     *
     * @param subrunNumber subrun number
     */
    public void prepareSubrun(int subrunNumber)
    {
        // override in event builder component
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
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.reset();
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
            waitForStateChange(STATE_RESETTING);
        } catch (DAQCompException dce) {
            LOG.error("Had to destroy " + this, dce);
            needDestroy = true;
        } catch (IOException io) {
            LOG.error("Had to destroy " + this, io);
            needDestroy = true;
        }

        if (needDestroy || isError()) {
            try {
                destroy();
                waitForStateChange(STATE_DESTROYING);
            } catch (DAQCompException dce) {
                LOG.error("Could not destroy " + this, dce);
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
     * Override this method to set the destination directory where the
     * dispatch files will be saved.
     *
     * @param dirName The absolute path of directory where the dispatch files will be stored.
     */
    public void setDispatchDestStorage(String dirName) {
        // Override me!
    }

    /**
     * Set the component ID assigned by the server.
     * This should only be called by DAQCompServer.
     *
     * @param id assigned ID
     */
    final void setId(int id)
    {
        this.id = id;
    }

    /**
     * Set the log level for this component.
     *
     * @param level new log level
     */
    public void setLogLevel(Level level)
    {
        logLevel = level;
    }

    /**
     * Override this method to set the maximum size of the dispatch file.
     *
     * @param maxFileSize the maximum size of the dispatch file.
     */
    public void setMaxFileSize(long maxFileSize) {
        // Override me!
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
        start(true);
    }

    /**
     * Start background threads.
     *
     * @param startMBeanAgent if <tt>false</tt>, do not start MBean server
     *
     * @throws DAQCompException if input server cannot be started
     */
    public final void start(boolean startMBeanAgent)
        throws DAQCompException
    {
        DAQCompException compEx = null;

        if (startMBeanAgent && mbeanAgent != null) {
            try {
                mbeanAgent.start();
            } catch (MBeanAgentException mae) {
                compEx = new DAQCompException("Couldn't start MBean agent",
                                              mae);
            }

            if (moniLocal != null) {
                mbeanAgent.setMonitoringData(moniLocal);
            }
        }

        // sort connectors so they are started in the correct order
        if (!connSorted) {
            Collections.sort(connectors, new ConnCmp());
            connSorted = true;
        }

        Iterator iter = connectors.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            try {
                conn.start();
            } catch (IllegalThreadStateException itse) {
                compEx = new DAQCompException("Couldn't start " + getName() +
                                              ":" + conn.getType() +
                                              "; engine has probably been" +
                                              "  added more than once", itse);
            } catch (Exception ex) {
                compEx = new DAQCompException("Couldn't start " + getName() +
                                              ":" + conn.getType(), ex);
            }

            if (conn.isInput()) {
                try {
                    conn.startServer(getByteBufferCache(conn.getType()));

                    if (LOG.isInfoEnabled()) {
                        LOG.info(getName() + ":" + conn.getType() +
                                 " listening on port " + conn.getPort());
                    }
                } catch (IOException ioe) {
                    compEx = new DAQCompException("Couldn't start " +
                                                  getName() + ":" +
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
     * Start connectors.
     *
     * @throws DAQCompException if there is a problem
     */
    public final void startEngines()
        throws DAQCompException
    {
        DAQCompException compEx = null;

        Iterator iter = connectors.iterator();
        while (iter.hasNext()) {
            DAQConnector conn = (DAQConnector) iter.next();

            try {
                conn.startProcessing();
            } catch (Exception ex) {
                LOG.error("Couldn't start " + getName(), ex);
                compEx = new DAQCompException("Couldn't start " + getName() +
                                              ":" + conn.getType(),
                                              ex);
            }
        }

        if (compEx != null) {
            forcedStop();
            waitForStateChange(STATE_FORCING_STOP);
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
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        if (moniLocal != null) {
            moniLocal.startMonitoring();
        }

        stateTask.startRun(runNumber);
    }

    /**
     * Start the subrun using the supplied data.
     *
     * @param data subrun data
     *
     * @throws DAQCompException if there is a problem with the configuration
     */
    public long startSubrun(List<FlasherboardConfiguration> data)
        throws DAQCompException
    {
        // override in stringHub component
        return -1L;
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
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        if (moniLocal != null) {
            moniLocal.stopMonitoring();
        }

        stateTask.stopRun();
    }

    /**
     * Override this method to perform any clean-up after all connectors
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
     * Override this method to perform any clean-up before connectors
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
     * Wait for the component's state to change from the specified state.
     *
     * TODO: Fix this to be more efficient!!!
     *
     * @param curState the state from which the component is expected to change
     */
    public void waitForStateChange(int curState)
    {
        while (stateTask != null && stateTask.getState() == curState &&
               !stateTask.isError())
        {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
    }

    /**
     * Debugging string.
     *
     * @return debugging string
     */
    public String toString()
    {
        StringBuffer buf = new StringBuffer(getName());

        if (caches.size() > 0) {
            buf.append(" [");

            boolean first = true;
            for (Iterator iter = caches.keySet().iterator(); iter.hasNext(); ) {
                if (first) {
                    first = false;
                } else {
                    buf.append(',');
                }

                buf.append(iter.next());
            }
            buf.append(']');
        }

        if (connectors.size() > 0) {
            buf.append(" [");

            boolean first = true;
            for (Iterator iter = connectors.iterator(); iter.hasNext(); ) {
                if (first) {
                    first = false;
                } else {
                    buf.append(',');
                }

                buf.append(iter.next());
            }
            buf.append(']');
        }

        return buf.toString();
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

            // if 1st object is an output engine...
            if (dc1.isOutput()) {
                if (!dc2.isOutput()) {
                    // if 2nd object is not an output engine, it goes after 1st
                    return -1;
                }
            } else if (dc2.isOutput()) {
                // if 2nd obj is an output engine, it goes before 1st
                return 1;
            } else if (dc1.isInput()) {
                // 1st object is an input engine...
                if (!dc2.isInput()) {
                    // if 2nd object is not an input engine, it goes after 1st
                    return -1;
                }
            } else if (dc2.isInput()) {
                // if 2nd obj is an input engine, it goes before 1st
                return 1;
            } else if (dc1.isSplicer()) {
                if (!dc2.isSplicer()) {
                    // if 2nd object is not a splicer, it goes after 1st
                    return -1;
                }
            } else if (dc2.isSplicer()) {
                // if 2nd obj is a splicer, it goes before 1st
                return 1;
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
