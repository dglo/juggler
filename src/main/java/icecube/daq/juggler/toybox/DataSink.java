package icecube.daq.juggler.toybox;

import icecube.daq.io.PushPayloadInputEngine;

import icecube.daq.juggler.component.DAQCompConfig;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;

import icecube.daq.payload.ByteBufferCache;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;

import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Data payload reader component.
 */
public class DataSink
    extends DAQComponent
{
    /** logger */
    private static final Log LOG = LogFactory.getLog(DataSink.class);

    /**
     * Input engine.
     */
    class SinkEngine
        extends PushPayloadInputEngine
    {
        /** engine name */
        private String name;
        /** byte buffer cache */
        private IByteBufferCache bufMgr;

        SinkEngine(String name, int id, String fcn, String prefix,
                   IByteBufferCache bufMgr)
        {
            super(name, id, fcn, prefix, bufMgr);

            this.name = name + "#" + id;
            this.bufMgr = bufMgr;
        }

        /**
         * Process byte buffer.
         *
         * @param buf newly-read byte buffer
         *
         * @throws IOException if ther eis a problem
         */
        public void pushBuffer(ByteBuffer buf)
            throws IOException
        {
            final int len = buf.getInt(0);
            if (len != 38) {
                LOG.error(name + ": Expected 38 bytes, not " + len);
            } else {
                final String dbgStr =
                    icecube.daq.payload.DebugDumper.toString(buf);
                LOG.error(name + ": " + dbgStr);
            }

            bufMgr.returnBuffer(buf);
        }

        /**
         * Received a stop message.
         */
        public void sendStop()
        {
            LOG.error(name + ": StopMsg");
            // do nothing
        }
    }

    /** input engine. */
    private PushPayloadInputEngine dataSink;

    /**
     * Create a data payload reader.
     *
     * @param config configuration info
     * @param inputType data type used for input engine
     */
    public DataSink(DAQCompConfig config, String inputType)
    {
        super(getCompName(inputType), 0);

        IByteBufferCache bufMgr =
            new ByteBufferCache(config.getGranularity(),
                                config.getMaxCacheBytes(),
                                config.getMaxAcquireBytes(), "DataSink");
        addCache(bufMgr);

        dataSink = new SinkEngine("dataSink", 0, "data", "DS0", bufMgr);

        addEngine(inputType, dataSink);
    }

    /**
     * Set the component name based on the input type.
     *
     * @param inputType input type
     *
     * @return component name
     */
    private static String getCompName(String inputType)
    {
        if (inputType == null) {
            throw new Error("Input type is null");
        }

        if (inputType.equals(DAQConnector.TYPE_TCAL_DATA)) {
            return "tcalSink";
        }

        if (inputType.equals(DAQConnector.TYPE_SN_DATA)) {
            return "snSink";
        }

        if (inputType.equals(DAQConnector.TYPE_MONI_DATA)) {
            return "moniSink";
        }

        if (inputType.equals(DAQConnector.TYPE_TEST_DATA)) {
            return "dataSink";
        }

        if (inputType.equals(DAQConnector.TYPE_TRIGGER)) {
            return "trigSink";
        }

        if (!inputType.equals(DAQConnector.TYPE_TEST_HIT)) {
            LOG.error("Unknown DataSink input type \"" + inputType + "\"");
        }

        return "hitSink";
    }
}
