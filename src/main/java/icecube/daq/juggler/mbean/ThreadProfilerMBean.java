package icecube.daq.stringhub;

import java.util.HashMap;
import java.util.Map;

public interface ThreadProfilerMBean
{
    Map<String, Long> getBlockedTime();
    Map<String, Long> getCPUTime();
    Map<String, Long> getUserTime();
    Map<String, Long> getWaitedTime();
}
