package icecube.daq.juggler.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;

/**
 * JVM thread statistics monitor.
 */
public class ThreadProfiler
    implements ThreadProfilerMBean
{
    /** Handle for thread management info */
    private ThreadMXBean threadBean;

    /**
     * Create a thread profiler.
     */
    public ThreadProfiler()
    {
        threadBean = ManagementFactory.getThreadMXBean();
        if (!threadBean.isThreadCpuTimeSupported()) {
            threadBean = null;
        }
    }

    /**
     * Get current thread CPU usage info.
     *
     * @return map of threadName->cpuTime
     */
    public HashMap<String, Long> getThreadCPUTime()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();
        if (threadBean != null) {
            for (long id : threadBean.getAllThreadIds()) {
                ThreadInfo info = threadBean.getThreadInfo(id);
                map.put(info.getThreadName(), threadBean.getThreadCpuTime(id));
            }
        }
        return map;
    }

    /**
     * Get general thread data.
     *
     * @return map of threadData->value
     */
    public HashMap<String, Long> getThreadInfo()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();
        if (threadBean != null) {
            long numDaemons = threadBean.getDaemonThreadCount();
            long numThreads = threadBean.getThreadCount();
            map.put("ActiveTotal", numThreads);
            map.put("ActiveDaemons", numDaemons);
            map.put("ActiveNonDaemons", numThreads - numDaemons);
            map.put("PeakActiveTotal", (long) threadBean.getPeakThreadCount());
            map.put("TotalStarted", threadBean.getTotalStartedThreadCount());
            threadBean.resetPeakThreadCount();
        }
        return map;
    }

    /**
     * Get current thread user-level CPU usage info.
     *
     * @return map of threadName->userTime
     */
    public HashMap<String, Long> getThreadUserTime()
    {
        HashMap<String, Long> map = new HashMap<String, Long>();
        if (threadBean != null) {
            for (long id : threadBean.getAllThreadIds()) {
                ThreadInfo info = threadBean.getThreadInfo(id);
                map.put(info.getThreadName(),
                        threadBean.getThreadUserTime(id));
            }
        }
        return map;
    }
}
