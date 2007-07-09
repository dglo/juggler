package icecube.daq.juggler.toybox;

import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;
import icecube.daq.io.PushPayloadReader;

import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.component.DAQOutputHack;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.VitreousBufferCache;

import icecube.daq.sim.GenericHit;
import icecube.daq.sim.GenericReadoutElement;
import icecube.daq.sim.GenericTriggerRequest;
import icecube.daq.sim.ReadoutDataGenerator;
import icecube.daq.sim.TriggerRequestGenerator;

import icecube.daq.trigger.IReadoutRequestElement;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Hit payload generator component.
 */
public class EBHarness
    extends DAQComponent
    implements DAQOutputHack
{
    private static final Log LOG = LogFactory.getLog(EBHarness.class);

    private static final int MAX_REQUESTS_IN_FLIGHT = 2;

    private int numHits;
    private int hitsPerTrigger;
    private int timeInc;

    private boolean stopThread;
    private int numRequestsInFlight;
    private ArrayList hitList = new ArrayList();

    private int nextTrigReqUID = 1;
    private int timeOffset;

    private PayloadOutputEngine trigSrc;
    private PayloadOutputEngine rdoutSrc;
    private PushPayloadReader reqSink;
    private PayloadTransmitChannel trigChan;
    private PayloadTransmitChannel rdoutChan;

    /**
     * Generator task.
     */
    class EBGenTask
        implements Runnable
    {
        private TriggerRequestGenerator trigGen;

        /**
         * Create a generator task.
         *
         * @throws DAQCompException if there is a problem with one of the params
         */
        EBGenTask()
            throws DAQCompException
        {
            if (trigGen == null) {
                trigGen = new TriggerRequestGenerator();
            }
        }

        private void sendTriggerRequest()
        {
            long firstTime = Long.MAX_VALUE;
            long lastTime = Long.MIN_VALUE;

            synchronized (hitList) {
                for (int i = hitsPerTrigger - 1; i >= 0; i--) {
                    GenericHit hit =
                        (GenericHit) hitList.get(hitList.size() - (i + 1));

                    if (hit.getTimeStamp() < firstTime) {
                        firstTime = hit.getTimeStamp();
                    }
                    if (hit.getTimeStamp() > lastTime) {
                        lastTime = hit.getTimeStamp();
                    }
                }
            }

            final int globalRequest =
                IReadoutRequestElement.READOUT_TYPE_GLOBAL;

            GenericReadoutElement elem = new GenericReadoutElement();
            elem.setReadoutType(globalRequest);
            elem.setSourceId(-1);
            elem.setDomId(-1);
            elem.setFirstTime(firstTime);
            elem.setLastTime(lastTime);

            final int type = -1;
            final int configId = -1;

            GenericTriggerRequest trigReq = new GenericTriggerRequest();
            trigReq.setTriggerUID(nextTrigReqUID++);
            trigReq.setTriggerType(type);
            trigReq.setTriggerConfigId(configId);
            trigReq.setSourceId(SourceIdRegistry.GLOBAL_TRIGGER_SOURCE_ID);
            trigReq.setFirstTime(firstTime);
            trigReq.setLastTime(lastTime);
            trigReq.addReadoutElement(elem);

            synchronized (hitList) {
                while (numRequestsInFlight >= MAX_REQUESTS_IN_FLIGHT) {
                    try {
                        hitList.wait();
                    } catch (InterruptedException ie) {
                        // ignore interrupts
                    }
                }
                numRequestsInFlight++;

                ByteBuffer trigBuf = trigGen.generatePayload(trigReq);
                trigChan.receiveByteBuffer(trigBuf);
            }
        }

        /**
         * Generate payloads and write them to the specified output streams.
         */
        public void run()
        {
            int numQueued = 0;

            final long baseTime = 0L;
            final int srcId = 1001;
            final int trigMode = 1;

            for (int n = 0; !stopThread && n < numHits; n++) {
                final long timeStamp = baseTime + timeOffset;
                final int domId = (n % 8) + 1;

                GenericHit hit =
                    new GenericHit(timeStamp, domId, srcId, trigMode);
                synchronized (hitList) {
                    hitList.add(hit);
                }

                numQueued++;
                timeOffset += timeInc;

                if (numQueued == hitsPerTrigger) {
                    sendTriggerRequest();
                    numQueued = 0;
                }
            }

            trigSrc.sendLastAndStop();
        }
    }

    /**
     * Readout request reader.
     *
     * @param rdoutOut readout output engine
     * @param rdoutChan readout transmit channel
     */
    class RequestSink
        extends PushPayloadReader
    {
        private IByteBufferCache bufMgr;
        private ReadoutDataGenerator dataGen;

        RequestSink(String name, int id, IByteBufferCache bufMgr)
            throws IOException
        {
            super(name + "#" + id);

            this.bufMgr = bufMgr;

            dataGen = new ReadoutDataGenerator();
        }

        /**
         * Process byte buffer.
         *
         * @param buf newly-read byte buffer
         *
         * @throws IOException if there is a problem
         */
        public void pushBuffer(ByteBuffer buf)
            throws IOException
        {
            final int len = buf.getInt(0);
            if (len == 4) {
                LOG.error("Saw unexpected STOP");
            } else {
                final int type = buf.getInt(4);

                if (type != PayloadRegistry.PAYLOAD_ID_READOUT_REQUEST) {
                    LOG.error("Expected payload#" +
                              PayloadRegistry.PAYLOAD_ID_READOUT_REQUEST +
                              ", not #" + type);
                } else {
                    //final int recType = buf.getShort(16);
                    final int uid = buf.getInt(18);
                    final int srcId = buf.getInt(22);
                    final int numElems = buf.getInt(26);

                    if (numElems != 1) {
                        LOG.error("Expected 1 element, not " + numElems);
                    } else {
                        final int reqType = buf.getInt(30);
                        //final int trigSrcId = buf.getInt(34);
                        final long firstTime = buf.getLong(38);
                        final long lastTime = buf.getLong(46);
                        //final long domId = buf.getLong(54);

                        ArrayList reqHits = new ArrayList();
                        synchronized (hitList) {
                            Iterator iter = hitList.iterator();
                            while (iter.hasNext()) {
                                GenericHit hit = (GenericHit) iter.next();
                                if (hit.getTimeStamp() >= firstTime &&
                                    hit.getTimeStamp() <= lastTime)
                                {
                                    reqHits.add(hit);
                                    iter.remove();
                                }
                            }

                            numRequestsInFlight--;
                            hitList.notify();
                        }

                        ByteBuffer dataBuf =
                            dataGen.generatePayload((short) reqType, uid,
                                                    (short) 1, (short) 1,
                                                    srcId, firstTime, lastTime,
                                                    reqHits);
                        rdoutChan.receiveByteBuffer(dataBuf);
                    }
                }
            }

            bufMgr.returnBuffer(buf);
        }

        /**
         * Received a stop message.
         */
        public void sendStop()
        {
            rdoutSrc.sendLastAndStop();
        }
    }

    /**
     * Create an event builder trigger and readout data generator.
     *
     * @param numHits number of hits to generate
     * @param hitsPerTrigger number of hits per trigger
     */
    public EBHarness(int numHits, int hitsPerTrigger)
    {
        super("ebHarness", 0);

        this.numHits = numHits;
        this.hitsPerTrigger = hitsPerTrigger;
        this.timeInc = 3;

        registerOutputHack(this);

        IByteBufferCache dataBufMgr = new VitreousBufferCache();
        addCache(DAQConnector.TYPE_READOUT_DATA, dataBufMgr);

        IByteBufferCache genBufMgr = new VitreousBufferCache();
        addCache(genBufMgr);

        trigSrc = new PayloadOutputEngine("trigSrc", 0, "glblTrig");
        addEngine(DAQConnector.TYPE_GLOBAL_TRIGGER, trigSrc);

        rdoutSrc = new PayloadOutputEngine("rdoutSrc", 0, "rdoutData");
        addEngine(DAQConnector.TYPE_READOUT_DATA, rdoutSrc);

        try {
            reqSink = new RequestSink("reqSink", 0, genBufMgr);
        } catch (IOException ioe) {
            throw new Error("Couldn't create RequestSink", ioe);
        }
        addEngine(DAQConnector.TYPE_READOUT_REQUEST, reqSink);
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
        if (outEngine == trigSrc) {
            if (trigChan != null) {
                throw new DAQCompException("Multiple trigger channels exist");
            }

            trigChan = xmitChan;
        } else if (outEngine == rdoutSrc) {
            if (rdoutChan != null) {
                throw new DAQCompException("Multiple readout channels exist");
            }

            rdoutChan = xmitChan;
        } else {
            throw new DAQCompException("Unknown output engine " + outEngine);
        }
    }

    /**
     * Clear cached transmit engine.
     *
     * @throws DAQCompException if the transmit channel was not properly closed
     */
    public void disconnected()
        throws DAQCompException
    {
        final String trigState = trigChan.presentState();
        final String rdoutState = rdoutChan.presentState();

        trigChan = null;
        rdoutChan = null;

        // whine if transmit channel was not closed properly
        if (!trigState.equals(PayloadTransmitChannel.STATE_CLOSED_NAME)) {
            throw new DAQCompException("Trigger channel should be closed" +
                                       ", not " + trigState);
        }
        if (!rdoutState.equals(PayloadTransmitChannel.STATE_CLOSED_NAME)) {
            throw new DAQCompException("Readout channel should be closed" +
                                       ", not " + rdoutState);
        }
    }

    /**
     * Start background thread.
     *
     * @throws DAQCompException if there is a problem
     */
    public void started()
        throws DAQCompException
    {
        stopThread = false;

        Thread task = new Thread(new EBGenTask());
        task.setName("generateEBData");
        task.start();
    }

    /**
     * All I/O engines have stopped, so reset internal state.
     */
    public void stopping()
    {
        stopThread = true;
    }
}
