package icecube.daq.juggler.component;

import icecube.daq.common.DAQComponentObserver;

import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.PayloadReceiveChannel;
import icecube.daq.io.PayloadTransmitChannel;

import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;

import java.net.ServerSocket;

import java.nio.ByteBuffer;

import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import java.util.Iterator;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

class MockInputEngine
    implements DAQComponentInputProcessor
{
    private Selector selector;
    private int port;
    private Thread server;
    private boolean serving;
    private boolean running;

    public MockInputEngine()
        throws IOException
    {
        selector = Selector.open();

        ServerSocketChannel ssChan = ServerSocketChannel.open();
        ssChan.configureBlocking(false);

        ssChan.socket().bind(null);
        port = ssChan.socket().getLocalPort();

        ssChan.register(selector, SelectionKey.OP_ACCEPT);
    }

    public PayloadReceiveChannel addDataChannel(ReadableByteChannel x0,
                                                IByteBufferCache x1)
    {
        throw new Error("Unimplemented");
    }

    public void destroyProcessor()
    {
        serving = false;
    }

    public void forcedStopProcessing()
    {
        running = false;
    }

    public String getPresentState()
    {
        throw new Error("Unimplemented");
    }

    public int getServerPort()
    {
        return port;
    }

    public boolean isDestroyed()
    {
        return server == null;
    }

    public boolean isDisposing()
    {
        throw new Error("Unimplemented");
    }

    public boolean isRunning()
    {
        return running;
    }

    public boolean isStopped()
    {
        return !running;
    }

    public void registerComponentObserver(DAQComponentObserver x0)
    {
        throw new Error("Unimplemented");
    }

    public void start()
    {
        // do nothing
    }

    public void startDisposing()
    {
        throw new Error("Unimplemented");
    }

    public void startProcessing()
    {
        running = true;
    }

    public void startServer(IByteBufferCache x0)
        throws IOException
    {
        server = new Thread(new ServerThread());
        server.setName("MockInputServer");
        server.start();
    }

    class ServerThread
        implements Runnable
    {
        private final Log LOG = LogFactory.getLog(ServerThread.class);

        ServerThread()
        {
        }

        private void addSocketChannel(SocketChannel chan)
        {
            // do nothing
        }

        public void run()
        {
            serving = true;

            while (serving) {
                int numSelected;
                try {
                    numSelected = selector.select(1000);
                } catch (IOException ioe) {
                    LOG.error("Error on selection", ioe);
                    numSelected = 0;
                }

                if (numSelected != 0) {
                    // get iterator for select keys
                    Iterator selectorIterator =
                        selector.selectedKeys().iterator();
                    while (selectorIterator.hasNext()) {
                        // get the selection key
                        SelectionKey selKey =
                            (SelectionKey) selectorIterator.next();

                        if (selKey.isAcceptable()) {
                            selectorIterator.remove();

                            ServerSocketChannel ssChan =
                                (ServerSocketChannel) selKey.channel();

                            try {
                                SocketChannel chan = ssChan.accept();

                                // if server channel is non-blocking,
                                // chan may be null

                                if (chan != null) {
                                    addSocketChannel(chan);
                                }
                            } catch (IOException ioe) {
                                LOG.error("Couldn't accept client socket", ioe);
                            }
                            continue;
                        }
                    }
                }
            }

            server = null;
        }
    }
}

class MockOutputEngine
    implements DAQComponentOutputProcess
{
    private final Log LOG = LogFactory.getLog(MockOutputEngine.class);

    private boolean connected;
    private boolean running;
    private boolean destroyed;

    public MockOutputEngine()
    {
    }

    public PayloadTransmitChannel addDataChannel(WritableByteChannel x0,
                                                 IByteBufferCache x1)
    {
        throw new Error("Unimplemented");
    }

    public PayloadTransmitChannel connect(IByteBufferCache cache,
                                          WritableByteChannel chan, int srcId)
        throws IOException
    {
        connected = true;
        return null;
    }

    public void destroyProcessor()
    {
        destroyed = true;
    }

    public void disconnect()
        throws IOException
    {
        connected = false;
    }

    public void forcedStopProcessing()
    {
        running = false;
    }

    public String getPresentState()
    {
        throw new Error("Unimplemented");
    }

    public boolean isConnected()
    {
        return connected;
    }

    public boolean isDestroyed()
    {
        return destroyed;
    }

    public boolean isRunning()
    {
        return running;
    }

    public boolean isStopped()
    {
        return !running;
    }

    public void registerComponentObserver(DAQComponentObserver x0)
    {
        throw new Error("Unimplemented");
    }

    public void sendLastAndStop()
    {
        running = false;
    }

    public void start()
    {
        // do nothing
    }

    public void startProcessing()
    {
        running = true;
    }
}

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
}

class MockComponent
    extends DAQComponent
{
    private final Log LOG = LogFactory.getLog(MockComponent.class);

    private boolean calledConfiguring;
    private boolean calledDisconnected;
    private boolean calledStarted;
    private boolean calledStarting;
    private boolean calledStopped;
    private boolean calledStopping;
    private boolean stopEngines;

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
        calledStopping = false;
    }

    public void configuring(String name)
    {
        calledConfiguring = true;
    }

    public void disconnected()
    {
        calledDisconnected = true;
    }

    public void stopEnginesWhenStopping()
    {
        stopEngines = true;
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

    public void stopping()
    {
        calledStopping = true;

        if (stopEngines) {
            for (Iterator iter = listConnectors(); iter.hasNext(); ) {
                DAQConnector conn = (DAQConnector) iter.next();
                try {
                    conn.forcedStopProcessing();
                } catch (Exception ex) {
                    LOG.error("Couldn't stop " + conn, ex);
                }
            }
        }
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

    public void testUnconnectedInput()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.start();

        try {
            mockComp.connect();
            fail("Expect failure due to unconnected output");
        } catch (DAQCompException dce) {
            // expect failure
        }

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

        Connection[] badList = new Connection[] {
            new Connection("bleh", "unused", 0, "localhost", 123),
        };

        try {
            mockComp.connect(badList);
            fail("Expect failure due to unconnected output");
        } catch (DAQCompException dce) {
            // expect failure
        }

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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
            new Connection("gunk", "unused", 1, "localhost",
                           outTarget.getServerPort()),
        };

        try {
            mockComp.connect(badList);
            fail("Expect failure due to multiple output targets");
        } catch (DAQCompException dce) {
            // expect failure
        }

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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
            new Connection("gunk", "unused", 1, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(badList);
        assertEquals("Bad state after multi-connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        try {
            mockComp.connect(badList);
            fail("Expect failure due to multiple output targets");
        } catch (DAQCompException dce) {
            // expect failure
        }

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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        try {
            mockComp.connect(badList);
            fail("Expect failure due to unconnected output");
        } catch (DAQCompException dce) {
            // expect failure
        }

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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] connList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(connList);

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        try {
            mockComp.connect(connList);
            fail("Expect second connect to fail");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state after failed connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());
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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] connList = new Connection[] {
            new Connection("gunk", "someComp", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(connList);

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

        assertTrue("Not really running?!?!", mockComp.isRunning());

        mockComp.stopRun();
        if (mockComp.getState() != DAQComponent.STATE_READY) {
            mockComp.forcedStop();
        }

        assertTrue("Not really stopped?!?!", mockComp.isStopped());

        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());

        mockComp.disconnect();
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
    }

    public void testBadForcedStop()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        try {
            mockComp.forcedStop();
            fail("Shouldn't be able to top newly created component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.connect();
        try {
            mockComp.forcedStop();
            fail("Shouldn't be able to stop unconfigured component");
        } catch (DAQCompException dce) {
            // expect failure
        }

        assertEquals("Bad state after connect",
                     DAQComponent.STATE_CONNECTED, mockComp.getState());

        mockComp.configure("xxx");

        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());

        // forcedStop should succeed even if run has not been started
        mockComp.forcedStop();

        assertEquals("Bad state after premature forcedStop",
                     DAQComponent.STATE_READY, mockComp.getState());

        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(badList);
        mockComp.configure("xxx");

        mockComp.startRun(1);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());

        try {
            mockComp.forcedStop();
            fail("Expected exception from forcedStop()");
        } catch (DAQCompException dce) {
            // expect failure
        }
        assertEquals("Bad state after forcedStop",
                     DAQComponent.STATE_STOPPING, mockComp.getState());

        mockComp.reset();
        assertEquals("Bad state after reset",
                     DAQComponent.STATE_IDLE, mockComp.getState());
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

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] badList = new Connection[] {
            new Connection("gunk", "unused", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(badList);
        mockComp.configure("xxx");

        try {
            mockComp.startRun(1);
            fail("Expected exception from startRun()");
        } catch (DAQCompException dce) {
            // expect failure
        }
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_READY, mockComp.getState());
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

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        int numFound = 0;
        for (Iterator iter = mockComp.listConnectors(); iter.hasNext(); ) {
            iter.next();
            numFound++;
        }
        assertEquals("Bad number of connectors", 2, numFound);
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

            if (i > 0) {
                MockInputEngine outTarget = new MockInputEngine();

                Connection[] connList = new Connection[] {
                    new Connection("gunk", "someComp", 0, "localhost",
                                   outTarget.getServerPort()),
                };

                mockComp.connect(connList);
                assertEquals("Bad state after connect#" + i,
                             DAQComponent.STATE_CONNECTED, mockComp.getState());

                if (i > 1) {
                    mockComp.configure("foo");
                    assertEquals("Bad state after configure#" + i,
                                 DAQComponent.STATE_READY, mockComp.getState());

                    if (i > 2) {
                        mockComp.startRun(1);
                        assertEquals("Bad state after startRun#" + i,
                                     DAQComponent.STATE_RUNNING,
                                     mockComp.getState());

                        assertTrue("Not really running?!?! (#" + i + ")",
                                   mockComp.isRunning());

                        if (i > 3) {
                            mockComp.stopRun();
                            assertEquals("Bad state after stopRun#" + i,
                                         DAQComponent.STATE_READY,
                                         mockComp.getState());

                            assertTrue("Not really stopped?!?! (#" + i + ")",
                                       mockComp.isStopped());

                            if (i > 4) {
                                mockComp.disconnect();
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
        }

        assertTrue("Not all cases were checked", checkedAll);
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

        MockOutputEngine mockOut = new MockOutputEngine();
        mockComp.addEngine("gunk", mockOut);

        mockComp.setGlobalConfigurationDir("bogus");

        mockComp.start();

        MockInputEngine outTarget = new MockInputEngine();

        Connection[] connList = new Connection[] {
            new Connection("gunk", "someComp", 0, "localhost",
                           outTarget.getServerPort()),
        };

        mockComp.connect(connList);

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

        assertTrue("Not really running?!?!", mockComp.isRunning());

        mockComp.stopRun();
        if (mockComp.getState() != DAQComponent.STATE_READY) {
            mockComp.forcedStop();
        }

        assertTrue("Not really stopped?!?!", mockComp.isStopped());

        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());

        mockComp.disconnect();
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());

        mockComp.destroy();
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

            MockInputEngine outTarget = new MockInputEngine();

            Connection[] badList = new Connection[] {
                new Connection("gunk", "unused", 0, "localhost",
                               outTarget.getServerPort()),
            };

            mockComp.connect(badList);
            mockComp.configure("xxx");

            mockComp.startRun(1);
            assertEquals("Bad state after startRun#" + i,
                         DAQComponent.STATE_RUNNING, mockComp.getState());

            mockComp.serverDied();
            assertEquals("Bad state for #" + i,
                         DAQComponent.STATE_DESTROYED, mockComp.getState());
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

        try {
            mockComp.destroy();
            fail("Expected destroy exception");
        } catch (DAQCompException dce) {
            // expect failure
        }
        assertEquals("Bad state after failed destroy",
                     DAQComponent.STATE_DESTROYED, mockComp.getState());
    }

    public void testResetDestroyed()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);
        testComp = mockComp;

        mockComp.destroy();

        try {
            mockComp.reset();
            fail("Resetting destroyed component should fail");
        } catch (DAQCompException dce) {
            // expect failure
        }
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
