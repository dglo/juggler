package icecube.daq.juggler.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import java.util.HashMap;
import java.util.Map;

public class ThreadProfiler
    implements ThreadProfilerMBean
{
    private ThreadMXBean bean = ManagementFactory.getThreadMXBean();

    public ThreadProfiler()
    {
        bean.setThreadContentionMonitoringEnabled(true);
        bean.setThreadCpuTimeEnabled(true);
    }

    public Map<String, Long> getBlockedTime()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();

        for (long id : bean.getAllThreadIds()) {
            ThreadInfo threadInfo = bean.getThreadInfo(id, 0);

            map.put(threadInfo.getThreadName(), threadInfo.getBlockedTime());
        }

        return map;
    }

    public Map<String, Long> getCPUTime()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();

        for (long id : bean.getAllThreadIds()) {
            ThreadInfo threadInfo = bean.getThreadInfo(id, 0);

            final long time = bean.getThreadCpuTime(id);
            map.put(threadInfo.getThreadName(), time);
        }

        return map;
    }

    /**
     * Get general thread data.
     *
     * @return map of threadData-&gt;value
     */
    public HashMap<String, Long> getThreadInfo()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();
        long numDaemons = bean.getDaemonThreadCount();
        long numThreads = bean.getThreadCount();
        map.put("ActiveTotal", numThreads);
        map.put("ActiveDaemons", numDaemons);
        map.put("ActiveNonDaemons", numThreads - numDaemons);
        map.put("PeakActiveTotal", (long) bean.getPeakThreadCount());
        map.put("TotalStarted", bean.getTotalStartedThreadCount());
        bean.resetPeakThreadCount();
        return map;
    }

    public Map<String, Long> getUserTime()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();

        for (long id : bean.getAllThreadIds()) {
            ThreadInfo threadInfo = bean.getThreadInfo(id, 0);

            final long time = bean.getThreadUserTime(id);
            map.put(threadInfo.getThreadName(), time);
        }

        return map;
    }

    public Map<String, Long> getWaitedTime()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();

        for (long id : bean.getAllThreadIds()) {
            ThreadInfo threadInfo = bean.getThreadInfo(id, 0);

            map.put(threadInfo.getThreadName(), threadInfo.getWaitedTime());
        }

        return map;
    }
}
