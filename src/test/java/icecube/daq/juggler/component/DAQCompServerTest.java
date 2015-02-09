package icecube.daq.juggler.component;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.juggler.test.LogReader;
import icecube.daq.juggler.test.LoggingCase;
import icecube.daq.juggler.test.MockCache;
import icecube.daq.juggler.test.MockHandler;
import icecube.daq.juggler.test.MockOutputEngine;
import icecube.daq.util.LocatePDAQ;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
    private static final Log LOG = LogFactory.getLog(DAQCompServerTest.class);

    private MockHandler handler;
    private File tmpDir;

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

    private static boolean deleteRecursive(File path)
        throws FileNotFoundException
    {
        if (!path.exists()) {
            throw new FileNotFoundException(path.getAbsolutePath());
        }

        boolean ret = true;
        if (path.isDirectory()){
            for (File f : path.listFiles()){
                ret = ret && deleteRecursive(f);
            }
        }

        return ret && path.delete();
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

    public final void tearDown()
        throws FileNotFoundException
    {
        // make sure we don't leave cached directory paths pointing at 'tmpDir'
        LocatePDAQ.clearCache();

        if (tmpDir != null) {
            try {
                deleteRecursive(tmpDir);
            } finally {
                tmpDir = null;
            }
        }
    }

    private void waitForLogMessages(LogReader logRdr)
    {
        for (int i = 0;
             !logRdr.hasError() && !logRdr.isFinished() && i < 10;
             i++)
        {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        if (logRdr.hasError()) fail(logRdr.getNextError());
        assertEquals("Not all log messages were received from " + logRdr,
                     0, logRdr.getNumberOfExpectedMessages());
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

        mockComp.waitForStateChange(DAQState.CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        rtnVal = srvr.configure();
        assertEquals("Bad configure() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQState.CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQState.READY, mockComp.getState());
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

        Object badConn = Integer.valueOf(666);
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
        connMap.put("compName", DAQCmdInterface.DAQ_EVENTBUILDER);
        connMap.put("compNum", Integer.valueOf(0));
        connMap.put("host", "localhost");
        connMap.put("port", Integer.valueOf(port));

        String rtnVal;

        rtnVal = srvr.connect(new Object[] { connMap });
        assertEquals("Bad connect() return value", "OK", rtnVal);

        HashMap[] states = srvr.listConnectorStates();
        assertEquals("Bad number of connectors", 1, states.length);
        assertTrue("Connector does not contain \"type\"",
                   states[0].containsKey("type"));
        assertTrue("Connector does not contain \"state\"",
                   states[0].containsKey("state"));
        assertEquals("Bad connector type", connType, states[0].get("type"));
        assertEquals("Bad connector state", connState, states[0].get("state"));
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

        mockComp.waitForStateChange(DAQState.DESTROYING);
        assertEquals("Bad state after destroy",
                     DAQState.DESTROYED, mockComp.getState());
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
        mockComp.waitForStateChange(DAQState.CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        rtnVal = srvr.configure();
        assertEquals("Bad configure() return value", "OK", rtnVal);
        mockComp.waitForStateChange(DAQState.CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQState.READY, mockComp.getState());
        assertFalse("Unexpected error after configure", mockComp.isError());

        rtnVal = srvr.forcedStop();
        assertEquals("Bad forcedStop() return value", "OK", rtnVal);
        assertEquals("Bad state after forcedStop",
                     DAQState.READY, mockComp.getState());
        assertFalse("Unexpected error after forcedStop", mockComp.isError());

        rtnVal = srvr.startRun(123);
        assertEquals("Bad startRun() return value", "OK", rtnVal);
        mockComp.waitForStateChange(DAQState.STARTING);
        assertEquals("Bad state after startRun",
                     DAQState.RUNNING, mockComp.getState());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        rtnVal = srvr.forcedStop();
        assertEquals("Bad forcedStop() return value", "OK", rtnVal);
        mockComp.waitForStateChange(DAQState.FORCING_STOP);
        assertEquals("Bad state after forcedStop",
                     DAQState.READY, mockComp.getState());
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
            srvr.logTo("xxx", 0, null, 0);
            fail("Should have failed due to null component");
        } catch (DAQCompException dce) {
            assertEquals("Unexpected exception",
                         "Component not found", dce.getMessage());
        }
    }

    public void testLogToOld()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        rtnVal = srvr.logTo("localhost", 999, null, 0);
        assertEquals("Bad logTo() return value", "OK", rtnVal);
    }

    public void testLogToOldSocket()
        throws DAQCompException, IOException
    {
        LogReader dfltLog = new LogReader("dflt");
        LogReader runLog = new LogReader("run");

        //final String endResetMsg = "Logging has been reset";
        //final String startResetMsg = "Resetting logging";

        boolean succeeded = false;
        try {
            MockComponent mockComp = new MockComponent("tst", 0);

            MockServer srvr = new MockServer(mockComp, new String[0]);

            //dfltLog.addExpected(endResetMsg);

            srvr.initializeLogging("localhost", dfltLog.getPort(), null, 0);

            final String dfltMsg = "Test message";
            dfltLog.addExpected(dfltMsg);
            LOG.error(dfltMsg);
            waitForLogMessages(dfltLog);
            waitForLogMessages(runLog);

            String rtnVal;

            //dfltLog.addExpected(startResetMsg);
            //runLog.addExpected(endResetMsg);

            rtnVal = srvr.logTo("localhost", runLog.getPort(), null, 0);
            assertEquals("Bad logTo() return value", "OK", rtnVal);

            final String runMsg = "Another message";
            runLog.addExpected(runMsg);
            LOG.error(runMsg);
            waitForLogMessages(dfltLog);
            waitForLogMessages(runLog);

            //runLog.addExpected(startResetMsg);
            //dfltLog.addExpected(endResetMsg);

            srvr.resetLogging();

            final String finalMsg = "Final message";
            dfltLog.addExpected(finalMsg);
            LOG.error(finalMsg);
            waitForLogMessages(runLog);
            waitForLogMessages(dfltLog);
            succeeded = true;
        } finally {
            dfltLog.close();
            runLog.close();
            if (!succeeded) {
                clearMessages();
            }
        }
    }

    public void testLogToNew()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        String rtnVal;

        rtnVal = srvr.logTo(null, 0, "localhost", 999);
        assertEquals("Bad logTo() return value", "OK", rtnVal);
    }

    public void testLogToNewSocket()
        throws DAQCompException, IOException
    {
        LogReader dfltLog = new LogReader("dflt", true);
        LogReader runLog = new LogReader("run", true);

        //final String endResetMsg = "Logging has been reset";
        //final String startResetMsg = "Resetting logging";

        boolean succeeded = false;
        try {
            MockComponent mockComp = new MockComponent("tst", 0);

            MockServer srvr = new MockServer(mockComp, new String[0]);

            //dfltLog.addExpected(endResetMsg);

            srvr.initializeLogging(null, 0, "localhost", dfltLog.getPort());

            final String dfltMsg = "Test message";
            dfltLog.addExpected(dfltMsg);
            LOG.error(dfltMsg);
            waitForLogMessages(dfltLog);
            waitForLogMessages(runLog);

            String rtnVal;

            //dfltLog.addExpected(startResetMsg);
            //runLog.addExpected(endResetMsg);

            rtnVal = srvr.logTo(null, 0, "localhost", runLog.getPort());
            assertEquals("Bad logTo() return value", "OK", rtnVal);

            final String runMsg = "Another message";
            runLog.addExpected(runMsg);
            LOG.error(runMsg);
            waitForLogMessages(dfltLog);
            waitForLogMessages(runLog);

            //runLog.addExpected(startResetMsg);
            //dfltLog.addExpected(endResetMsg);

            srvr.resetLogging();

            final String finalMsg = "Final message";
            dfltLog.addExpected(finalMsg);
            LOG.error(finalMsg);
            waitForLogMessages(runLog);
            waitForLogMessages(dfltLog);
            succeeded = true;
        } finally {
            dfltLog.close();
            runLog.close();
            if (!succeeded) {
                clearMessages();
            }
        }
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

        File testTmp = File.createTempFile("foo", "").getParentFile();

        // tearDown() method will remove this directory
        tmpDir = new File(testTmp, "tmpTrunk");

        File ddTop = new File(tmpDir, "dispatch");
        ddTop.mkdirs();

        File cfgTop = new File(tmpDir, "config");
        cfgTop.mkdirs();

        // create subdirectories used to validate config directory
        new File(cfgTop, "trigger").mkdirs();
        new File(cfgTop, "domconfigs").mkdirs();

        final int moniInterval = 543;
        final String dispatchDir = ddTop.getAbsolutePath();
        final String globalConfig = cfgTop.getAbsolutePath();
        final long maxFileSize = 678L;

        String[] args = new String[] {
            "-M", "localhost:3",
            "-S",
            "-c", "foo",
            "-d", dispatchDir,
            "-g", globalConfig,
            "-l", "localhost:1,debug",
            "-L", "localhost:2,debug",
            "-m", Integer.toString(moniInterval),
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
                         "Null list of flashers", dce.getMessage());
        }
    }

    public void testStartSubrunListEmpty()
        throws DAQCompException, IOException
    {
        MockComponent mockComp = new MockComponent("tst", 0);

        MockServer srvr = new MockServer(mockComp, new String[0]);

        long startTime = 0L;

        String rtnVal;

        rtnVal = srvr.startSubrun(new ArrayList());
        assertEquals("Bad startSubrun() return value",
                     Long.toString(startTime) + "L", rtnVal);
        assertTrue("startSubrun() was not called",
                   mockComp.wasStartSubrunCalled());
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
            "ABC", Integer.valueOf(12), Integer.valueOf(34),
            Integer.valueOf(56), Integer.valueOf(78), Integer.valueOf(90),
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

    public void testSwitchRunNull()
        throws DAQCompException, IOException
    {
        MockServer srvr = new MockServer();

        try {
            srvr.switchToNewRun(1);
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

        mockComp.waitForStateChange(DAQState.CONNECTING);
        assertFalse("Unexpected error after connect", mockComp.isError());

        rtnVal = srvr.configure("foo");
        assertEquals("Bad configure() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQState.CONFIGURING);
        assertEquals("Bad state after configure",
                     DAQState.READY, mockComp.getState());
        assertTrue("configuring() was not called",
                   mockComp.wasConfiguringCalled());
        assertFalse("Unexpected error after configure", mockComp.isError());

        rtnVal = srvr.startRun(runNum);
        assertEquals("Bad startRun() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQState.STARTING);
        assertEquals("Bad state after startRun",
                     DAQState.RUNNING, mockComp.getState());
        assertTrue("starting() was not called",
                   mockComp.wasStartingCalled());
        assertTrue("started() was not called",
                   mockComp.wasStartedCalled());
        assertFalse("Unexpected error after startRun", mockComp.isError());

        int switchNum = runNum + 10;

        rtnVal = srvr.switchToNewRun(switchNum);
        assertEquals("Bad switchToNewRun() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQState.SWITCHING);
        assertEquals("Bad state after switchRun",
                     DAQState.RUNNING, mockComp.getState());
        assertTrue("switching() was not called",
                   mockComp.wasSwitchingCalled());
        assertFalse("Unexpected error after switchRun", mockComp.isError());

        rtnVal = srvr.stopRun();
        assertEquals("Bad stopRun() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQState.STOPPING);
        assertEquals("Bad state after stopRun",
                     DAQState.READY, mockComp.getState());
        assertTrue("stopped() was not called",
                   mockComp.wasStoppedCalled());
        assertFalse("Unexpected error after stopRun", mockComp.isError());

        rtnVal = srvr.reset();
        assertEquals("Bad reset() return value", "OK", rtnVal);

        mockComp.waitForStateChange(DAQState.RESETTING);
        assertEquals("Bad state",
                     DAQState.IDLE, mockComp.getState());
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
