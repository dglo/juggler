package icecube.daq.juggler.toybox;

import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;
import icecube.daq.io.PushPayloadReader;

import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.component.DAQOutputHack;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayloadDestinationCollection;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.VitreousBufferCache;

import icecube.daq.payload.impl.SourceID4B;

import icecube.daq.payload.splicer.Payload;

import icecube.daq.sim.GenericHit;
import icecube.daq.sim.GenericReadoutElement;
import icecube.daq.sim.GenericTriggerRequest;
import icecube.daq.sim.ReadoutDataGenerator;
import icecube.daq.sim.TriggerRequestGenerator;

import icecube.daq.trigger.IReadoutRequest;
import icecube.daq.trigger.IReadoutRequestElement;
import icecube.daq.trigger.IHitPayload;

import icecube.daq.trigger.impl.DOMID8B;
import icecube.daq.trigger.impl.ReadoutRequestPayloadFactory;
import icecube.daq.trigger.impl.ReadoutRequestPayload;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import java.util.zip.DataFormatException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Hit payload generator component.
 */
public class SHHarness
    extends DAQComponent
{
    private static final Log LOG = LogFactory.getLog(SHHarness.class);

    private static final int MAX_REQUESTS_IN_FLIGHT = 2;

    private static final DOMID8B EMPTY_DOM = new DOMID8B();

    private int hitsPerTrigger;

    private PayloadDestinationOutputEngine reqSrc;
    private IPayloadDestinationCollection reqDest;

    private PushPayloadReader hitSink;
    private PushPayloadReader rdoutSink;

    class HitSink
        extends PushPayloadReader
    {
        private MasterPayloadFactory masterFactory;
        private ArrayList hitList;
        private ReadoutRequestPayloadFactory readoutFactory;
        private int nextEvent = 1;

        /**
         * Hit reader.
         *
         * @param bufMgr byte buffer manager
         */
        HitSink(IByteBufferCache bufMgr)
            throws IOException
        {
            super("hitSink");

            masterFactory = new MasterPayloadFactory(bufMgr);

            hitList = new ArrayList();

            final int reqType = PayloadRegistry.PAYLOAD_ID_READOUT_REQUEST;
            readoutFactory = (ReadoutRequestPayloadFactory)
                masterFactory.getPayloadFactory(reqType);
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

                if (type != PayloadRegistry.PAYLOAD_ID_SIMPLE_HIT) {
                    LOG.error("Expected payload#" +
                              PayloadRegistry.PAYLOAD_ID_SIMPLE_HIT +
                              ", not #" + type);
                } else {
                    IHitPayload hit;
                    try {
                        hit = (IHitPayload) masterFactory.createPayload(0, buf);
                    } catch (DataFormatException dfe) {
                        LOG.error("Could not create hit", dfe);
                        hit = null;
                    }

                    if (hit != null) {
                        synchronized (hitList) {
                            hitList.add(hit);
                        }
                    }

                    if (hitList.size() >= hitsPerTrigger) {
                        sendRequest(nextEvent++);
                    }
                }
            }
        }

        private void sendRequest(int eventId)
        {
            long firstTime = Long.MAX_VALUE;
            long lastTime = Long.MIN_VALUE;

            IUTCTime firstUTC = null;
            IUTCTime lastUTC = null;

            for (int i = 0; i < hitsPerTrigger; i++) {
                IHitPayload hit =
                    (IHitPayload) hitList.get(i);
                long time = hit.getHitTimeUTC().getUTCTimeAsLong();
                if (time < firstTime) {
                    firstTime = time;
                    firstUTC = hit.getHitTimeUTC();
                }
                if (time > lastTime) {
                    lastTime = time;
                    lastUTC = hit.getHitTimeUTC();
                }
            }

            Iterator idIter = reqDest.getAllSourceIDs().iterator();
            while (idIter.hasNext()) {
                ISourceID srcId = (ISourceID) idIter.next();
                sendOneRequest(eventId, firstUTC, lastUTC, srcId);
            }
        }

        private void sendOneRequest(int eventId, IUTCTime firstUTC,
                                    IUTCTime lastUTC, ISourceID stringSrcId)
        {
            IReadoutRequestElement elem = (IReadoutRequestElement)
                ReadoutRequestPayloadFactory.createReadoutRequestElement
                (IReadoutRequestElement.READOUT_TYPE_II_STRING, firstUTC,
                 lastUTC, EMPTY_DOM, stringSrcId);

            Vector readoutElements = new Vector();
            readoutElements.add(elem);

            SourceID4B EB_SRC_ID =
                new SourceID4B(SourceIdRegistry.EVENTBUILDER_SOURCE_ID);

            IReadoutRequest req = (IReadoutRequest)
                ReadoutRequestPayloadFactory.createReadoutRequest
                (EB_SRC_ID, eventId, readoutElements);

            IUTCTime timeStamp = lastUTC;

            IWriteablePayload payload;
            try {
                payload = (IWriteablePayload)
                    readoutFactory.createPayload(timeStamp, req);
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Couldn't get ReadoutRequestPayload" +
                             " from IReadoutRequest", e);
                }

                payload = null;
            }

            try {
                LOG.info("Sending " +
                         icecube.daq.payload.DebugDumper.toString(payload));
                reqDest.writePayload(payload);
            } catch (IOException ioe) {
                LOG.error("Couldn't write request", ioe);
            }
        }

        /**
         * Received a stop message.
         */
        public void sendStop()
        {
            try {
                reqDest.stopAllPayloadDestinations();
            } catch (IOException ioe) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Couldn't stop request destinations", ioe);
                }
            }
        }
    }

    class ReadoutSink
        extends PushPayloadReader
    {
        private IByteBufferCache bufMgr;

        /**
         * Readout data reader.
         *
         * @param bufMgr byte buffer manager
         */
        ReadoutSink(IByteBufferCache bufMgr)
            throws IOException
        {
            super("rdoutSink");

            this.bufMgr = bufMgr;
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
            System.err.println("ReadoutSink got " +
                               icecube.daq.payload.DebugDumper.toString(buf));
        }

        /**
         * Received a stop message.
         */
        public void sendStop()
        {
            // ignore
        }
    }

    /**
     * Create a string hub trigger generator.
     *
     * @param hitsPerTrigger number of hits per trigger
     */
    public SHHarness(int hitsPerTrigger)
    {
        super("shHarness", 0);

        this.hitsPerTrigger = hitsPerTrigger;

        IByteBufferCache dataBufMgr = new VitreousBufferCache();
        addCache(DAQConnector.TYPE_READOUT_REQUEST, dataBufMgr);

        IByteBufferCache genBufMgr = new VitreousBufferCache();
        addCache(genBufMgr);

        try {
            hitSink = new HitSink(genBufMgr);
        } catch (IOException ioe) {
            throw new Error("Couldn't create HitSink", ioe);
        }
        addEngine(DAQConnector.TYPE_STRING_HIT, hitSink);

        reqSrc = new PayloadDestinationOutputEngine("reqSrc", 0, "rdoutReq");
        addEngine(DAQConnector.TYPE_READOUT_REQUEST, reqSrc);

        reqSrc.registerBufferManager(dataBufMgr);
        reqDest = reqSrc.getPayloadDestinationCollection();

        try {
            rdoutSink = new ReadoutSink(dataBufMgr);
        } catch (IOException ioe) {
            throw new Error("Couldn't create ReadoutSink", ioe);
        }
        addEngine(DAQConnector.TYPE_READOUT_DATA, rdoutSink);
    }
}
