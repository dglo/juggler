package icecube.daq.juggler.toybox;

import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;

import icecube.daq.juggler.component.DAQCompConfig;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.component.DAQOutputHack;

import icecube.daq.payload.ByteBufferCache;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;

import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Generator task.
 */
class GenTask
    implements Runnable
{
    /** logger */
    private static final Log LOG = LogFactory.getLog(GenTask.class);

    private String name;
    private Generator gen;
    private PayloadOutputEngine engine;
    private PayloadTransmitChannel xmitChan;
    private boolean stopping;

    /**
     * Create a generator task.
     *
     * @param gen payload generator
     * @param engine output engine
     * @param xmitChan transmit channel
     *
     * @throws DAQCompException if there is a problem with one of the params
     */
    GenTask(String name, Generator gen, PayloadOutputEngine engine,
            PayloadTransmitChannel xmitChan)
        throws DAQCompException
    {
        if (gen == null) {
            throw new DAQCompException("Generator cannot be null");
        }
        if (engine == null) {
            throw new DAQCompException("Output engine cannot be null");
        }
        if (xmitChan == null) {
            throw new DAQCompException("Transmit channel cannot be null");
        }

        this.name = name;
        this.gen = gen;
        this.engine = engine;
        this.xmitChan = xmitChan;
    }

    /**
     * Generate payloads and write them to the specified output streams.
     */
    public void run()
    {
        while (!stopping && gen.isGenerating()) {
            ByteBuffer buf = gen.generate();
            if (buf == null) {
                LOG.error("Generated NULL buffer");
            } else {
                // wait a second...
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    // ignore interrupts
                }

                final String dbgStr =
                    icecube.daq.payload.DebugDumper.toString(buf);
                LOG.error(name + ": " + dbgStr);

                xmitChan.receiveByteBuffer(buf);
            }
        }

        LOG.error(name + ": StopMsg");
        engine.sendLastAndStop();
    }

    /**
     * Stop the generator.
     */
    public void stopRun()
    {
        LOG.error("Stopping Run");
        stopping = true;
    }
}

/**
 * Hit payload generator component.
 */
public class HitSource
    extends DAQComponent
    implements DAQOutputHack
{
    /** logger */
    private static final Log LOG = LogFactory.getLog(HitSource.class);

    private PayloadOutputEngine hitSrc;
    private Generator gen;
    private PayloadTransmitChannel xmitChan;
    private GenTask task;

    /**
     * Create a hit generator.
     *
     * @param config configuration info
     * @param outputType label to use for output engine
     */
    public HitSource(DAQCompConfig config, String outputType)
    {
        this(config, 100, outputType);
    }

    /**
     * Create a hit generator.
     *
     * @param config configuration info
     * @param numHits number of hits to generate
     * @param outputType data type for output engine
     */
    public HitSource(DAQCompConfig config, int numHits, String outputType)
    {
        super(getCompName(outputType), 0);

        hitSrc = new PayloadOutputEngine("hitSrc", 0, "hits");
        registerOutputHack(this);

        IByteBufferCache bufMgr =
            new ByteBufferCache(config.getGranularity(),
                                config.getMaxCacheBytes(),
                                config.getMaxAcquireBytes(), "HitSource");
        addCache(bufMgr);

        gen = new HitGenerator(bufMgr, numHits);

        addEngine(outputType, hitSrc);
    }

    /**
     * Callback method invoked when a new transmit channel is associated
     * with the specified output engine.
     *
     * @param outEngine output engine
     * @param xmitChan new transmit channel
     *
     * @throws DAQCompException if there is a problem
     */
    public void createdTransmitChannel(PayloadOutputEngine outEngine,
                                       PayloadTransmitChannel xmitChan)
        throws DAQCompException
    {
        if (this.xmitChan != null) {
            throw new DAQCompException("Multiple transmit channels exist");
        }

        this.xmitChan = xmitChan;
    }

    /**
     * Clear cached transmit engine.
     *
     * @throws DAQCompException if the transmit channel was not properly closed
     * @throws IOException if there is a problem disconnecting
     */
    public void disconnect()
        throws DAQCompException, IOException
    {
        super.disconnect();

        final String xmitState = xmitChan.presentState();

        xmitChan = null;

        // whine if transmit channel was not closed properly
        if (!xmitState.equals(PayloadTransmitChannel.STATE_CLOSED_NAME)) {
            throw new DAQCompException("Transmit channel should be closed" +
                                       ", not " + xmitState);
        }
    }

    /**
     * Emergency stop of component.
     *
     * @throws DAQCompException if there is a problem
     */
    public void emergencyStop()
        throws DAQCompException
    {
        super.emergencyStop();

        gen.stop();
    }

    /**
     * Set the component name based on the output type.
     *
     * @param outputType output type
     *
     * @return component name
     */
    private static String getCompName(String outputType)
    {
        if (outputType == null) {
            throw new Error("Output type is null");
        }

        if (outputType.equals(DAQConnector.TYPE_TCAL_DATA)) {
            return "tcalSrc";
        }

        if (outputType.equals(DAQConnector.TYPE_SN_DATA)) {
            return "snSrc";
        }

        if (outputType.equals(DAQConnector.TYPE_MONI_DATA)) {
            return "moniSrc";
        }

        if (!outputType.equals(DAQConnector.TYPE_TEST_HIT)) {
            LOG.error("Unknown HitSource output type \"" + outputType + "\"");
        }

        return "hitSrc";
    }

    /**
     * Start engines.
     *
     * @throws DAQCompException if there is a problem
     */
    public void startEngines()
        throws DAQCompException
    {
        super.startEngines();

        gen.reset();

        task = new GenTask(getName() + "#" + getNumber(), gen, hitSrc,
                           xmitChan);

        Thread hitTask = new Thread(task);
        hitTask.setName("generateHits");
        hitTask.start();
    }

    /**
     * All I/O engines have stopped, so reset internal state.
     */
    public void stopping()
    {
        LOG.error("Stopping");
        task.stopRun();
    }
}
