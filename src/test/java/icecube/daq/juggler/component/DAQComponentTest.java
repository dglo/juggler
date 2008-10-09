package icecube.daq.juggler.component;

import icecube.daq.io.PayloadTransmitChannel;
import icecube.daq.juggler.test.LoggingCase;
import icecube.daq.juggler.test.MockCache;
import icecube.daq.juggler.test.MockInputEngine;
import icecube.daq.juggler.test.MockOutputEngine;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;

import junit.framework.Test;
import junit.framework.TestSuite;

class BadOutputEngine
    extends MockOutputEngine
{
    private IOException connEx;
    private RuntimeException startEx;
    private RuntimeException forcedEx;
    private RuntimeException destroyEx;

    BadOutputEngine()
    {
        super();
    }

    public PayloadTransmitChannel connect(IByteBufferCache cache,
                                          WritableByteChannel chan, int srcId)
        throws IOException
    {
        if (connEx != null) {
            IOException tmpEx = connEx;
            connEx = null;
            throw tmpEx;
        }

        return super.connect(cache, chan,srcId);
    }

    public void destroyProcessor()
    {
        if (destroyEx != null) {
            RuntimeException tmpEx = destroyEx;
            destroyEx = null;
            throw tmpEx;
        }

        super.destroyProcessor();
    }

    public void forcedStopProcessing()
    {
        if (forcedEx != null) {
            RuntimeException tmpEx = forcedEx;
            forcedEx = null;
            throw tmpEx;
        }

        super.forcedStopProcessing();
    }

    public void startProcessing()
    {
        if (startEx != null) {
            RuntimeException tmpEx = startEx;
            startEx = null;
            throw tmpEx;
        }

        super.startProcessing();
    }

    void setConnectException(IOException ex)
    {
        connEx = ex;
    }

    void setDestroyProcessorException(RuntimeException ex)
    {
        destroyEx = ex;
    }

    void setForcedStopException(RuntimeException ex)
    {
        forcedEx = ex;
    }

    void setStartProcessingException(RuntimeException ex)
    {
        startEx = ex;
    }
}

class MiniComponent
    extends DAQComponent
{
    MiniComponent(String name, int num)
    {
        super(name, num);
    }

    public String getVersionInfo()
    {
        return "$Id$";
    }
}

public class DAQComponentTest
    extends LoggingCase
{
    private DAQComponent testComp;

    public DAQComponentTest(String name)
    {
        super(name);
    }

    public static Test suite()
    {
        return new TestSuite(DAQComponentTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        if (testComp != null) {
            try {
                testComp.destroy();
                testComp.waitForStateChange(DAQComponent.STATE_DESTROYING);
            } catch (Throwable thr) {
                // ignore teardown errors
            }

            if (testComp.getState() != DAQComponent.STATE_DESTROYED) {
                System.err.println("Could not destroy component!");
            }
        }

        super.tearDown();
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

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        // currently allowed to overwrite cache entries
        for (int i = 0; i < types.length; i++) {
            MockCache cache = new MockCache(types[i]);
            testComp.addCache(types[i], cache);

            assertEquals("Bad number of log messages",
                         1, getNumberOfMessages());
            assertEquals("Unexpected log message",
                         "Overwriting buffer cache for type \"" + types[i] +
                         "\"", getMessage(0));
            clearMessages();
        }

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        for (int i = 0; i < types.length; i++) {
            MockCache cache =
                (MockCache) testComp.getByteBufferCache(types[i]);
            assertEquals("Wrong cache returned", cache.getType(), types[i]);
        }

        try {
            testComp.getByteBufferCache("BOGUS");
            fail("BOGUS cache request should fail without generic cache");
        } catch (DAQCompException dce) {
            // expect failure
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
        testComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, testComp.getState());
        assertFalse("Unexpected error after connect", testComp.isError());
    }

    public void testBadConnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("foo");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertFalse("Unexpected error after configure", mockComp.isError());

        try {
            mockComp.connect();
            fail("Connect after configure should not succeed");
        } catch (DAQCompException dce) {
            // expect this
        }
        assertFalse("Unexpected error after bad connect", mockComp.isError());
    }

    public void testSimpleDisconnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.disconnect();
        mockComp.waitForStateChange(DAQComponent.STATE_DISCONNECTING);
        assertEquals("Bad state after disconnect",
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
        assertFalse("Unexpected error after bogus configure",
                    mockComp.isError());

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state " + mockComp.getStateString() +
                     " after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("abc");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state " + mockComp.getStateString() +
                     " after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state " + mockComp.getStateString() +
                     " after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        try {
            mockComp.configure("abc");
            fail("Shouldn't be able to configure running component");
        } catch (DAQCompException dce) {
            // expect failure
        }
        assertFalse("Unexpected error after bogus configure",
                    mockComp.isError());

        mockComp.stopRun();
        mockComp.waitForStateChange(DAQComponent.STATE_STOPPING);
        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        mockComp.configure("abc");
        assertFalse("Unexpected error after configure", mockComp.isError());
    }

    public void testConfigure()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("foo");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());
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
        assertFalse("Unexpected error after bad startRun", mockComp.isError());

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        try {
            mockComp.startRun(123);
            fail("Shouldn't be able to start unconfigured component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state after bogus startRun",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after bogus startRun",
                    mockComp.isError());

        mockComp.configure("xxx");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        try {
            mockComp.startRun(2);
            fail("Shouldn't be able to start running component");
        } catch (DAQCompException dce) {
            // expect failure
        }
        assertFalse("Unexpected error after bad startRun", mockComp.isError());
    }

    public void testBadStop()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        try {
            mockComp.stopRun();
            fail("Shouldn't be able to stop newly created component");
        } catch (DAQCompException dce) {
            // expect failure
        }
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after connect", mockComp.isError());

        try {
            mockComp.stopRun();
            fail("Shouldn't be able to stop unconfigured component");
        } catch (DAQCompException dce) {
            // expect failure
        }
        assertEquals("Bad state after bad stopRun",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after bad stopRun", mockComp.isError());

        mockComp.configure("xxx");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        // stopRun should succeed even if run has not been started
        mockComp.stopRun();
        mockComp.waitForStateChange(DAQComponent.STATE_STOPPING);
        assertEquals("Bad state after premature stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());
    }

    public void testBadDisconnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        // should be able to diconnect idle component
        mockComp.disconnect();
        mockComp.waitForStateChange(DAQComponent.STATE_DISCONNECTING);
        assertEquals("Bad state after idle disconnect",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("xxx");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        try {
            mockComp.disconnect();
            fail("Shouldn't be able to disconnect running component");
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
        testComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());
        assertFalse("Unexpected error after connect", testComp.isError());

        testComp.configure("foo");
        testComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());
        assertFalse("Unexpected error after configure", testComp.isError());

        testComp.startRun(1);
        testComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());
        assertFalse("Unexpected error after startRun", testComp.isError());

        testComp.stopRun();
        testComp.waitForStateChange(DAQComponent.STATE_STOPPING);
        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());
        assertFalse("Unexpected error after stopRun", testComp.isError());

        testComp.reset();
        testComp.waitForStateChange(DAQComponent.STATE_RESETTING);
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, testComp.getState());
        assertFalse("Bad isRunning() value", testComp.isRunning());
        assertTrue("Bad isStopped() value", testComp.isStopped());
        assertFalse("Unexpected error after reset", testComp.isError());
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
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("foo");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        mockComp.stopRun();
        mockComp.waitForStateChange(DAQComponent.STATE_STOPPING);
        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("stopped() was not called",
                   mockComp.wasStoppedCalled());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        mockComp.reset();
        testComp.waitForStateChange(DAQComponent.STATE_RESETTING);
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
        assertFalse("Unexpected error after reset", mockComp.isError());
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
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("foo");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        mockComp.reset();
        testComp.waitForStateChange(DAQComponent.STATE_RESETTING);
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
        assertFalse("Unexpected error after reset", mockComp.isError());
    }

    public void testUnconnectedInput()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertTrue("Expected error", mockComp.isError());

        assertEquals("Bad number of log messages",
                     1, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Connect failed", getMessage(0));
        clearMessages();

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testUnknownConnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        Connection[] badList = new Connection[] {
            new Connection("bleh", "unused", 0, "localhost", 123),
        };

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.connect(badList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertTrue("Expected error", mockComp.isError());

        assertEquals("Bad number of log messages",
                     1, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Connect failed", getMessage(0));
        clearMessages();

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testBadMultiConnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
            new Connection("gunk", "unused", 1, "localhost",
                           outTarget.getServerPort()),
        };

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.connect(badList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertTrue("Expected error", mockComp.isError());

        assertEquals("Bad number of log messages",
                     1, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Connect failed", getMessage(0));
        clearMessages();

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testMultiConnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut, true);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
            new Connection("gunk", "unused", 1, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(badList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());
    }

    public void testMultiOutput()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);
        mockComp.addEngine("gunk", mockOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.connect(badList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertTrue("Expected error", mockComp.isError());

        assertEquals("Bad number of log messages",
                     1, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Connect failed", getMessage(0));
        clearMessages();

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testConnectException()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        BadOutputEngine badOut = new BadOutputEngine();
        badOut.setConnectException(new IOException("Test"));

        mockComp.addEngine("gunk", badOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.connect(badList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertTrue("Expected error", mockComp.isError());

        assertEquals("Bad number of log messages",
                     1, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Connect failed", getMessage(0));
        clearMessages();

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testBadRealConnect()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] connList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(connList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        try {
            mockComp.connect(connList);
            fail("Expect second connect to fail");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after bogus connect", mockComp.isError());
    }

    public void testBadForcedStop()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        try {
            mockComp.forcedStop();
            fail("Shouldn't be able to stop newly created component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        try {
            mockComp.forcedStop();
            fail("Shouldn't be able to stop unconfigured component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.configure("xxx");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        // forcedStop should succeed even if run has not been started
        mockComp.forcedStop();
        mockComp.waitForStateChange(DAQComponent.STATE_FORCING_STOP);
        assertEquals("Bad state after premature forcedStop",
                     DAQComponent.STATE_READY, mockComp.getState());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());
    }

    public void testForcedStopException()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        BadOutputEngine badOut = new BadOutputEngine();
        badOut.setForcedStopException(new RuntimeException("Test"));

        mockComp.addEngine("gunk", badOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(badList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("xxx");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.forcedStop();
        mockComp.waitForStateChange(DAQComponent.STATE_FORCING_STOP);
        assertEquals("Bad state after forcedStop",
                     DAQComponent.STATE_RUNNING, mockComp.getState());

        assertEquals("Bad number of log messages",
                     1, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Forced stop failed", getMessage(0));
        clearMessages();

        mockComp.reset();
        testComp.waitForStateChange(DAQComponent.STATE_RESETTING);
        assertEquals("Bad state after reset",
                     DAQComponent.STATE_IDLE, mockComp.getState());
        assertFalse("Unexpected error after reset", mockComp.isError());
    }

    public void testStartProcessingException()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        BadOutputEngine badOut = new BadOutputEngine();
        badOut.setStartProcessingException(new RuntimeException("Test"));

        mockComp.addEngine("gunk", badOut);

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(badList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("xxx");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after bad startRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("Expected error after bad startRun", mockComp.isError());

        assertEquals("Bad number of log messages",
                     2, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Couldn't start tst#0", getMessage(0));
        assertEquals("Unexpected log message 1",
                     "Start run failed", getMessage(1));
        clearMessages();
    }

    public void testListConnectors()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        MockInputEngine mockIn = new MockInputEngine();
        mockComp.addEngine("garbage", mockIn);

        MockInputEngine mockSelf = new MockInputEngine();
        mockComp.addEngine(DAQConnector.TYPE_SELF_CONTAINED, mockSelf);

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        int numFound = 0;
        for (Iterator iter = mockComp.listConnectors(); iter.hasNext(); ) {
            iter.next();
            numFound++;
        }
        assertEquals("Bad number of connectors", 3, numFound);
    }

    public void testDestroy()
        throws DAQCompException, IOException
    {
        boolean checkedAll = false;
        for (int i = 0; i < 6; i++) {
            MockComponent mockComp = new MockComponent("tst", 0);
            testComp = mockComp;

            mockComp.stopEnginesWhenStopping();

            MockCache genCache = new MockCache("generic");
            mockComp.addCache(genCache);

            MockInputEngine mockIn = new MockInputEngine();
            mockComp.addEngine("garbage", mockIn);

            MockOutputEngine mockOut = new MockOutputEngine();
            mockComp.addEngine("gunk", mockOut);

            mockComp.setGlobalConfigurationDir("bogus");

            mockComp.start();
            mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

            if (i > 0) {
                MockInputEngine outTarget = new MockInputEngine();

                Connection[] connList = new Connection[] {
                    new Connection("gunk", "someComp", 0, "localhost",
                                   outTarget.getServerPort()),
                };

                mockComp.connect(connList);
                mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
                assertEquals("Bad state after connect#" + i,
                             DAQComponent.STATE_CONNECTED, mockComp.getState());
                assertFalse("Unexpected error after connect#" + i,
                            mockComp.isError());

                if (i > 1) {
                    mockComp.configure("foo");
                    mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
                    assertEquals("Bad state after configure#" + i,
                                 DAQComponent.STATE_READY, mockComp.getState());
                    assertFalse("Unexpected error after configure",
                                mockComp.isError());

                    if (i > 2) {
                        final int startState =
                            DAQComponent.STATE_STARTING;

                        mockComp.startRun(1);
                        mockComp.waitForStateChange(startState);
                        assertEquals("Bad state after startRun#" + i,
                                     DAQComponent.STATE_RUNNING,
                                     mockComp.getState());
                        assertFalse("Unexpected error after startRun",
                                    mockComp.isError());

                        assertTrue("Not really running?!?! (#" + i + ")",
                                   mockComp.isRunning());

                        if (i > 3) {
                            final int stopState =
                                DAQComponent.STATE_STOPPING;

                            mockComp.stopRun();
                            mockComp.waitForStateChange(stopState);
                            assertEquals("Bad state after stopRun#" + i,
                                         DAQComponent.STATE_READY,
                                         mockComp.getState());
                            assertFalse("Unexpected error after stopRun",
                                        mockComp.isError());

                            assertTrue("Not really stopped?!?! (#" + i + ")",
                                       mockComp.isStopped());

                            if (i > 4) {
                                final int curState =
                                    DAQComponent.STATE_DISCONNECTING;

                                mockComp.disconnect();
                                mockComp.waitForStateChange(curState);
                                assertEquals("Bad state after disconnect#" + i,
                                             DAQComponent.STATE_IDLE,
                                             mockComp.getState());

                                if (i > 5) {
                                    throw new Error("Unknown value " + i);
                                }

                                checkedAll = true;
                            }
                        }
                    }
                }
            }

            mockComp.destroy();
            mockComp.waitForStateChange(DAQComponent.STATE_DESTROYING);
        }

        assertTrue("Not all cases were checked", checkedAll);
    }

    public void testOutput()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.setGlobalConfigurationDir("bogus");

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] connList = new Connection[] {
            new Connection("gunk", "someComp", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(connList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("foo");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        assertTrue("Not really running?!?!", mockComp.isRunning());

        mockComp.stopRun();
        System.err.println("XXX Not checking for true stop");
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        if (mockComp.getState() != DAQComponent.STATE_READY) {
            mockComp.forcedStop();
            mockComp.waitForStateChange(DAQComponent.STATE_FORCING_STOP);
        }

        assertTrue("Not really stopped?!?!", mockComp.isStopped());

        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        mockComp.disconnect();
        mockComp.waitForStateChange(DAQComponent.STATE_DISCONNECTING);
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testAll()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        MockInputEngine mockIn = new MockInputEngine();
        mockComp.addEngine("garbage", mockIn);

        MockInputEngine mockSelf = new MockInputEngine();
        mockComp.addEngine(DAQConnector.TYPE_SELF_CONTAINED, mockSelf);

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.setGlobalConfigurationDir("bogus");

        mockComp.start();
        mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] connList = new Connection[] {
            new Connection("gunk", "someComp", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(connList);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        mockComp.configure("foo");
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());

        mockComp.startRun(1);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        assertTrue("Not really running?!?!", mockComp.isRunning());

        mockComp.stopRun();
        System.err.println("XXX Not checking for true stop");
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        if (mockComp.getState() != DAQComponent.STATE_READY) {
            mockComp.forcedStop();
            mockComp.waitForStateChange(DAQComponent.STATE_FORCING_STOP);
        }

        assertTrue("Not really stopped?!?!", mockComp.isStopped());

        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        mockComp.disconnect();
        mockComp.waitForStateChange(DAQComponent.STATE_DISCONNECTING);
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.destroy();
        mockComp.waitForStateChange(DAQComponent.STATE_DESTROYING);
        assertEquals("Bad state",
                     DAQComponent.STATE_DESTROYED, mockComp.getState());
    }

    public void testSimpleServerDied()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        mockComp.serverDied();
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testServerDied()
        throws DAQCompException, IOException
    {
        for (int i = 0; i < 2; i++) {
            MockComponent mockComp = new MockComponent("tst", 0);
            testComp = mockComp;

            MockCache genCache = new MockCache("generic");
            mockComp.addCache(genCache);

            BadOutputEngine badOut = new BadOutputEngine();
            badOut.setForcedStopException(new RuntimeException("Test"));
            if (i > 0) {
                RuntimeException rte = new RuntimeException("Test");
                badOut.setDestroyProcessorException(rte);
            }

            mockComp.addEngine("gunk", badOut);

            mockComp.start();
            mockComp.waitForStateChange(DAQComponent.STATE_UNKNOWN);

            MockInputEngine outTarget = new MockInputEngine();

            Connection[] badList = new Connection[] {
                new Connection("gunk", "unused", 0, "localhost",
                               outTarget.getServerPort()),
            };

            mockComp.connect(badList);
            mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
            assertEquals("Bad state after connect",
                         DAQComponent.STATE_CONNECTED, mockComp.getState());
            assertFalse("Unexpected error after connect", mockComp.isError());

            mockComp.configure("xxx");
            mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
            assertEquals("Bad state after configure",
                         DAQComponent.STATE_READY, mockComp.getState());
            assertFalse("Unexpected error after configure", mockComp.isError());

            mockComp.startRun(1);
            mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
            assertEquals("Bad state after startRun#" + i,
                         DAQComponent.STATE_RUNNING, mockComp.getState());
            assertFalse("Unexpected error after startRun", mockComp.isError());

            assertEquals("Bad number of log messages",
                         0, getNumberOfMessages());

            mockComp.serverDied();
            assertEquals("Bad state for #" + i,
                         DAQComponent.STATE_DESTROYED, mockComp.getState());

            switch (i) {
            case 0:
                assertEquals("Bad number of log messages",
                             1, getNumberOfMessages());
                assertEquals("Unexpected log message 0",
                             "Reset failed", getMessage(0));
                break;
            case 1:
                assertEquals("Bad number of log messages",
                             2, getNumberOfMessages());
                assertEquals("Unexpected log message 0",
                             "Reset failed", getMessage(0));
                assertEquals("Unexpected log message 1",
                             "Destroy failed", getMessage(1));
                break;
            default:
                fail("Unexpected case");
                break;
            }
            clearMessages();
        }
    }

    public void testDestroyException()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockCache genCache = new MockCache("generic");
        mockComp.addCache(genCache);

        BadOutputEngine badOut = new BadOutputEngine();
        badOut.setDestroyProcessorException(new RuntimeException("Test"));

        mockComp.addEngine("gunk", badOut);

        assertEquals("Bad number of log messages",
                     0, getNumberOfMessages());

        mockComp.destroy();
        mockComp.waitForStateChange(DAQComponent.STATE_DESTROYING);
        assertEquals("Bad state after failed destroy",
                     DAQComponent.STATE_DESTROYED, mockComp.getState());

        assertEquals("Bad number of log messages",
                     1, getNumberOfMessages());
        assertEquals("Unexpected log message 0",
                     "Destroy failed", getMessage(0));
        clearMessages();
    }

    public void testResetDestroyed()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        mockComp.destroy();
        mockComp.waitForStateChange(DAQComponent.STATE_DESTROYING);

        try {
            mockComp.reset();
            fail("Shouldn't be able to reset destroyed component");
        } catch (DAQCompException dce) {
            // expect failure
        }
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
