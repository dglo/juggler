package icecube.daq.juggler.component;

import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.io.SimpleReader;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.io.SpliceableSimpleReader;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.alert.DAQAlerter;
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
 * <li><i>optional</i> switchToNewRun()
 * <li>stopRun()
 * </ol>
 *
 * @version $Id: DAQComponent.java 13548 2012-03-08 21:57:35Z dglo $
 */
public abstract class DAQComponent
    implements IComponent
{
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
    private ArrayList<DAQConnector> connectors = new ArrayList<DAQConnector>();
    /** has list of connectors been sorted? */
    private boolean connSorted;

    /** hash table of byte buffer caches */
    private HashMap caches = new HashMap();

    /** MBean manager */
    private MBeanAgent mbeanAgent;

    /** Local monitoring, is enabled */
    private LocalMonitor moniLocal;

    /** Thread which transitions between states */
    private StateTask stateTask;

    /** Alert manager */
    private Alerter alerter = new DAQAlerter();

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

        stateTask = new StateTask(this);

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
    private final void addConnector(DAQConnector conn)
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
     */
    public final void addMonitoredEngine(String type,
                                         DAQComponentInputProcessor engine)
    {
        addMonitoredEngine(type, engine, false);
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
    private final void addMonitoredEngine(String type,
                                          DAQComponentInputProcessor engine,
                                          boolean optional)
    {
        addConnector(new DAQInputConnector(type, engine, optional));

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
     */
    public final void addMonitoredEngine(String type,
                                         DAQComponentOutputProcess engine)
    {
        addMonitoredEngine(type, engine, false, false);
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
     */
    public final void addMonitoredEngine(String type,
                                          DAQComponentOutputProcess engine,
                                          boolean allowMultipleConnections)
    {
        addMonitoredEngine(type, engine, allowMultipleConnections, false);
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
     * @throws Error if 'engine' is not a known output engine
     */
    private final void addMonitoredEngine(String type,
                                          DAQComponentOutputProcess engine,
                                          boolean allowMultipleConnections,
                                          boolean optional)
    {
        addConnector(new DAQOutputConnector(type, engine,
                                            allowMultipleConnections,
                                            optional));

        if (engine instanceof SimpleOutputEngine) {
            addMBean(type, new MBeanWrapper(engine, simpleEngineMethods));
        } else {
            throw new Error("Cannot monitor " + engine.getClass().getName());
        }
    }

    /**
     * Add an optional input engine with the specified type,
     * and supply a monitoring MBean.
     *
     * @param type engine type
     * @param engine input engine
     *
     */
    public final void addOptionalEngine(String type,
                                        DAQComponentInputProcessor engine)
    {
        addMonitoredEngine(type, engine, true);
    }

    /**
     * Add an optional output engine with the specified type,
     * and supply a monitoring MBean.
     *
     * @param type engine type
     * @param engine output engine
     *
     */
    public final void addOptionalEngine(String type,
                                        DAQComponentOutputProcess engine)
    {
        addOptionalEngine(type, engine, false);
    }

    /**
     * Add an optional output engine with the specified type,
     * and supply a monitoring MBean.
     *
     * @param type engine type
     * @param engine output engine
     * @param allowMultipleConnections <tt>true</tt> if this output connector
     *                                 can connect to multiple input connectors
     *
     */
    public final void addOptionalEngine(String type,
                                        DAQComponentOutputProcess engine,
                                        boolean allowMultipleConnections)
    {
        addMonitoredEngine(type, engine, allowMultipleConnections, true);
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
     * Flush buffer caches
     */
    public void flushCaches()
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
     * Get the alert manager.
     *
     * @return alert manager
     */
    public Alerter getAlerter()
    {
        return alerter;
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
     * Get the run data for a builder.
     *
     * @param runnum run number
     *
     * @return list of run data values
     */
    public long[] getRunData(int runnum)
        throws DAQCompException
    {
        return new long[0];
    }

    /**
     * Get the current run number.
     *
     * @return current run number
     */
    public int getRunNumber()
    {
        return -1;
    }

    /**
     * Get component name (and ID, for non-hub components.)
     *
     * @return full name
     */
    public String getFullName()
    {
        if (num == 0 && !name.endsWith("Hub")) {
            return name;
        }

        return name + "#" + num;
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
    public final DAQState getState()
    {
        if (stateTask == null) {
            return DAQState.DESTROYED;
        }

        return stateTask.getState();
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
        for (DAQConnector conn : connectors) {
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
        for (DAQConnector conn : connectors) {
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
    public final Iterable<DAQConnector> listConnectors()
    {
        return connectors;
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
            waitForStateChange(DAQState.RESETTING);
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
                waitForStateChange(DAQState.DESTROYING);
            } catch (DAQCompException dce) {
                LOG.error("Could not destroy " + this, dce);
            }
        }

        return needDestroy;
    }

    /**
     * Set the alert manager.
     *
     * @param alerter alert manager
     */
    public void setAlerter(Alerter alerter)
    {
        this.alerter = alerter;
    }

    /**
     * Override this method to set the destination directory where the
     * dispatch files will be saved.
     *
     * @param dirName The absolute path of directory where the
     * dispatch files will be stored.
     */
    public void setDispatchDestStorage(String dirName)
    {
        // Override me!
    }

    /**
     * Override this method to receive the first "good" time for the current
     * run.
     *
     * @param firstTime first "good" time
     */
    public void setFirstGoodTime(long firstTime)
        throws DAQCompException, IOException
    {
        // Override me!
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
     * This should only be called by DAQCompServer.
     *
     * @param id assigned ID
     */
    final void setId(int id)
    {
        this.id = id;
    }

    /**
     * Override this method to receive the last "good" time for the current
     * run.
     *
     * @param lastTime last "good" time
     */
    public void setLastGoodTime(long lastTime)
        throws DAQCompException, IOException
    {
        // Override me!
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
    public void setMaxFileSize(long maxFileSize)
    {
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

        for (DAQConnector conn : connectors) {
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

                    if (LOG.isDebugEnabled()) {
                        LOG.debug(getName() + ":" + conn.getType() +
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

        for (DAQConnector conn : connectors) {
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
            waitForStateChange(DAQState.FORCING_STOP);
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
     * Stop the MBean agent associated with this component.
     *
     * @throws DAQCompException if MBean agent was not stopped
     */
    public void stopMBeanAgent()
        throws DAQCompException
    {
        if (mbeanAgent != null) {
            try {
                mbeanAgent.stop();
            } catch (MBeanAgentException mae) {
                throw new DAQCompException("Couldn't stop MBean agent", mae);
            }
        }
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
     * Stop the state task associated with this component.
     */
    public void stopStateTask()
    {
        if (stateTask != null) {
            stateTask.stop();
            stateTask = null;
        }
    }

    /**
     * Override this method to perform clean-up after all connectors
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
     * Switch to a new run.
     *
     * @param runNumber run number
     *
     * @throws DAQCompException if there is a problem
     */
    public final void switchToNewRun(int runNumber)
        throws DAQCompException
    {
        if (stateTask == null) {
            throw new DAQCompException("Component " + getName() +
                                       " has been destroyed");
        }

        stateTask.switchToNewRun(runNumber);
    }

    /**
     * Override this method to perform any actions related to switching to
     * a new run.
     *
     * @param runNumber new run number
     *
     * @throws DAQCompException if there is a problem switching the component
     */
    public void switching(int runNumber)
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
    public void waitForStateChange(DAQState curState)
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
            for (Iterator iter = caches.keySet().iterator(); iter.hasNext();) {
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
            for (DAQConnector conn : connectors) {
                if (first) {
                    first = false;
                } else {
                    buf.append(',');
                }

                buf.append(conn);
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
