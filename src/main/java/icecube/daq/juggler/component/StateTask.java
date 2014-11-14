package icecube.daq.juggler.component;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StateTask
    implements Runnable
{
    private static final Log LOG = LogFactory.getLog(StateTask.class);

    /** DAQ component */
    private IComponent comp;
    /** <tt>true</tt> if setState() method should be enabled for testing */
    private boolean testing;
    /** current component state */
    private DAQState state = DAQState.IDLE;
    /** previous component state */
    private DAQState prevState = state;

    /** is the task thread running? */
    private boolean running;
    /** has the task thread stopped? */
    private boolean stopped = true;
    private boolean caughtError;
    private boolean stateChanged;
    private boolean calledStopping;

    private Connection[] connectList;
    private String configName;
    private int startNumber;
    private int switchNumber;

    StateTask(IComponent comp)
    {
        this(comp, false);
    }

    StateTask(IComponent comp, boolean testing)
    {
        this.comp = comp;
        this.testing = testing;
    }

    private void changeState(DAQState newState)
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
        if (state != DAQState.CONFIGURING && state != DAQState.CONNECTED &&
            state != DAQState.READY)
        {
            throw new DAQCompException("Cannot configure component " +
                                       getName() + " from state " +
                                       getState());
        }

        synchronized (this) {
            configName = cfgName;
            changeState(DAQState.CONFIGURING);
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
        if (state != DAQState.IDLE) {
            throw new DAQCompException("Cannot connect component " +
                                       getName() + " from state " +
                                       getState() + " (should be idle)");
        }

        synchronized (this) {
            connectList = list;
            changeState(DAQState.CONNECTING);
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
        if (state == DAQState.DESTROYED) {
            return;
        }

        synchronized (this) {
            changeState(DAQState.DESTROYING);
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
        if (state == DAQState.IDLE) {
            // allow idle component to be 'disconnected'
            return;
        }

        if (state != DAQState.CONNECTING && state != DAQState.CONNECTED &&
            state != DAQState.CONFIGURING && state != DAQState.READY &&
            state != DAQState.DISCONNECTING)
        {
            throw new DAQCompException("Cannot disconnect component " +
                                       getName() + " from state " +
                                       getState());
        }

        synchronized (this) {
            changeState(DAQState.DISCONNECTING);
        }
    }

    private synchronized void doConfigure()
        throws DAQCompException
    {
        if (configName != null) {
            comp.configuring(configName);
        }

        state = DAQState.READY;
    }

    private synchronized void doConnect()
        throws DAQCompException
    {
        for (DAQConnector dc : comp.listConnectors()) {
            if (dc.isOutput() &&
                !((DAQOutputConnector) dc).isConnected())
            {
                throw new DAQCompException("Component " + getName() +
                                           " has unconnected " +
                                           dc.getType() + " output");
            }
        }

        state = DAQState.CONNECTED;
    }

    private synchronized void doConnect(Connection[] list)
        throws DAQCompException, IOException
    {
        for (int i = 0; i < list.length; i++) {
            DAQOutputConnector conn = null;

            for (DAQConnector dc : comp.listConnectors()) {
                if (dc.isOutput() && list[i].matches(dc.getType())) {
                    if (conn != null) {
                        final String errMsg = "Component " + getName() +
                            " has multiple " + list[i].getType() +
                            " outputs";

                        throw new DAQCompException(errMsg);
                    }

                    conn = (DAQOutputConnector) dc;
                    if (conn.isConnected() &&
                        !conn.allowMultipleConnections())
                    {
                        final String errMsg = "Component " + getName() +
                            " output " + list[i].getType() +
                            " is already connected";

                        throw new DAQCompException(errMsg);
                    }
                }
            }

            if (conn == null) {
                throw new DAQCompException("Component " + getName() +
                                           " does not contain " +
                                           list[i].getType() + " output");
            }

            try {
                conn.connect(comp.getByteBufferCache(conn.getType()), list[i]);
            } catch (IOException ioe) {
                throw new DAQCompException("Cannot connect " + conn +
                                           " to connection #" + i + ": " +
                                           list[i], ioe);
            }
        }

        state = DAQState.CONNECTED;
    }

    private void doDestroy()
        throws DAQCompException
    {
        DAQCompException compEx = null;

        for (DAQConnector conn : comp.listConnectors()) {
            try {
                conn.destroy();
            } catch (Exception ex) {
                compEx = new DAQCompException("Couldn't destroy " +
                                              getName() + ":" +
                                              conn.getType(), ex);
            }
        }

        try {
            comp.stopMBeanAgent();
        } catch (DAQCompException dce) {
            if (compEx == null) {
                compEx = dce;
            }
        }

        comp.stopStateTask();

        state = DAQState.DESTROYED;

        if (compEx != null) {
            throw compEx;
        }
    }

    private void doDisconnect()
        throws DAQCompException, IOException
    {
        IOException ioEx = null;
        for (DAQConnector dc : comp.listConnectors()) {
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

        comp.disconnected();

        comp.flushCaches();

        state = DAQState.IDLE;
    }

    /**
     * Emergency stop of component.
     *
     * @throws DAQCompException if there is a problem
     */
    private void doForcedStop()
        throws DAQCompException
    {
        DAQCompException compEx = null;

        for (DAQConnector conn : comp.listConnectors()) {
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

        if (comp.isStopped()) {
            comp.stopped();
            state = DAQState.READY;
        }
    }

    private void doReset()
        throws DAQCompException, IOException
    {
        if (prevState == DAQState.RESETTING) {
            prevState = DAQState.RUNNING;
        }

        if (prevState == DAQState.STARTING || prevState == DAQState.RUNNING ||
            prevState == DAQState.STOPPING ||
            prevState == DAQState.FORCING_STOP)
        {
            // if we haven't called the component's stopping() method yet...
            if (!calledStopping) {
                comp.stopping();
                calledStopping = true;
            }

            comp.forceStopping();

            doForcedStop();
            prevState = DAQState.READY;
        }

        comp.resetting();

        if (prevState == DAQState.CONNECTING ||
            prevState == DAQState.CONNECTED ||
            prevState == DAQState.CONFIGURING ||
            prevState == DAQState.READY ||
            prevState == DAQState.DISCONNECTING)
        {
            doDisconnect();
        } else {
            state = prevState;
        }

        if (state != DAQState.IDLE)
        {
            throw new DAQCompException("Bad state " + getState() +
                                       " after reset (prevState was " +
                                       prevState + ")");
        }
    }

    private void doStartRun()
        throws DAQCompException
    {
        // haven't yet called stopping() for this run
        calledStopping = false;

        comp.starting(startNumber);

        comp.startEngines();

        comp.started(startNumber);

        state = DAQState.RUNNING;
    }

    private void doStopRun()
        throws DAQCompException
    {
        if (!calledStopping) {
            comp.stopping();
            calledStopping = true;
        }

        if (state == DAQState.STOPPING && comp.isStopped()) {
            comp.stopped();
            state = DAQState.READY;
        }
    }

    private void doSwitchRun()
        throws DAQCompException
    {
        comp.switching(switchNumber);
        state = DAQState.RUNNING;
    }

    /**
     * Emergency stop of component.
     *
     * @throws DAQCompException if there is a problem
     */
    void forcedStop()
        throws DAQCompException
    {
        if (state == DAQState.READY || state == DAQState.FORCING_STOP) {
            return;
        }

        if (state != DAQState.RUNNING && state != DAQState.STOPPING) {
            throw new DAQCompException("Cannot force-stop component " +
                                       getName() + " from state " +
                                       getState());
        }

        synchronized (this) {
            changeState(DAQState.FORCING_STOP);
        }
    }

    /**
     * Get component name.
     *
     * @return name
     */
    public String getName()
    {
        return comp.getFullName();
    }

    /**
     * Get current state.
     *
     * @return current state
     */
    DAQState getState()
    {
        return state;
    }

    public boolean isError()
    {
        return caughtError;
    }

    public boolean isRunning()
    {
        return running;
    }

    public boolean isStopped()
    {
        return stopped;
    }

    /**
     * Reset the component back to the idle state, doing any necessary
     * cleanup.
     *
     * @throws DAQCompException if the component cannot be reset
     */
    final void reset()
        throws DAQCompException
    {
        if (state != DAQState.IDLE && state != DAQState.DESTROYING &&
            state != DAQState.DESTROYED)
        {
            synchronized (this) {
                changeState(DAQState.RESETTING);
            }
        }
    }

    public void run()
    {
        running = true;
        stopped = false;
        while (running) {
            try {
                runInternal();
            } catch (Throwable t) {
                LOG.error("StateTask thread error", t);
            }
        }

        stopped = true;
    }

    private void runInternal()
    {
        synchronized (this) {
            if (stateChanged) {
                stateChanged = false;
            } else {
                try {
                    if (state == DAQState.STOPPING) {
                        wait(100);
                    } else {
                        wait();
                    }
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }

        boolean success;
        switch (state) {
        case CONFIGURING:
            try {
                doConfigure();
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Configure failed", dce);
                success = false;
                caughtError = true;
            }

            if (!success) {
                // revert to the previous state if configure failed
                state = prevState;
            }

            break;
        case CONNECTED:
            // nothing to be done
            break;
        case CONNECTING:
            try {
                if (connectList == null) {
                    doConnect();
                } else {
                    doConnect(connectList);
                }
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Connect failed", dce);
                success = false;
                caughtError = true;
            } catch (IOException ioe) {
                LOG.error("Connect failed", ioe);
                success = false;
                caughtError = true;
            }

            if (!success) {
                state = DAQState.IDLE;
                try {
                    doDisconnect();
                } catch (DAQCompException dce) {
                    LOG.error("Couldn't disconnect after" +
                              " failed connect", dce);
                } catch (IOException ioe) {
                    LOG.error("Couldn't disconnect after" +
                              " failed connect", ioe);
                }
            }
            break;
        case DESTROYING:
            try {
                doDestroy();
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Destroy failed", dce);
                success = false;
                caughtError = true;
            }

            if (!success) {
                // pretend we were destroyed
                state = DAQState.DESTROYED;
            }

            break;
        case DESTROYED:
            running = false;
            break;
        case DISCONNECTING:
            try {
                doDisconnect();
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Disconnect failed", dce);
                success = false;
                caughtError = true;
            } catch (IOException ioe) {
                LOG.error("Disconnect failed", ioe);
                success = false;
                caughtError = true;
            }

            if (!success) {
                // revert to the previous state if disconnect failed
                state = prevState;
            }

            break;
        case FORCING_STOP:
            try {
                doForcedStop();
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Forced stop failed", dce);
                success = false;
                caughtError = true;
            }

            if (!success) {
                // revert to the previous state if forced stop failed
                state = prevState;
            }

            break;
        case IDLE:
            // nothing to be done
            break;
        case READY:
            // nothing to be done
            break;
        case RESETTING:
            try {
                doReset();
            } catch (DAQCompException dce) {
                LOG.error("Reset failed", dce);
                caughtError = true;
            } catch (IOException ioe) {
                LOG.error("Reset failed", ioe);
                caughtError = true;
            }

            break;
        case RUNNING:
            // nothing to be done
            break;
        case STARTING:
            try {
                doStartRun();
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Start run failed", dce);
                success = false;
                caughtError = true;
            }

            if (!success) {
                // revert to the previous state if startRun failed
                state = prevState;
            }

            break;
        case STOPPING:
            try {
                doStopRun();
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Stop run failed", dce);
                success = false;
                caughtError = true;
            }

            if (!success) {
                // revert to the previous state if stopRun failed
                state = prevState;
            }

            break;
        case SWITCHING:
            try {
                doSwitchRun();
                success = true;
            } catch (DAQCompException dce) {
                LOG.error("Switch run failed", dce);
                success = false;
                caughtError = true;
            }

            if (!success) {
                // revert to the previous state if switchRun failed
                state = prevState;
            }

            break;
        default:
            LOG.error("StateTask not handling " + getState());
            break;
        }
    }

    void setState(DAQState state)
        throws DAQCompException
    {
        if (!testing) {
            throw new DAQCompException("Cannot directly set the current state");
        }

        this.state = state;
    }

    void startRun(int runNumber)
        throws DAQCompException
    {
        // ignore start command if already starting or running
        if (state == DAQState.STARTING || state == DAQState.RUNNING) {
            return;
        }

        if (state != DAQState.READY) {
            throw new DAQCompException("Cannot start component " +
                                       getName() + " from state " +
                                       getState());
        }

        synchronized (this) {
            startNumber = runNumber;
            changeState(DAQState.STARTING);
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
        // ignore stop command if already stopped
        if (state == DAQState.READY || state == DAQState.STOPPING) {
            return;
        }

        if (state != DAQState.RUNNING) {
            throw new DAQCompException("Cannot stop component " + getName() +
                                       " from state " + getState());
        }

        synchronized (this) {
            changeState(DAQState.STOPPING);
        }
    }

    /**
     * Switch to a new run.
     *
     * @throws DAQCompException if there is a problem
     */
    void switchToNewRun(int runNumber)
        throws DAQCompException
    {
        // ignore command if already switching
        if (state == DAQState.SWITCHING) {
            return;
        }

        if (state != DAQState.RUNNING) {
            throw new DAQCompException("Cannot switch component " + getName() +
                                       " from state " + getState());
        }

        synchronized (this) {
            switchNumber = runNumber;
            changeState(DAQState.SWITCHING);
        }
    }

    public String toString()
    {
        return "StateTask[" + state + "(prev=" + prevState + ")," +
            (running ? "" : "!") + "running," +
            (caughtError ? "" : "!") + "caughtError," +
            (stateChanged ? "" : "!") + "stateChanged," +
            (calledStopping ? "" : "!") + "calledStopping," +
            "config=" + configName + "," +
            "startNum=" + startNumber +
            "]";
    }
}
