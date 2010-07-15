package icecube.daq.juggler.component;

import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;

/**
 * Generic DAQ connector.
 */
public abstract class DAQConnector
{
    /** Event connector. */
    public static final String TYPE_EVENT = "event";
    /** Global trigger connector. */
    public static final String TYPE_GLOBAL_TRIGGER = "glblTrig";
    /** icetopHub-&gt;icetopTrigger hit connector. */
    public static final String TYPE_ICETOP_HIT = "icetopHit";
    /** Moni data connector. */
    public static final String TYPE_MONI_DATA = "moniData";
    /** Readout data connector. */
    public static final String TYPE_READOUT_DATA = "rdoutData";
    /** Readout request connector. */
    public static final String TYPE_READOUT_REQUEST = "rdoutReq";
    /** SN data connector. */
    public static final String TYPE_SN_DATA = "snData";
    /** stringHub-&gt;iniceTrigger hit connector. */
    public static final String TYPE_STRING_HIT = "stringHit";
    /** Tcal data connector. */
    public static final String TYPE_TCAL_DATA = "tcalData";
    /** Test hit connector. */
    public static final String TYPE_TEST_HIT = "testHit";
    /** General payload data connector. */
    public static final String TYPE_TEST_DATA = "testData";
    /** stringHub-&gt;trackEngine hit connector. */
    public static final String TYPE_TRACKENG_HIT = "trackEngineHit";
    /** icetopTrigger/iniceTrigger-&gt;globalTrigger trigger connector. */
    public static final String TYPE_TRIGGER = "trigger";
    /** self-contained connector which doesn't need any external connections */
    public static final String TYPE_SELF_CONTAINED = "selfContained";

    /** General-purpose byte buffer cache. */
    public static final String TYPE_GENERIC_CACHE = "genericCache";

    private String type;
    private boolean optional;

    /**
     * Create a DAQ connector.
     *
     * @param type connector type
     * @param optional <tt>true</tt> if this is an optional connector
     */
    public DAQConnector(String type, boolean optional)
    {
        this.type = type;
        this.optional = optional;
    }

    /**
     * Destroy this connector.
     *
     * @throws Exception if there was a problem
     */
    public abstract void destroy()
        throws Exception;

    /**
     * Force engine to stop processing data.
     *
     * @throws Exception if there is a problem
     */
    public abstract void forcedStopProcessing()
        throws Exception;

    /**
     * Get description character.
     *
     * @return one of <tt>I</tt> (optional input), <tt>i</tt> (required input),
     *         <tt>O</tt> (optional output), or <tt>o</tt> (required output).
     */
    public char getDescriptionChar()
    {
        if (isInput()) {
            return optional ? 'I' : 'i';
        } else if (isOutput()) {
            return optional ? 'O' : 'o';
        }

        return '?';
    }

    /**
     * Get connector port.
     *
     * @return <tt>0</tt>
     */
    public int getPort()
    {
        return 0;
    }

    /**
     * Get current engine/splicer state.
     *
     * @return state string
     */
    public abstract String getState();

    /**
     * Get connector type.
     *
     * @return type
     */
    public String getType()
    {
        return type;
    }

    /**
     * Is this an input engine?
     *
     * @return <tt>false</tt>
     */
    public boolean isInput()
    {
        return false;
    }

    /**
     * Is this an output engine?
     *
     * @return <tt>false</tt>
     */
    public boolean isOutput()
    {
        return false;
    }

    /**
     * Is this connector running?
     *
     * @return <tt>true</tt> if this connector is running
     */
    public abstract boolean isRunning();

    /**
     * Is this a splicer?
     *
     * @return <tt>false</tt>
     */
    public boolean isSplicer()
    {
        return false;
    }

    /**
     * Is this connector stopped?
     *
     * @return <tt>true</tt> if this connector is stopped
     */
    public abstract boolean isStopped();

    /**
     * Start background threads.
     *
     * @throws Exception if there is a problem
     */
    public abstract void start()
        throws Exception;

    /**
     * Start processing data.
     *
     * @throws Exception if there is a problem
     */
    public abstract void startProcessing()
        throws Exception;

    /**
     * Start server socket for this engine.
     *
     * @param bufCache buffer cache manager
     *
     * @throws IOException if there is a problem
     */
    public void startServer(IByteBufferCache bufCache)
        throws IOException
    {
        throw new IOException("No server to start");
    }
}
