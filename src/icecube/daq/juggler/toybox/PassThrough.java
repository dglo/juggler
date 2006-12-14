package icecube.daq.juggler.toybox;

import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;
import icecube.daq.io.PushPayloadInputEngine;

import icecube.daq.juggler.component.DAQCompConfig;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.component.DAQOutputHack;

import icecube.daq.payload.ByteBufferCache;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;

import java.nio.ByteBuffer;

/**
 * Payload pass-through component.
 */
public class PassThrough
    extends DAQComponent
    implements DAQOutputHack
{
    /**
     * Input engine.
     */
    class PassEngine
        extends PushPayloadInputEngine
    {
        private IByteBufferCache bufMgr;

        PassEngine(String name, int id, String fcn, String prefix,
                   IByteBufferCache bufMgr)
        {
            super(name, id, fcn, prefix, bufMgr);

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
                System.err.println("Expected 38 bytes, not " + len);
            } else {
                final long time = buf.getLong(8);
                System.out.println("Passing time " + time);
                xmitChan.receiveByteBuffer(buf);
            }
        }

        /**
         * Received a stop message.
         */
        public void sendStop()
        {
            if (xmitChan != null) {
                ByteBuffer stopBuf = bufMgr.acquireBuffer(4);
                stopBuf.putInt(4);
                stopBuf.flip();
                xmitChan.receiveByteBuffer(stopBuf);
            }
        }
    }

    /** input engine. */
    private PushPayloadInputEngine passIn;
    /** output engine. */
    private PayloadOutputEngine passOut;

    /** receive channel */
    private PayloadTransmitChannel xmitChan;

    /**
     * Create a hit generator.
     *
     * @param config configuration info
     * @param inputType data type used for input engine
     * @param outputType data type used for output engine
     */
    public PassThrough(DAQCompConfig config, String inputType,
                       String outputType)
    {
        super("passThru", 0);

        IByteBufferCache bufMgr =
            new ByteBufferCache(config.getGranularity(),
                                config.getMaxCacheBytes(),
                                config.getMaxAcquireBytes(), "PassThrough");
        addCache(bufMgr);

        passIn = new PassEngine("passIn", 0, "hits", "pass", bufMgr);
        passOut = new PayloadOutputEngine("passOut", 0, "data");
        registerOutputHack(this);

        addEngine(inputType, passIn);
        addEngine(outputType, passOut);
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
}
