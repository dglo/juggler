package org.apache.xmlrpc.webserver;

import java.util.Map;

public interface XmlRpcStatisticsServerMBean
{
    Map<String, double[]> getProfileTimes();
}
