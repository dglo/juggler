package org.apache.xmlrpc.webserver;

import java.util.HashMap;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;

class RPCStat
{
    private int num;
    private long min;
    private long max;
    private long sum;
    private long sumSquared;

    void tally(long secs)
    {
        num++;

        if (secs < min) {
            min = secs;
        }
        if (secs > max) {
            max = secs;
        }

        sum += secs;
        sumSquared += secs * secs;
    }

    double[] snapshot()
    {
        if (num == 0) {
            return null;
        }

        final double avg = sum / num;
        final double x2avg = sumSquared / num;
        final double xavg2 = avg * avg;

        double rms;
        try {
            rms = Math.sqrt(x2avg - xavg2);
        } catch (Throwable thr) {
            rms = 0.0;
        }

        return new double[] { num, min, max, avg, rms };
    }
}

public class XmlRpcStatisticsServer
    extends ConnectionServer
    implements XmlRpcStatisticsServerMBean
{
    private HashMap<String, RPCStat> statDict = new HashMap<String, RPCStat>();

    private DAQWebServer server;

    XmlRpcStatisticsServer(DAQWebServer webServer)
    {
        this.server = webServer;
    }

    public Object execute(XmlRpcRequest req)
        throws XmlRpcException
    {
        final long start = System.nanoTime();
        try {
            return super.execute(req);
        } finally {
            try {
                final long stop = System.nanoTime();

                final String name = req.getMethodName();

                final String key;
                if (name == null || !name.startsWith("xmlrpc.")) {
                    key = name;
                } else {
                    key = name.substring(7);
                }

                if (key != null) {
                    if (!statDict.containsKey(key)) {
                        // add a new statistics object for this method
                        statDict.put(key, new RPCStat());
                    }

                    statDict.get(key).tally(start - stop);
                }
            } catch (Throwable thr) {
                // ignore failures in statistics-gathering code
            }
        }
    }

    @Override
    public Map<String, double[]> getProfileTimes()
    {
        HashMap<String, double[]> rpcStats = new HashMap<String, double[]>();

        for (String key: statDict.keySet()) {
            double[] snap = statDict.get(key).snapshot();
            if (snap != null) {
                rpcStats.put(key, snap);
            }
        }

        return rpcStats;
    }
}
