package icecube.daq.juggler.mbean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * System statistics
 */
public class SystemStatistics
    implements SystemStatisticsMBean
{
    private static final Log LOG = LogFactory.getLog(SystemStatistics.class);

    /** pattern matching a single load average value. */
    private static final String LOAD_AVG_STR = "\\s+(\\d+\\.\\d+),?";

    /** uptime command. */
    private ProcessBuilder uptime = new ProcessBuilder("uptime");
    /** pattern for parsing 'uptime' output. */
    private Pattern uptimePat =
        Pattern.compile("^.*load\\s+averages?:" + LOAD_AVG_STR + LOAD_AVG_STR +
                        LOAD_AVG_STR);

    /** disk free space command. The -k must be *after* the -P on
     * BSD-based systems! */
    private ProcessBuilder df = new ProcessBuilder("df", "-P", "-l", "-k");
    /** pattern for parsing 'df' output. */
    private Pattern dfPat =
        Pattern.compile("^\\s*(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)" +
                        "\\s+(\\d+)%\\s+(\\S+)\\s*$");

    /** Network IO reading stuff */
    private static String PNDfilename = "/proc/net/dev";
    private BufferedReader PNDreader = null;
    private Pattern barPattern   = Pattern.compile("\\|");
    private Pattern dataPattern  = Pattern.compile("[:\\s]+");
    private Pattern spacePattern = Pattern.compile("\\s+");

    /**
     * Simple constructor.
     */
    public SystemStatistics()
    {
    }

    public double[] getLoadAverage()
    {
        Process proc;
        try {
            proc = uptime.start();
        } catch (IOException ioe) {
            LOG.error("Couldn't get uptime", ioe);
            return null;
        }

        BufferedReader out =
            new BufferedReader(new InputStreamReader(proc.getInputStream()));

        String line;
        try {
            line = out.readLine();
            if (line == null) {
                LOG.error("No uptime output found");
            }
        } catch (IOException ioe) {
            LOG.error("Couldn't read uptime output", ioe);
            line = null;
        }

        if (line != null) {
            Matcher match = uptimePat.matcher(line);
            if (!match.matches()) {
                LOG.error("Bogus uptime line \"" + line + "\"");
            } else {
                double[] array = new double[match.groupCount()];
                for (int i = 0; i < array.length; i++) {
                    try {
                        array[i] = Double.parseDouble(match.group(i + 1));
                    } catch (NumberFormatException nfe) {
                        LOG.error("Couldn't parse load average #" + i +
                                  " \"" + match.group(i + 1) + "\" from \"" +
                                  line + "\"", nfe);
                        array[i] = 0.0;
                    }
                }

                return array;
            }
        }

        return null;
    }

    public HashMap getAvailableDiskSpace()
    {
        Process proc;
        try {
            proc = df.start();
        } catch (IOException ioe) {
            LOG.error("Couldn't get df", ioe);
            return null;
        }

        BufferedReader out =
            new BufferedReader(new InputStreamReader(proc.getInputStream()));

        HashMap map = null;
        while (true) {
            String line;
            try {
                line = out.readLine();
            } catch (IOException ioe) {
                LOG.error("Couldn't read df output", ioe);
                line = null;
            }

            if (line == null) {
                if (map == null) {
                    LOG.error("No df output found");
                }

                break;
            }

            Matcher match = dfPat.matcher(line);
            if (!match.matches()) {
                if (!line.startsWith("Filesystem")) {
                    LOG.error("Bogus df line \"" + line + "\"");
                }
            } else {
                final boolean saveAll = false;
                if (saveAll) {
                    Long[] array = new Long[4];
                    for (int i = 0; i < array.length; i++) {
                        try {
                            array[i] = Long.parseLong(match.group(i + 2));
                        } catch (NumberFormatException nfe) {
                            LOG.error("Couldn't parse df #" + i +
                                      " \"" + match.group(i + 2) +
                                      "\" from \"" + line + "\"", nfe);
                            array[i] = new Long(0);
                        }
                    }

                    if (map == null) {
                        map = new HashMap();
                    }

                    map.put(match.group(6), array);
                } else {
                    Long avail;
                    try {
                        avail = Long.valueOf(match.group(4));
                    } catch (NumberFormatException nfe) {
                        LOG.error("Couldn't parse df avail bytes \"" +
                                  match.group(4) + "\" from \"" + line + "\"",
                                  nfe);
                        avail = new Long(0);
                    }

                    if (map == null) {
                        map = new HashMap();
                    }

                    map.put(match.group(6), avail);
                }
            }
        }

        return map;
    }


    /**
     * Get infomation on current network IO
     *
     * @return TreeMap of network IO name/value stats
     */
    public TreeMap<String, String> getNetworkIO()
    {
        TreeMap<String, String> map = null;
        boolean past_header = false;
        String line = null;
        String[] header;
        String[] rx_headers = null;
        String[] tx_headers = null;
        String[] data;

        // Network IO reading set-up
        try {
            PNDreader = new BufferedReader(new FileReader(PNDfilename));
        } catch (FileNotFoundException fnfe) {
            LOG.error("Couldn't open " + PNDfilename + " on OS: " +
                      System.getProperty("os.name"));
            return null;
        }

        while (true) {
            try {
                line = PNDreader.readLine();
            } catch (IOException ioe) {
                LOG.error("Problem reading io stats: " + ioe);
            }

            if (line == null) { // End of output
                break;
            }

            // Parse header looking for RX and TX headers
            if (!past_header) {
                if (line.startsWith("Inter-|")) {
                    continue;
                } else if (line.startsWith(" face |")) {
                    header = barPattern.split(line);
                    rx_headers = spacePattern.split(header[1]);
                    tx_headers = spacePattern.split(header[2]);
                } else {
                    LOG.error("Bogus " + PNDfilename +
                              "  line: \"" + line + "\"");
                }
                past_header = true;
                continue;
            }

            if (map == null) {
                map = new TreeMap<String, String>();
            }
            data = dataPattern.split(line.trim());
            String iface = data[0];  // one interface per line
            int rx_i;
            int tx_i;
            // The recieve data for this interface
            for(rx_i = 0; rx_i < rx_headers.length; rx_i++) {
                map.put(iface + "_rx_" + rx_headers[rx_i], data[rx_i + 1]);
            }
            // The transmit data for this interface
            for(tx_i = 0; tx_i < tx_headers.length; tx_i++) {
                map.put(iface + "_tx_" + tx_headers[tx_i],
                        data[rx_i + tx_i + 1]);
            }
        }

        return map;
    }


    /**
     * Return description of current statistics.
     *
     * @return description of current statistics
     */
    public String toString()
    {
        double[] loadAvg = getLoadAverage();

        String loadStr;
        if (loadAvg == null || loadAvg.length != 3) {
            loadStr = "";
        } else {
            loadStr = String.format("load: [%f, %f, %f]\n", loadAvg[0],
                                    loadAvg[1], loadAvg[2]);
        }

        HashMap dfMap = getAvailableDiskSpace();

        String dfStr;
        if (dfMap == null || dfMap.size() == 0) {
            dfStr = "";
        } else {
            StringBuffer buf = new StringBuffer("space: {");

            boolean needComma = false;
            Iterator iter = dfMap.keySet().iterator();
            while (iter.hasNext()) {
                String mountPt = (String) iter.next();

                if (needComma) {
                    buf.append(", ");
                }

                Long avail = (Long) dfMap.get(mountPt);
                buf.append(String.format("%s: %d", mountPt,
                                         avail.longValue()));
                needComma = true;
            }

            dfStr = buf.append("}\n").toString();
        }

        TreeMap<String, String> ioMap = getNetworkIO();
        String ioStr;
        if (ioMap == null || ioMap.size() == 0) {
            ioStr = "";
        } else {
            StringBuffer buf = new StringBuffer("network: {");

            boolean needComma = false;
            Iterator<String> iter = ioMap.keySet().iterator();
            while (iter.hasNext()) {
                String ioStat = iter.next();
                String ioData = ioMap.get(ioStat);
                if (needComma) {
                    buf.append(", ");
                }
                buf.append(String.format("%s: %s", ioStat, ioData));
                needComma = true;
            }
            ioStr = buf.append("}\n").toString();
        }

        return loadStr + dfStr + ioStr;
    }

    /**
     * This is intended for unit testing
     */
    public static void main(String[] args) {
        System.out.println("SystemStats: in");
        SystemStatistics ss = new SystemStatistics();

        System.out.println(ss);
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ie) {
                break;
            }
            System.out.println(ss);
        }
        System.out.println("SystemStatistics: out");
    }

}
