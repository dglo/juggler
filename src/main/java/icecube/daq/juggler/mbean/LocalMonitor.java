package icecube.daq.juggler.mbean;

import icecube.daq.juggler.mbean.MBeanAgentException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;

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
                fileName = prefix + "-" + instNum + ".moni";
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
                for (Object attrName : valMap.keySet()) {
                    out.println("\t" + attrName + ": " + valMap.get(attrName));
                }
            }

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
}
