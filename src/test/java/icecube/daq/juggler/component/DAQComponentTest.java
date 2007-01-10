package icecube.daq.juggler.component;

import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;

import java.nio.ByteBuffer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

class MockCache
    implements IByteBufferCache
{
    private String type;

    MockCache(String type)
    {
        this.type = type;
    }

    String getType()
    {
        return type;
    }

    public ByteBuffer acquireBuffer(int i0)
    {
        throw new Error("Unimplemented");
    }

    public void destinationClosed()
    {
        throw new Error("Unimplemented");
    }

    public void flush()
    {
        throw new Error("Unimplemented");
    }

    public int getCurrentAquiredBuffers()
    {
        throw new Error("Unimplemented");
    }

    public long getCurrentAquiredBytes()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersAcquired()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersCreated()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersReturned()
    {
        throw new Error("Unimplemented");
    }

    public long getTotalBytesInCache()
    {
        throw new Error("Unimplemented");
    }

    public boolean isBalanced()
    {
        throw new Error("Unimplemented");
    }

    public void receiveByteBuffer(ByteBuffer x0)
    {
        throw new Error("Unimplemented");
    }

    public void returnBuffer(ByteBuffer x0)
    {
        throw new Error("Unimplemented");
    }
}

class MiniComponent
    extends DAQComponent
{
    MiniComponent(String name, int num)
    {
        super(name, num);
    }
}

class MockComponent
    extends DAQComponent
{
    boolean calledConfiguring;
    boolean calledDisconnected;
    boolean calledStarted;
    boolean calledStarting;
    boolean calledStopped;

    MockComponent(String name, int num)
    {
        super(name, num);
    }

    void clearFlags()
    {
        calledConfiguring = false;
        calledDisconnected = false;
        calledStarted = false;
        calledStarting = false;
        calledStopped = false;
    }

    public void configuring(String name)
    {
        calledConfiguring = true;
    }

    public void disconnected()
    {
        calledDisconnected = true;
    }

    public void started()
    {
        calledStarted = true;
    }

    public void starting()
    {
        calledStarting = true;
    }

    public void stopped()
    {
        calledStopped = true;
    }

    boolean wasConfiguringCalled()
    {
        return calledConfiguring;
    }

    boolean wasDisconnectedCalled()
    {
        return calledDisconnected;
    }

    boolean wasStartedCalled()
    {
        return calledStarted;
    }

    boolean wasStoppedCalled()
    {
        return calledStopped;
    }

    boolean wasStartingCalled()
    {
        return calledStarting;
    }
}

public class DAQComponentTest
    extends TestCase
{
    private DAQComponent testComp;

    public static Test suite()
    {
        return new TestSuite(DAQComponentTest.class);
    }

    protected void tearDown()
    {
        if (testComp != null) {
            try {
                testComp.destroy();
            } catch (Throwable thr) {
                // ignore teardown errors
            }
        }
    }

    public void testInitial()
    {
        String[] names = new String[] { "one", "two", "bar", "foo" };

        for (int i = 0; i < names.length; i++) {
            DAQComponent comp = new MiniComponent(names[i], i);
            assertEquals("Bad name", names[i], comp.getName());
            assertEquals("Bad number", i, comp.getNumber());
            assertEquals("Bad state", DAQComponent.STATE_IDLE, comp.getState());
        }
    }

    public void testId()
    {
        testComp = new MiniComponent("tst", 0);

        final int id = 5432;

        testComp.setId(id);
        assertEquals("Bad ID", id, testComp.getId());
    }

    public void testAddCache()
        throws DAQCompException
    {
        testComp = new MiniComponent("tst", 0);

        String[] types = new String[] { "one", "two" };

        for (int i = 0; i < types.length; i++) {
            MockCache cache = new MockCache(types[i]);
            testComp.addCache(types[i], cache);
        }

        // currently allowed to overwrite cache entries
        for (int i = 0; i < types.length; i++) {
            MockCache cache = new MockCache(types[i]);
            testComp.addCache(types[i], cache);
        }

        for (int i = 0; i < types.length; i++) {
            MockCache cache =
                (MockCache) testComp.getByteBufferCache(types[i]);
            assertEquals("Wrong cache returned", cache.getType(), types[i]);
        }

        try {
            testComp.getByteBufferCache("BOGUS");
            fail("BOGUS cache request should fail without generic cache");
        } catch (DAQCompException dce) {
            //expect failure
        }

        MockCache genCache = new MockCache("generic");
        testComp.addCache(genCache);

        assertEquals("BOGUS request didn't return generic cache",
                     genCache, testComp.getByteBufferCache("BOGUS"));
    }

    public void testSimpleConnect()
        throws DAQCompException, IOException
    {
        testComp = new MockComponent("tst", 0);
        assertEquals("Bad state", DAQComponent.STATE_IDLE, testComp.getState());

        testComp.connect();
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, testComp.getState());
    }

    public void testBadConnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        mockComp.connect();
        mockComp.configure("foo");

        try {
            mockComp.connect();
            fail("Connect after configure should not succeed");
        } catch (DAQCompException dce) {
            // expect this
        }
    }

    public void testSimpleDisconnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        mockComp.connect();
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.disconnect();
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        assertTrue("disconnected() was not called",
                   mockComp.wasDisconnectedCalled());
    }

    public void testBadConfigure()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        try {
            mockComp.configure("foo");
            fail("Configure before connect should not succeed");
        } catch (DAQCompException dce) {
            // expect this
        }

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        mockComp.configure("xxx");

        try {
            mockComp.configure("abc");
        } catch (DAQCompException dce) {
            fail("Should be able to configure a configured component");
        }

        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());

        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());

        try {
            mockComp.configure("abc");
            fail("Shouldn't be able to configure running component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        mockComp.stopRun();
        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());

        try {
            mockComp.configure("abc");
        } catch (DAQCompException dce) {
            fail("Should be able to configure stopped component");
        }
    }

    public void testConfigure()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.configure("foo");
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
    }

    public void testBadStart()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        try {
            mockComp.startRun(123);
            fail("Shouldn't be able to start newly created component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        try {
            mockComp.startRun(123);
            fail("Shouldn't be able to start unconfigured component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.configure("xxx");
        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());

        try {
            mockComp.startRun(2);
            fail("Shouldn't be able to start running component");
        } catch (DAQCompException dce) {
            // expect failure
        }
    }

    public void testBadStop()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        try {
            mockComp.stopRun();
            fail("Shouldn't be able to top newly created component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        try {
            mockComp.stopRun();
            fail("Shouldn't be able to stop unconfigured component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.configure("xxx");

        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());

        // stopRun should succeed even if run has not been started
        mockComp.stopRun();

        assertEquals("Bad state after premature stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());

        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
    }

    public void testBadDisconnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        // should be able to diconnect idle component
        mockComp.disconnect();

        mockComp.connect();
        mockComp.configure("xxx");
        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());

        try {
            mockComp.disconnect();
            fail("Shouldn't be able to diconnect running component");
        } catch (DAQCompException dce) {
            // expect failure
        }
    }

    public void testMiniRunStop()
        throws DAQCompException, IOException
    {
        testComp = new MiniComponent("tst", 0);
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, testComp.getState());

        testComp.setGlobalConfigurationDir("bogus");

        testComp.connect();
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());

        testComp.configure("foo");
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());

        testComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());

        testComp.stopRun();
        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());

        testComp.reset();
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());
    }

    public void testRunStop()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.setGlobalConfigurationDir("bogus");

        mockComp.connect();
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.configure("foo");
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());

        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());

        mockComp.stopRun();
        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("stopped() was not called",
                   mockComp.wasStoppedCalled());

        mockComp.reset();
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testRunReset()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.setGlobalConfigurationDir("bogus");

        mockComp.start();

        mockComp.connect();
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.configure("foo");
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());

        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());

        mockComp.reset();
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
