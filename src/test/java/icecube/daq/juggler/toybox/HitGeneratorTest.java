package icecube.daq.juggler.toybox;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.VitreousBufferCache;

import java.nio.ByteBuffer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

public class HitGeneratorTest
    extends TestCase
{
    public HitGeneratorTest(String name)
    {
        super(name);
    }

    private static final void validateHit(ByteBuffer buf, long expTime)
    {
        assertNotNull("Didn't expect null hit", buf);
        assertEquals("Bad buffer limit", 38, buf.limit());
        assertEquals("Bad buffer position", 0, buf.position());
        assertEquals("Bad payload length", 38, buf.getInt(0));
        assertEquals("Bad payload type", 1, buf.getInt(4));
        assertEquals("Bad payload time", expTime, buf.getLong(8));
    }

    /**
     * Create test suite for this class.
     *
     * @return the suite of tests declared in this class.
     */
    public static Test suite()
    {
        return new TestSuite(HitGeneratorTest.class);
    }

    public void testBasic()
    {
        IByteBufferCache bufCache = new VitreousBufferCache();

        final int expNum = 10;

        HitGenerator gen = new HitGenerator(bufCache, expNum);

        long prevTime = 0L;

        int actNum;

        // try one run
        for (actNum = 0; gen.isGenerating(); actNum++) {
            ByteBuffer buf = gen.generate();
            validateHit(buf, prevTime++);

            bufCache.returnBuffer(buf);
        }

        // make sure we generated the expected number of hits
        assertEquals("Incorrect number of hits", expNum, actNum);

        // reset and try again
        gen.reset();
        for (actNum = 0; gen.isGenerating(); actNum++) {
            ByteBuffer buf = gen.generate();
            validateHit(buf, prevTime++);

            bufCache.returnBuffer(buf);
        }

        // make sure we generated the expected number of hits
        assertEquals("Incorrect number of hits", expNum, actNum);
    }

    public void testStop()
    {
        IByteBufferCache bufCache = new VitreousBufferCache();

        final int stopNum = 10;

        HitGenerator gen = new HitGenerator(bufCache, stopNum + 10);

        long prevTime = 0L;

        int actNum;

        // try one run
        for (actNum = 0; gen.isGenerating(); actNum++) {
            ByteBuffer buf = gen.generate();
            validateHit(buf, prevTime++);

            bufCache.returnBuffer(buf);

            if (actNum == stopNum) {
                gen.stop();
            }
        }

        // make sure we generated the expected number of hits
        assertEquals("Incorrect number of hits", stopNum + 1, actNum);
    }

    public void testThreeTicks()
    {
        IByteBufferCache bufCache = new VitreousBufferCache();

        final int expNum = 10;
        final int timeInc = 3;

        HitGenerator gen = new HitGenerator(bufCache, expNum, timeInc);

        long prevTime = 0L;

        int actNum;

        // try one run
        for (actNum = 0; gen.isGenerating(); actNum++) {
            ByteBuffer buf = gen.generate();
            validateHit(buf, prevTime);

            prevTime += timeInc;

            bufCache.returnBuffer(buf);
        }
    }

    /**
     * Main routine which runs text test in standalone mode.
     *
     * @param args the arguments with which to execute this method.
     */
    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
