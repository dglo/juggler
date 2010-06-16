package icecube.daq.juggler.component;

import icecube.daq.payload.IByteBufferCache;

public interface IComponent
{
    /**
     * Configure a component using the specified configuration name.
     *
     * @param configName configuration name
     *
     * @throws DAQCompException if there is a problem configuring
     */
    void configuring(String configName)
        throws DAQCompException;

    /**
     * Perform any actions after all output connectors have been disconnected.
     *
     * @throws DAQCompException if there is a problem
     */
    void disconnected()
        throws DAQCompException;

    /**
     * Flush buffer caches
     */
    void flushCaches();

    /**
     * Get the buffer cache for the specified data type.
     *
     * @param type data type of buffer cache
     *
     * @return byte buffer cache
     *
     * @throws DAQCompException if the cache could not be found
     */
    IByteBufferCache getByteBufferCache(String type)
        throws DAQCompException;

    /**
     * Get component name (and ID, for non-hub components.)
     *
     * @return full name
     */
    String getFullName();

    /**
     * Are all connectors stopped?
     *
     * @return <tt>true</tt> if all connectors have stopped
     */
    boolean isStopped();

    /**
     * Return an iterator for this component's I/O connections.
     *
     * @return connection iterator
     */
    Iterable<DAQConnector> listConnectors();

    /**
     * Reset internal state after a component has moved to idle.
     *
     * @throws DAQCompException if there is a problem resetting
     */
    void resetting()
        throws DAQCompException;

    /**
     * Set the run number inside this component.
     *
     * @param runNumber run number
     */
    void setRunNumber(int num);

    /**
     * Start connectors.
     *
     * @throws DAQCompException if there is a problem
     */
    void startEngines()
        throws DAQCompException;

    /**
     * Perform any actions which should happen just after a run is started.
     *
     * @throws DAQCompException if there is a problem starting the component
     */
    void started()
        throws DAQCompException;

    /**
     * Perform any actions which should happen just before a run is started.
     *
     * @throws DAQCompException if there is a problem starting the component
     */
    void starting()
        throws DAQCompException;

    /**
     * Stop the MBean agent associated with this component.
     *
     * @throws DAQCompException if MBean agent was not stopped
     */
    void stopMBeanAgent()
        throws DAQCompException;

    /**
     * Stop the state task associated with this component.
     */
    void stopStateTask();

    /**
     * Perform any clean-up after all connectors have stopped.
     *
     * @throws DAQCompException if there is a problem stopping the component
     */
    void stopped()
        throws DAQCompException;

    /**
     * Perform any clean-up before connectors are stopped.
     *
     * @throws DAQCompException if there is a problem stopping the component
     */
    void stopping()
        throws DAQCompException;
}
