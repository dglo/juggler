package icecube.daq.juggler.mbean;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LocalMonitor
    implements Runnable
{
    private static final Log LOG = LogFactory.getLog(LocalMonitor.class);

    private String compName;
    private int compNum;
    private int interval;
    private MBeanData mbeanData;

    private boolean running;

    public LocalMonitor(String compName, int compNum, int seconds,
                        MBeanData mbeanData)
    {
        this.compName = compName;
        this.compNum = compNum;
        this.interval = seconds * 1000;
        this.mbeanData = mbeanData;
    }

    private HashMap<String, String[]> buildBeanHash()
    {
        HashMap<String, String[]> beanHash = new HashMap<String, String[]>();

        String[] beans;
        try {
            beans = mbeanData.listMBeans();
        } catch (MBeanAgentException mae) {
            LOG.error("Couldn't get list of MBeans", mae);
            return null;
        }

        for (int i = 0; i < beans.length; i++) {
            String[] attrs;
            try {
                attrs = mbeanData.listGetters(beans[i]);
            } catch (MBeanAgentException mae) {
                LOG.error("Couldn't get attributes for MBean " + beans[i],
                          mae);
                continue;
            }

            beanHash.put(beans[i], attrs);
        }

        return beanHash;
    }

    public PrintWriter openWriter()
    {
        String prefix = "/tmp/" + compName + "-" + compNum;
        int instNum = -1;

        File file;
        do {
            instNum++;

            String fileName;
            if (instNum > 0) {
                fileName = prefix + "#" + instNum + ".moni";
            } else {
                fileName = prefix + ".moni";
            }
            file = new File(fileName);
        } while (file.exists());

        PrintWriter out;
        try {
            out =
                new PrintWriter(new BufferedWriter(new FileWriter(file)));
        } catch (IOException ioe) {
            throw new Error("Cannot create monitoring file " + file,
                            ioe);
        }

        return out;
    }

    public void run()
    {
        if (mbeanData == null) {
            throw new Error("Cannot locally monitor null MBean data");
        }

        boolean firstEntry = true;

        PrintWriter out = openWriter();

        HashMap<String, String[]> beanHash = buildBeanHash();

        String[] keys = beanHash.keySet().toArray(new String[0]);
        Arrays.sort(keys);

        running = true;
        while (running) {
            long nextTime = System.currentTimeMillis() + interval;

            if (firstEntry) {
                firstEntry = false;
            } else {
                out.println();
            }

            Calendar cal = Calendar.getInstance();

            String dateStr = String.format("%tF %tT.%tL", cal, cal, cal);

            for (int i = 0; i < keys.length; i++) {
                HashMap valMap;
                try {
                    valMap = mbeanData.getAttributes(keys[i],
                                                     beanHash.get(keys[i]));
                } catch (MBeanAgentException mae) {
                    LOG.error("Couldn't get mbean " + keys[i] + " values",
                              mae);
                    continue;
                }

                out.println(keys[i] + ": " + dateStr + ":");
		for (Map.Entry entry: (Set<Map.Entry>)valMap.entrySet()) {
		    out.println("\t" + entry.getKey() + ": " +
                                toString(entry.getValue()));
		}
            }
            out.flush();

            long remainder = nextTime - System.currentTimeMillis();
            if (remainder > 0) {
                try {
                    Thread.sleep(remainder);
                } catch (InterruptedException ie) {
                    // ignore interrupts
                }
            }
        }

        out.close();
    }

    /**
     * Set the MBean data handler to be monitored locally.
     *
     * @param data MBean data handler
     */
    public void setMonitoringData(MBeanData data)
    {
        mbeanData = data;
    }

    public void startMonitoring()
    {
        Thread thread = new Thread(this);
        thread.setName(compName + "#" + compNum + "-Monitoring");
        thread.start();
    }

    public void stopMonitoring()
    {
        running = false;
    }

    private String toString(Object obj)
    {
        if (obj.getClass().isArray()) {
            StringBuffer strBuf = new StringBuffer("[");
            final int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (strBuf.length() > 1) {
                    strBuf.append(", ");
                }
                strBuf.append(toString(Array.get(obj, i)));
            }
            strBuf.append("]");
            return strBuf.toString();
        } else if (obj.getClass().equals(HashMap.class)) {
            StringBuffer strBuf = new StringBuffer("{");
            HashMap map = (HashMap) obj;

	    for (Map.Entry entry: (Set<Map.Entry>)map.entrySet()) {
                if (strBuf.length() > 1) {
                    strBuf.append(", ");
                }
                strBuf.append('\'').append(toString(entry.getKey()));
                strBuf.append("': ").append(toString(entry.getValue()));
            }
            strBuf.append("}");
            return strBuf.toString();
        } else {
            return obj.toString();
        }
    }
}
