package icecube.daq.juggler.toybox;

import icecube.daq.payload.IByteBufferCache;

import java.nio.ByteBuffer;

/**
 * Hit payload generator.
 */
class HitGenerator
    implements Generator
{
    private static final int SIMPLE_HIT = 1;
    private static final int TRIGGER_TYPE = 1;
    private static final int CONFIG_ID = 1;
    private static final int SRC_ID = 1;
    private static final int DOM_ID = 1;
    private static final short TRIG_MODE = 1;

    private IByteBufferCache bufMgr;
    private int numHits;
    private int timeInc;
    private long curTime;

    private int numGenerated;

    /**
     * Create a hit generator.
     *
     * @param bufMgr ByteBuffer manager
     * @param numHits number of hits to generate
     */
    HitGenerator(IByteBufferCache bufMgr, int numHits)
    {
        this(bufMgr, numHits, 1);
    }

    /**
     * Create a hit generator.
     *
     * @param bufMgr ByteBuffer manager
     * @param numHits number of hits to generate
     * @param timeInc number of ticks to increase after each hit
     */
    HitGenerator(IByteBufferCache bufMgr, int numHits, int timeInc)
    {
        this.bufMgr = bufMgr;
        this.numHits = numHits;
        this.timeInc = timeInc;
    }

    /**
     * Generate a hit payload.
     *
     * @return hit payload
     */
    public ByteBuffer generate()
    {
        if (numGenerated >= numHits) {
            return null;
        }

        final int len = 38;

        ByteBuffer buf = bufMgr.acquireBuffer(len);
        buf.putInt(len);
        buf.putInt(SIMPLE_HIT);
        buf.putLong(curTime);
        buf.putInt(TRIGGER_TYPE);
        buf.putInt(CONFIG_ID);
        buf.putInt(SRC_ID);
        buf.putLong(DOM_ID);
        buf.putShort(TRIG_MODE);

        buf.flip();

        curTime += timeInc;
        numGenerated++;

        return buf;
    }

    /**
     * Are hits still being generated?
     *
     * @return <tt>true</tt> if there are more hits to be generated
     */
    public boolean isGenerating()
    {
        return numGenerated < numHits;
    }

    /**
     * Reset the generator.
     */
    public void reset()
    {
        numGenerated = 0;
    }

    /**
     * Stop the generator.
     */
    public void stop()
    {
        numGenerated = numHits;
    }
}
