package icecube.daq.juggler.component;

import icecube.daq.juggler.test.LoggingCase;
import icecube.daq.juggler.test.MockCache;
import icecube.daq.juggler.test.MockHandler;
import icecube.daq.juggler.test.MockOutputEngine;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

class MockServer
    extends DAQCompServer
{
    public MockServer()
    {
        super();
    }

    public MockServer(DAQComponent comp, String[] args)
        throws DAQCompException
    {
        super(comp, args);
    }

    public void startServing()
    {
        // do nothing
    }
}

public class DAQCompServerTest
    extends LoggingCase
{
    private MockHandler handler;

    public DAQCompServerTest(String name)
    {
        super(name);
    }

    int createServer(Selector sel)
        throws IOException
    {
        ServerSocketChannel ssChan = ServerSocketChannel.open();
        ssChan.configureBlocking(false);
        ssChan.socket().setReuseAddress(true);

        ssChan.socket().bind(null);

        ssChan.register(sel, SelectionKey.OP_ACCEPT);

        return ssChan.socket().getLocalPort();
    }

    protected void setUp()
        throws Exception
    {
        super.setUp();

        DAQCompServer.resetStaticValues();

        handler = new MockHandler();
        DAQCompServer.setDefaultLoggingConfiguration(getAppender(), handler);
    }

    public static Test suite()
    {
        return new TestSuite(DAQCompServerTest.class);
    }

    public void testCommitSubrunNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();
        try {
            srvr.commitSubrun(1, "xxx");
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testCommitSubrunBadTime()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        final int subNum = 543;
        final String badTime = "ABC";

        try {
            srvr.commitSubrun(subNum, badTime);
            fail("Should have failed due to bad time");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Bad time '" + badTime + "' for subrun " + subNum,
                         dce.getMessage());
        }
    }

    public void testCommitSubrun()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        final int subNum = 543;
        final long runTime = 12345L;

        String rtnVal;

        rtnVal = srvr.commitSubrun(subNum, Long.toString(runTime) + "L");
        assertEquals("Bad commitSubrun() return value", "OK", rtnVal);
        assertEquals("Bad subrun commit time",
                     runTime, mockComp.getSubrunCommitTime());
    }

    public void testConfigureEmpty()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.configure();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testConfigureNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.configure("foo");
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testConfigure()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        try {
            srvr.configure();
            fail("Should have failed to configure idle component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Cannot configure component " + mockComp +
                         " from state idle", dce.getMessage());
        }

        rtnVal = srvr.connect();
        assertEquals("Bad connect() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        rtnVal = srvr.configure();
        assertEquals("Bad configure() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("configuring() should not have been called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());
    }

    public void testConnectEmptyNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.connect();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testConnectEmptyList()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        try {
            srvr.connect(new Object[0]);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Empty/null list of connections", dce.getMessage());
        }
    }

    public void testConnectNullList()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        try {
            srvr.connect(null);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Empty/null list of connections", dce.getMessage());
        }
    }

    public void testConnectNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.connect(new Object[1]);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testConnectBogus()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        Object badConn = new Integer(666);
        try {
            srvr.connect(new Object[] { badConn });
            fail("Should have failed due to bad connection type");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Unexpected connect element #0: " +
                         badConn.getClass().getName(), dce.getMessage());
        }
    }

    public void testConnectList()
        throws DAQCompException, IOException
    {
        Selector sel = Selector.open();

        int port = createServer(sel);

        final String connType = "glue";
        final String connState = "wacky";

        MockComponent mockComp = new MockComponent("tst", 0);

        MockCache cache = new MockCache(connType);
        mockComp.addCache(connType, cache);

        MockOutputEngine engine = new MockOutputEngine();
        engine.setState(connState);
        mockComp.addEngine(connType, engine);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        HashMap connMap = new HashMap();
        connMap.put("type", connType);
        connMap.put("compName", "abc");
        connMap.put("compNum", new Integer(0));
        connMap.put("host", "localhost");
        connMap.put("port", new Integer(port));
        
        String rtnVal;

        rtnVal = srvr.connect(new Object[] { connMap });
        assertEquals("Bad connect() return value", "OK", rtnVal);

        String[][] states = srvr.listConnectorStates();
        assertEquals("Bad number of connectors", 1, states.length);
        assertEquals("Bad number of connector columns", 2, states[0].length);
        assertEquals("Bad connector type", connType, states[0][0]);
        assertEquals("Bad connector state", connState, states[0][1]);
    }

    public void testDestroyNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.destroy();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testDestroy()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        rtnVal = srvr.destroy();
        assertEquals("Bad destroy() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_DESTROYING);
        assertEquals("Bad state after destroy",
                     DAQComponent.STATE_DESTROYED, mockComp.getState());
        assertFalse("Unexpected error after destroy", mockComp.isError());
    }

    public void testForcedStopNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.forcedStop();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testForcedStop()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        try {
            srvr.forcedStop();
            fail("Should have failed to configure idle component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Cannot force-stop component " + mockComp +
                         " from state idle", dce.getMessage());
        }

        rtnVal = srvr.connect();
        assertEquals("Bad connect() return value", "OK", rtnVal);
        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        rtnVal = srvr.configure();
        assertEquals("Bad configure() return value", "OK", rtnVal);
        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        rtnVal = srvr.forcedStop();
        assertEquals("Bad forcedStop() return value", "OK", rtnVal);
        assertEquals("Bad state after forcedStop",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after forcedStop", mockComp.isError());

        rtnVal = srvr.startRun(123);
        assertEquals("Bad startRun() return value", "OK", rtnVal);
        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        rtnVal = srvr.forcedStop();
        assertEquals("Bad forcedStop() return value", "OK", rtnVal);
        mockComp.waitForStateChange(DAQComponent.STATE_FORCING_STOP);
        assertEquals("Bad state after forcedStop",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertFalse("Unexpected error after forcedStop", mockComp.isError());
    }

    public void testGetEventsNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.getEvents(1);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testGetEvents()
        throws DAQCompException, IOException
    {
        final long numEvents = 12345L;

        MockComponent mockComp = new MockComponent("tst", 0);
        mockComp.setEvents(numEvents);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String numStr = srvr.getEvents(1);
        assertEquals("Bad number of events", "" + numEvents + "L", numStr);
    }

    public void testGetStateNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.getState();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testGetState()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        assertEquals("Bad state", "idle", srvr.getState());
    }

    public void testGetVersionNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.getVersionInfo();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testGetVersion()
        throws DAQCompException, IOException
    {
        String version = "ABC XYZ";

        MockComponent mockComp = new MockComponent("tst", 0);
        mockComp.setVersionInfo(version);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        assertEquals("Bad version", version, srvr.getVersionInfo());
    }

    public void testListConnStatesNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.listConnectorStates();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testLogToNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.logTo("xxx", 0);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testLogTo()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        rtnVal = srvr.logTo("localhost", 999);
        assertEquals("Bad logTo() return value", "OK", rtnVal);
    }

    public void testPing()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        assertEquals("Bad ping() return value", "OK", srvr.ping());
    }

    public void testPrepareSubrunNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.prepareSubrun(0);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testPrepareSubrun()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        rtnVal = srvr.prepareSubrun(321);
        assertEquals("Bad prepareSubrun() return value", "OK", rtnVal);
        assertTrue("prepareSubrun() was not called",
                   mockComp.wasPrepareSubrunCalled());
    }

    public void testProcessArgs()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        final int moniInterval = 543;
        final String dispatchDir = "/tmp";
        final String globalConfig = "/foo";
        final long maxFileSize = 678L;

        String[] args = new String[] {
            "-M", Integer.toString(moniInterval),
            "-S",
            "-c", "foo",
            "-d", dispatchDir,
            "-g", globalConfig,
            "-l", "localhost:1,debug",
            "-s", Long.toString(maxFileSize),
        };

        MockServer srvr = new MockServer(mockComp, args);

        assertEquals("Bad monitoring interval",
                     moniInterval, mockComp.getMonitoringInterval());
        assertEquals("Bad dispatch directory",
                     dispatchDir, mockComp.getDispatchDirectory());
        assertEquals("Bad global config directory",
                     globalConfig, mockComp.getGlobalConfigurationDirectory());
        assertEquals("Bad max file size",
                     maxFileSize, mockComp.getMaxFileSize());
    }

    public void testResetLoggingNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.resetLogging();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testResetLogging()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        rtnVal = srvr.resetLogging();
        assertEquals("Bad resetLogging() return value", "OK", rtnVal);
    }

    public void testResetNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.reset();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testStartRunNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.startRun(1);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testStartSubrunNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.startSubrun((List) null);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testStartSubrunListNull()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        try {
            srvr.startSubrun((List) null);
            fail("Should have failed due to null list");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Empty/null list of flashers", dce.getMessage());
        }
    }

    public void testStartSubrunListEmpty()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        try {
            srvr.startSubrun(new ArrayList());
            fail("Should have failed due to empty list");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Empty/null list of flashers", dce.getMessage());
        }
    }

    public void testStartSubrunListShort()
        throws DAQCompException, IOException
    {
        final long startTime = 123456789L;
        MockComponent mockComp = new MockComponent("tst", 0);
        mockComp.setSubrunStartTime(startTime);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        Object[] flasherCfg = new Object[] { "ABC" };

        ArrayList list = new ArrayList();
        list.add(flasherCfg);

        String rtnVal;

        try {
            srvr.startSubrun(list);
            fail("Should have failed due to bogus list");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Configuration entry #0 has only 1 fields",
                         dce.getMessage());
        }
    }

    public void testStartSubrunListBogus()
        throws DAQCompException, IOException
    {
        final long startTime = 123456789L;
        MockComponent mockComp = new MockComponent("tst", 0);
        mockComp.setSubrunStartTime(startTime);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        Object[] flasherCfg = new Object[] {
            "ABC", "DEF", "GHI", "JKL", "MNO", "PQR",
        };

        ArrayList list = new ArrayList();
        list.add(flasherCfg);

        String rtnVal;

        try {
            srvr.startSubrun(list);
            fail("Should have failed due to bogus list");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Couldn't build config array", dce.getMessage());
        }
    }

    public void testStartSubrun()
        throws DAQCompException, IOException
    {
        final long startTime = 123456789L;
        MockComponent mockComp = new MockComponent("tst", 0);
        mockComp.setSubrunStartTime(startTime);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        Object[] flasherCfg = new Object[] {
            "ABC", new Integer(12), new Integer(34),
            new Integer(56), new Integer(78), new Integer(90),
        };

        ArrayList list = new ArrayList();
        list.add(flasherCfg);

        String rtnVal;

        rtnVal = srvr.startSubrun(list);
        assertEquals("Bad startSubrun() return value",
                     Long.toString(startTime) + "L", rtnVal);
        assertTrue("startSubrun() was not called",
                   mockComp.wasStartSubrunCalled());
    }

    public void testStopRunNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.stopRun();
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testRun()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);
        try {
            srvr.stopRun();
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception", "Cannot stop component " +
                         mockComp + " from state idle", dce.getMessage());
        }
        assertFalse("stopping() was called", mockComp.wasStoppingCalled());
        assertFalse("stopped() was called", mockComp.wasStoppedCalled());
        assertFalse("Unexpected error after bad stopRun", mockComp.isError());

        final int runNum = 123;

        String rtnVal;

        rtnVal = srvr.connect();
        assertEquals("Bad connect() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        rtnVal = srvr.configure("foo");
        assertEquals("Bad configure() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());

        rtnVal = srvr.startRun(runNum);
        assertEquals("Bad startRun() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_STARTING);
        assertEquals("Bad state after startRun",
                     DAQComponent.STATE_RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        rtnVal = srvr.stopRun();
        assertEquals("Bad stopRun() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_STOPPING);
        assertEquals("Bad state after stopRun",
                     DAQComponent.STATE_READY, mockComp.getState());
        assertTrue("stopped() was not called",
                   mockComp.wasStoppedCalled());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        rtnVal = srvr.reset();
        assertEquals("Bad reset() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQComponent.STATE_RESETTING);
        assertEquals("Bad state",
                     DAQComponent.STATE_IDLE, mockComp.getState());
        assertFalse("Unexpected error after reset", mockComp.isError());

/*
        assertEquals("Expected log message", 1, getNumberOfMessages());
        assertEquals("Bad log message", "Where am I?", getMessage(0));
        clearMessages();
*/
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
