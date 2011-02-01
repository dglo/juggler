package icecube.daq.juggler.component;

import icecube.daq.util.FlasherboardConfiguration;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MockComponent
    extends DAQComponent
{
    private final Log LOG = LogFactory.getLog(MockComponent.class);

    private boolean calledConfiguring;
    private boolean calledDisconnected;
    private boolean calledResetting;
    private boolean calledPrepareSubrun;
    private boolean calledStartSubrun;
    private boolean calledStarted;
    private boolean calledStarting;
    private boolean calledStopped;
    private boolean calledStopping;
    private boolean stopEngines;

    private String version;
    private long numEvents;
    private int moniInterval;
    private String dispatchDirName;
    private String globalConfigDirName;
    private long maxFileSize;
    private long subrunStartTime;
    private long subrunCommitTime;

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
    }

    public void commitSubrun(int subrunNumber, long startTime)
    {
        subrunCommitTime = startTime;
    }

    public void configuring(String name)
    {
        calledConfiguring = true;
    }

    public void disconnected()
    {
        calledDisconnected = true;
    }

    public void enableLocalMonitoring(int interval)
    {
        moniInterval = interval;
    }

    public String getDispatchDirectory()
    {
        return dispatchDirName;
    }

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

    public String getVersionInfo()
    {
        return version;
    }

    public void prepareSubrun(int subrunNumber)
    {
        calledPrepareSubrun = true;
    }

    public void resetting()
    {
        calledResetting = true;
    }

    public void setDispatchDestStorage(String dirName)
    {
        this.dispatchDirName = dirName;
    }

    public void setEvents(long numEvents)
    {
        this.numEvents = numEvents;
    }

    public void setGlobalConfigurationDir(String dirName)
    {
        globalConfigDirName = dirName;
    }

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
            for (DAQConnector conn : listConnectors()) {
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
}
