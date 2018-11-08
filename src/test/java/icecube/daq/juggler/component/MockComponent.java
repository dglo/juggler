package icecube.daq.juggler.component;

import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.test.MockAlerter;

import icecube.daq.util.FlasherboardConfiguration;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

public class MockComponent
    extends DAQComponent
{
    private final Logger LOG = Logger.getLogger(MockComponent.class);

    private boolean calledConfiguring;
    private boolean calledDisconnected;
    private boolean calledResetting;
    private boolean calledPrepareSubrun;
    private boolean calledStartSubrun;
    private boolean calledStarted;
    private boolean calledStarting;
    private boolean calledStopped;
    private boolean calledStopping;
    private boolean calledSwitching;
    private boolean stopEngines;

    private String version;
    private long numEvents;
    private int moniInterval;
    private String dispatchDirName;
    private String globalConfigDirName;
    private long maxFileSize;
    private long subrunStartTime;
    private long subrunCommitTime;

    private MockAlerter alerter;

    MockComponent(String name, int num)
    {
        super(name, num);
    }

    void clearFlags()
    {
        calledConfiguring = false;
        calledDisconnected = false;
        calledResetting = false;
        calledStarted = false;
        calledStarting = false;
        calledStopped = false;
        calledStopping = false;
        calledSwitching = false;
    }

    @Override
    public void commitSubrun(int subrunNumber, long startTime)
    {
        subrunCommitTime = startTime;
    }

    @Override
    public void configuring(String name)
    {
        calledConfiguring = true;
    }

    @Override
    public void disconnected()
    {
        calledDisconnected = true;
    }

    @Override
    public void enableLocalMonitoring(int interval)
    {
        moniInterval = interval;
    }

    public Alerter getAlerter()
    {
        if (alerter == null) {
            alerter = new MockAlerter();
        }

        return alerter;
    }

    public String getDispatchDirectory()
    {
        return dispatchDirName;
    }

    @Override
    public long getEvents(int subrun)
    {
        return numEvents;
    }

    public String getGlobalConfigurationDirectory()
    {
        return globalConfigDirName;
    }

    public long getMaxFileSize()
    {
        return maxFileSize;
    }

    public int getMonitoringInterval()
    {
        return moniInterval;
    }

    public long getSubrunCommitTime()
    {
        return subrunCommitTime;
    }

    @Override
    public String getVersionInfo()
    {
        return version;
    }

    @Override
    public void initialize()
    {
    }

    @Override
    public void prepareSubrun(int subrunNumber)
    {
        calledPrepareSubrun = true;
    }

    @Override
    public void resetting()
    {
        calledResetting = true;
    }

    @Override
    public void setDispatchDestStorage(String dirName)
    {
        this.dispatchDirName = dirName;
    }

    public void setEvents(long numEvents)
    {
        this.numEvents = numEvents;
    }

    @Override
    public void setGlobalConfigurationDir(String dirName)
    {
        globalConfigDirName = dirName;
    }

    @Override
    public void setMaxFileSize(long maxFileSize)
    {
        this.maxFileSize = maxFileSize;
    }

    public void setSubrunStartTime(long startTime)
    {
        subrunStartTime = startTime;
    }

    public void setVersionInfo(String version)
    {
        this.version = version;
    }

    public void stopEnginesWhenStopping()
    {
        stopEngines = true;
    }

    @Override
    public long startSubrun(List<FlasherboardConfiguration> data)
        throws DAQCompException
    {
        for (FlasherboardConfiguration fb : data) {
            if (fb.getMainboardID().length() != 12 ||
                fb.getMainboardID().startsWith(" "))
            {
                throw new DAQCompException("Bad MBID \"" +
                                           fb.getMainboardID() +
                                           "\" in " + fb);
            }
        }
        calledStartSubrun = true;
        return subrunStartTime;
    }

    @Override
    public void started(int runNumber)
    {
        calledStarted = true;
    }

    @Override
    public void starting(int runNumber)
    {
        calledStarting = true;
    }

    @Override
    public void stopped()
    {
        calledStopped = true;
    }

    @Override
    public void stopping()
    {
        calledStopping = true;

        if (stopEngines) {
            for (DAQConnector conn : listConnectors()) {
                try {
                    conn.forcedStopProcessing();
                } catch (Exception ex) {
                    LOG.error("Couldn't stop " + conn, ex);
                }
            }
        }
    }

    @Override
    public void switching(int runNumber)
    {
        calledSwitching = true;
    }

    boolean wasConfiguringCalled()
    {
        return calledConfiguring;
    }

    boolean wasDisconnectedCalled()
    {
        return calledDisconnected;
    }

    boolean wasPrepareSubrunCalled()
    {
        return calledPrepareSubrun;
    }

    boolean wasResettingCalled()
    {
        return calledResetting;
    }

    boolean wasStartSubrunCalled()
    {
        return calledStartSubrun;
    }

    boolean wasStartedCalled()
    {
        return calledStarted;
    }

    boolean wasStartingCalled()
    {
        return calledStarting;
    }

    boolean wasStoppedCalled()
    {
        return calledStopped;
    }

    boolean wasStoppingCalled()
    {
        return calledStopping;
    }

    boolean wasSwitchingCalled()
    {
        return calledSwitching;
    }
}
