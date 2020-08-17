package icecube.daq.juggler.component;

import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.test.LoggingCase;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestSuite;

class UnimplementedException
    extends Error
{
    UnimplementedException() { super("Unimplemented"); }
    UnimplementedException(String msg) { super(msg); }
}

class StateComponent
    implements IComponent
{
    private String fullName;
    private ArrayList<DAQConnector> connList = new ArrayList<DAQConnector>();
    private boolean connStopped = true;

    private boolean didConfiguring;
    private boolean didStartEngines;
    private boolean didStarting;
    private boolean didStarted;
    private boolean didSwitching;
    private boolean didStopping;
    private boolean didForceStopping;
    private boolean didStopped;
    private boolean didDisconnected;
    private boolean didStopMBeanAgent;
    private boolean didStopStateTask;
    private boolean didResetting;
    private boolean didFlushCaches;

    public StateComponent(String fullName)
    {
        this.fullName = fullName;
    }

    /**
     * Close all open files, sockets, etc.
     * NOTE: This is only used by unit tests.
     *
     * @throws IOException if there is a problem
     */
    @Override
    public void closeAll()
        throws IOException
    {
        throw new UnimplementedException();
    }

    /**
     * Configure a component using the specified configuration name.
     *
     * @param configName configuration name
     *
     * @throws DAQCompException if there is a problem configuring
     */
    @Override
    public void configuring(String configName)
        throws DAQCompException
    {
        didConfiguring = true;
    }

     /**
     * Destroy all connections and threads.
     *
     * @throws DAQCompException if there is a problem
     */
    @Override
    public void destroy()
        throws DAQCompException
    {
        throw new UnimplementedException();
    }

    boolean didConfiguring() { return didConfiguring; }
    boolean didDisconnected() { return didDisconnected; }
    boolean didFlushCaches() { return didFlushCaches; }
    boolean didForceStopping() { return didForceStopping; }
    boolean didResetting() { return didResetting; }
    boolean didStartEngines() { return didStartEngines; }
    boolean didStarted() { return didStarted; }
    boolean didStarting() { return didStarting; }
    boolean didStopMBeanAgent() { return didStopMBeanAgent; }
    boolean didStopStateTask() { return didStopStateTask; }
    boolean didStopped() { return didStopped; }
    boolean didStopping() { return didStopping; }
    boolean didSwitching() { return didSwitching; }

    /**
     * Perform any actions after all output connectors have been disconnected.
     *
     * @throws DAQCompException if there is a problem
     */
    @Override
    public void disconnected()
        throws DAQCompException
    {
        didDisconnected = true;
    }

    /**
     * Flush buffer caches
     */
    @Override
    public void flushCaches()
    {
        didFlushCaches = true;
    }

    /**
     * Perform any actions which should happen just before a run is
     * force-stopped.
     *
     * @throws DAQCompException if there is a problem
     */
    @Override
    public void forceStopping()
        throws DAQCompException
    {
        didForceStopping = true;
    }

    /**
     * Emergency stop of component.
     *
     * @throws DAQCompException if there is a problem
     */
    @Override
    public void forcedStop()
        throws DAQCompException
    {
        throw new UnimplementedException();
    }

    @Override
    public AlertQueue getAlertQueue()
    {
        throw new UnimplementedException();
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
    @Override
    public IByteBufferCache getByteBufferCache(String type)
        throws DAQCompException
    {
        throw new UnimplementedException();
    }

    /**
     * Get component name (and ID, for non-hub components.)
     *
     * @return full name
     */
    @Override
    public String getFullName()
    {
        return fullName;
    }

    /**
     * Return the requested MBean.
     *
     * @return MBean
     *
     * @throws DAQCompException if there is a problem
     */
    @Override
    public Object getMBean(String name)
        throws DAQCompException
    {
        throw new UnimplementedException();
    }

    /**
     * Are all connectors stopped?
     *
     * @return <tt>true</tt> if all connectors have stopped
     */
    @Override
    public boolean isStopped()
    {
        return connStopped;
    }

    /**
     * Return an iterator for this component's I/O connections.
     *
     * @return connection iterator
     */
    @Override
    public Iterable<DAQConnector> listConnectors()
    {
        return connList;
    }

    /**
     * List all MBean names.
     *
     * @return list of MBean names
     */
    @Override
    public Set<String> listMBeans()
    {
        throw new UnimplementedException();
    }

    void resetInternalFlags()
    {
        didConfiguring = false;
        didStartEngines = false;
        didStarting = false;
        didStarted = false;
        didSwitching = false;
        didStopping = false;
        didStopped = false;
        didDisconnected = false;
        didStopMBeanAgent = false;
        didStopStateTask = false;
        didResetting = false;
        didFlushCaches = false;
    }

    /**
     * Reset internal state after a component has moved to idle.
     *
     * @throws DAQCompException if there is a problem resetting
     */
    @Override
    public void resetting()
        throws DAQCompException
    {
        didResetting = true;
    }

    @Override
    public void setAlerter(Alerter alerter)
    {
        throw new UnimplementedException();
    }

    /**
     * Set the location of the global configuration directory.
     *
     * @param dirName absolute path of configuration directory
     */
    @Override
    public void setGlobalConfigurationDir(String dirName)
    {
        throw new UnimplementedException();
    }

    /**
     * Start background threads.
     *
     * @param startMBeanAgent if <tt>false</tt>, do not start MBean server
     *
     * @throws DAQCompException if input server cannot be started
     */
    @Override
    public void start(boolean startMBeanAgent)
        throws DAQCompException
    {
        throw new UnimplementedException();
    }

    /**
     * Start connectors.
     *
     * @throws DAQCompException if there is a problem
     */
    @Override
    public void startEngines()
        throws DAQCompException
    {
        didStartEngines = true;
    }

    /**
     * Perform any actions which should happen just after a run is started.
     *
     * @param runNumber new run number
     *
     * @throws DAQCompException if there is a problem starting the component
     */
    @Override
    public void started(int runNumber)
        throws DAQCompException
    {
        didStarted = true;
    }

    /**
     * Perform any actions which should happen just before a run is started.
     *
     * @param runNumber new run number
     * @param domMode either DOMMODE_NORMAL or DOMMODE_EXTENDED
     *
     * @throws DAQCompException if there is a problem starting the component
     */
    @Override
    public void starting(int runNumber, int domMode)
        throws DAQCompException
    {
        didStarting = true;
    }

    /**
     * Stop the MBean agent associated with this component.
     *
     * @throws DAQCompException if MBean agent was not stopped
     */
    @Override
    public void stopMBeanAgent()
        throws DAQCompException
    {
        didStopMBeanAgent = true;
    }

    /**
     * Stop the state task associated with this component.
     */
    @Override
    public void stopStateTask()
    {
        didStopStateTask = true;
    }

    /**
     * Perform any clean-up after all connectors have stopped.
     *
     * @throws DAQCompException if there is a problem stopping the component
     */
    @Override
    public void stopped()
        throws DAQCompException
    {
        didStopped = true;
    }

    /**
     * Perform any clean-up before connectors are stopped.
     *
     * @throws DAQCompException if there is a problem stopping the component
     */
    @Override
    public void stopping()
        throws DAQCompException
    {
        didStopping = true;
    }

    /**
     * Perform any actions related to switching to a new run.
     *
     * @param runNumber new run number
     *
     * @throws DAQCompException if there is a problem switching the component
     */
    @Override
    public void switching(int runNumber)
        throws DAQCompException
    {
        didSwitching = true;
    }
}

enum StateAction
{
    DO_CONFIGURE, DO_CONNECT, DO_DESTROY, DO_DISCONNECT, DO_FORCED_STOP,
        DO_RESET, DO_START_RUN, DO_STOP_RUN,
}

public class StateTaskTest
    extends LoggingCase
{
    public StateTaskTest(String name)
    {
        super(name);
    }

    private static void checkOneFunc(boolean expBit, boolean compBit,
                                     String methodName,
                                     StateTransition trans)
    {
        if (expBit != compBit) {
            String notStr;
            if (compBit) {
                notStr = "not ";
            } else {
                notStr = "";
            }

            fail("Should " + notStr + "have called " + methodName +
                 "() during " + trans.getOldState() + "->" +
                 trans.getAction());
        }
    }

    private static void checkFunctions(StateComponent comp,
                                       StateTransition trans, int bitmap)
    {
        checkOneFunc(CompFunc.didConfiguring(bitmap), comp.didConfiguring(),
                     "configuring", trans);
        checkOneFunc(CompFunc.didDisconnected(bitmap), comp.didDisconnected(),
                     "disconnected", trans);
        checkOneFunc(CompFunc.didFlushCaches(bitmap), comp.didFlushCaches(),
                     "flushCaches", trans);
        checkOneFunc(CompFunc.didResetting(bitmap), comp.didResetting(),
                     "resetting", trans);
        checkOneFunc(CompFunc.didStartEngines(bitmap), comp.didStartEngines(),
                     "startEngines", trans);
        checkOneFunc(CompFunc.didStarted(bitmap), comp.didStarted(),
                     "started", trans);
        checkOneFunc(CompFunc.didStarting(bitmap), comp.didStarting(),
                     "starting", trans);
        checkOneFunc(CompFunc.didSwitching(bitmap), comp.didSwitching(),
                     "switching", trans);
        checkOneFunc(CompFunc.didStopMBeanAgent(bitmap),
                     comp.didStopMBeanAgent(),
                     "stopMBeanAgent", trans);
        checkOneFunc(CompFunc.didStopStateTask(bitmap),
                     comp.didStopStateTask(),
                     "stopStateTask", trans);
        checkOneFunc(CompFunc.didStopped(bitmap), comp.didStopped(),
                     "stopped", trans);
        checkOneFunc(CompFunc.didStopping(bitmap), comp.didStopping(),
                     "stopping", trans);
    }

    public static Test suite()
    {
        return new TestSuite(StateTaskTest.class);
    }

    public void testTransitions()
        throws DAQCompException
    {
        setVerbose(true);

        for (int i = 0; i < StateTransition.LIST.length; i++) {
            StateComponent comp = new StateComponent("comp");

            StateTask st = new StateTask(comp, true);

            StateTransition trans = StateTransition.LIST[i];

            st.setState(trans.getOldState());

            final String excMsg = trans.getExceptionMessage();

            try {
                switch (trans.getAction()) {
                case DO_CONFIGURE:
                    st.configure("foo");
                    break;
                case DO_CONNECT:
                    Connection[] list = new Connection[0];
                    st.connect(list);
                    break;
                case DO_DESTROY:
                    st.destroy();
                    break;
                case DO_DISCONNECT:
                    st.disconnect();
                    break;
                case DO_FORCED_STOP:
                    st.forcedStop();
                    break;
                case DO_RESET:
                    st.reset();
                    break;
                case DO_START_RUN:
                    st.startRun(12345, IComponent.DOMMODE_NORMAL);
                    break;
                case DO_STOP_RUN:
                    st.stopRun();
                    break;
                default:
                    throw new UnimplementedException("Unimplemented action " +
                                                     trans.getAction());
                }

                if (excMsg != null) {
                    fail(trans.getAction().toString() + " from " +
                         trans.getOldState() + " should throw exception \"" +
                         excMsg + "\"");
                }
            } catch (DAQCompException dce) {
                assertEquals(trans.getAction().toString() + " from " +
                             trans.getOldState() +
                             " threw unexpected exception", excMsg,
                             dce.getMessage());
            }

            assertEquals("Unexpected mid-change state for " +
                         trans.getOldState() + "->" + trans.getAction(),
                         trans.getMidState(), st.getState());

            checkFunctions(comp, trans, 0);

            Thread thread = new Thread(st);
            thread.setName("StateTask");
            thread.start();
            for (int j = 0; !st.isRunning() && j < 100; j++) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    // ignore interrupts
                }
            }
            if (!st.isRunning()) {
                fail("StateTask " + st + " was not started!");
            }

            st.stop();
            for (int j = 0; !st.isStopped() && j < 100; j++) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    // ignore interrupts
                }
            }
            if (!st.isStopped()) {
                fail("StateTask " + st + " was not stopped!");
            }

            assertEquals("Unexpected final state for " + trans.getOldState() +
                         "->" + trans.getAction(),
                         trans.getEndState(), st.getState());

            checkFunctions(comp, trans, trans.getFunctionBitmap());

            assertNoLogMessages(trans.getOldState() + "->" +
                                trans.getAction());
        }
    }
}
