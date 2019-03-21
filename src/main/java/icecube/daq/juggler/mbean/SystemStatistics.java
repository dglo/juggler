package icecube.daq.juggler.mbean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * System statistics
 */
public class SystemStatistics
    implements SystemStatisticsMBean
{
    private static final Logger LOG = Logger.getLogger(SystemStatistics.class);

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
                        "\\s+(\\d+)%\\s+(\\S.*)\\s*$");
    /** Set to <tt>true</tt> if process ID is not available */
    private boolean warnedPID;

    /** pmap command. */
    private ProcessBuilder pmap;
    /** pattern for parsing 'pmap' output. */
    private Pattern pmapPat =
        Pattern.compile("^(\\S+)\\s+(\\d+)K\\s+([-rwxs]+)\\s+" +
                        "(.*)\\s*$");


    /** Path to network devices */
    public static String PND_FILENAME = "/proc/net/dev";

    /** Network IO reading stuff */
    private Pattern barPattern   = Pattern.compile("\\|");
    private Pattern dataPattern  = Pattern.compile("[:\\s]+");
    private Pattern spacePattern = Pattern.compile("\\s+");

    /** Path to CPU statistics file */
    public static File STAT_FILE = new File("/proc/stat");

    /** Does this system have a /proc/stat file? */
    private boolean hasProcStat;
    /**
     * Simple constructor.
     */
    public SystemStatistics()
    {
        hasProcStat = STAT_FILE.exists();
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

        try {
            proc.getOutputStream().close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        try {
            proc.getErrorStream().close();
        } catch (Throwable thr) {
            // ignore errors on close
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
                        avail = Long.valueOf(0);
                    }

                    if (map == null) {
                        map = new HashMap();
                    }

                    map.put(match.group(6), avail);
                }
            }
        }

        try {
            out.close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        try {
            proc.waitFor();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        return map;
    }

    /*
     * Return a list of overall and per-core CPU statistics.
     *
     * Individual entries contain these CPU times:
     * <ol>
     * <li>user
     * <li>nice
     * <li>system
     * <li>idle
     * <li>iowait
     * <li>irq
     * <li>softirq
     * <li>other
     * </ol>
     *
     * @return array of CPU times
     */
    @Override
    public Map<String, long[]> getCPUStatistics()
    {
        if (!hasProcStat) {
            return null;
        }

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(STAT_FILE));
        } catch (FileNotFoundException fnfe) {
            LOG.error("Couldn't open " + STAT_FILE + " on OS: " +
                      System.getProperty("os.name"));
            return null;
        }

        Pattern white = Pattern.compile("\\s+");

        Map<String, long[]> tmpMap = new HashMap<String, long[]>();

        boolean badLine = false;
        while (true) {
            String line;
            try {
                line = reader.readLine();
            } catch (IOException ioe) {
                LOG.error("Problem reading CPU stats", ioe);
                continue;
            }

            if (line == null) {
                break;
            }

            if (!line.startsWith("cpu")) {
                continue;
            }

            final int knownFields = 7;
            final String[] fields = white.split(line);
            if (fields.length < knownFields + 1) {
                if (badLine) {
                    LOG.error("Giving up on CPU statistics after line" +
                          " \"" + line.trim() + "\"");
                    hasProcStat = false;
                    break;
                }

                LOG.error("Expected at least " + (knownFields + 1) +
                          " fields in CPU statistics line \"" +
                          line.trim() + "\"");
                badLine = true;
                continue;
            }

            long[] values = new long[knownFields + 1];
            for (int i = 0; i < fields.length; i++) {
                boolean badOne = false;
                if (i == 0) {
                    // validate the "cpu" or "cpu##" value; first entry should
                    //   be "cpu", followed by "cpu0", "cpu1", etc.
                    if (fields[0].length() == 3) {
                        // this must be the system-wide 'cpu' line
                        if (tmpMap.size() != 0) {
                            LOG.error("System-wide 'cpu' entry seen after" +
                                      " other CPU entries");
                            badOne = true;
                        }
                    } else {
                        // this entry is one of the CPU cores
                        try {
                            final String numStr = fields[0].substring(3);
                            final int num = Integer.parseInt(numStr);
                            if (num + 1 != tmpMap.size()) {
                                LOG.error("Expected \"cpu" +
                                          (tmpMap.size() - 1) +
                                          "\", not \"" + fields[0] +
                                          "\" in \"" + line.trim() + "\"");
                                badOne = true;
                            }
                        } catch (Throwable thr) {
                            LOG.error("Bad CPU statistics line \"" +
                                      line.trim() + "\"", thr);
                            badOne = true;
                        }
                    }
                } else {
                    long value;
                    try {
                        value = Long.parseLong(fields[i]);
                    } catch (NumberFormatException nfe) {
                        LOG.error("Bad CPU statistics field #" + i + " \"" +
                                  fields[i] + "\" in \"" + line.trim() +
                                  "\"");
                        value = -1;
                        badOne = true;
                    }

                    if (!badOne) {
                        // unknown values get added to the final value
                        if (i - 1 < values.length) {
                            values[i - 1] = value;
                        } else {
                            values[values.length - 1] += value;
                        }
                    }
                }

                if (!badOne) {
                    // ignore single-line failues
                    badLine = false;
                } else if (!badLine) {
                    // give them one chance to fail
                    badLine = true;
                } else {
                    // two strikes and you're out!
                    break;
                }
            }

            tmpMap.put(fields[0], values);
        }

        return tmpMap;
    }

    @Override
    public double[] getLoadAverage()
    {
        Process proc;
        try {
            proc = uptime.start();
        } catch (IOException ioe) {
            LOG.error("Couldn't get uptime", ioe);
            return null;
        }

        try {
            proc.getOutputStream().close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        try {
            proc.getErrorStream().close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        BufferedReader out =
            new BufferedReader(new InputStreamReader(proc.getInputStream()));

        String line;

        try {
            try {
                line = out.readLine();
                if (line == null) {
                    LOG.error("No uptime output found");
                }
            } catch (IOException ioe) {
                LOG.error("Couldn't read uptime output", ioe);
                line = null;
            }
        } finally {
            try {
                out.close();
            } catch (Throwable thr) {
                // ignore errors on close
            }
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

        try {
            proc.waitFor();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        return null;
    }

    /**
     * Get information on current network IO
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
        BufferedReader PNDreader;
        try {
            PNDreader = new BufferedReader(new FileReader(PND_FILENAME));
        } catch (FileNotFoundException fnfe) {
            LOG.error("Couldn't open " + PND_FILENAME + " on OS: " +
                      System.getProperty("os.name"));
            return null;
        }

        while (true) {
            try {
                line = PNDreader.readLine();
            } catch (IOException ioe) {
                LOG.error("Problem reading io stats", ioe);
            }

            if (line == null) {
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
                    LOG.error("Bogus " + PND_FILENAME +
                              "  line: \"" + line + "\"");
                }
                past_header = true;
                continue;
            }

            if (map == null) {
                map = new TreeMap<String, String>();
            }
            data = dataPattern.split(line.trim());
            String iface = data[0];
            int rx_i;
            int tx_i;
            // The recieve data for this interface
            for (rx_i = 0; rx_i < rx_headers.length; rx_i++) {
                map.put(iface + "_rx_" + rx_headers[rx_i], data[rx_i + 1]);
            }
            // The transmit data for this interface
            for (tx_i = 0; tx_i < tx_headers.length; tx_i++) {
                map.put(iface + "_tx_" + tx_headers[tx_i],
                        data[rx_i + tx_i + 1]);
            }
        }

        try {
            PNDreader.close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        return map;
    }

    /**
     * Get current process ID (warning, this is UGLY).
     *
     * @return process ID (null if unavailable)
     */
    private String getProcessID()
    {
        // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();

        final int index = jvmName.indexOf('@');
        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            return null;
        }

        return jvmName.substring(0, index);
    }

    /**
     * Return the process memory map
     *
     * @return map of memory segments
     */
    public HashMap<String, Integer> getProcessMemory()
    {
        if (pmap == null) {
            if (warnedPID) {
                return null;
            }

            String pid = getProcessID();
            if (pid == null) {
                LOG.error("Cannot get current process ID");
                warnedPID = true;
                return null;
            }

            pmap = new ProcessBuilder("pmap", "-q", pid);
        }

        Process proc;
        try {
            proc = pmap.start();
        } catch (IOException ioe) {
            LOG.error("Couldn't get pmap", ioe);
            return null;
        }

        try {
            proc.getOutputStream().close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        try {
            proc.getErrorStream().close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        BufferedReader out =
            new BufferedReader(new InputStreamReader(proc.getInputStream()));

        HashMap<String, KBytes> allocMap = new HashMap<String, KBytes>();

        while (true) {
            String line;
            try {
                line = out.readLine();
            } catch (IOException ioe) {
                LOG.error("Cannot read pmap output", ioe);
                break;
            }

            if (line == null) {
                break;
            }

            Matcher m = pmapPat.matcher(line);
            if (!m.matches()) {
                if (!line.contains(":  ")) {
                    LOG.error("Bogus pmap line \"" + line + "\"");
                }
                continue;
            }

            int kbytes;
            try {
                kbytes = Integer.parseInt(m.group(2));
            } catch (NumberFormatException nfe) {
                LOG.error("Bad size \"" + m.group(2) + "\" for " + m.group(4));
                continue;
            }

            String key = m.group(4);
            if (key.startsWith("[ ") && key.endsWith(" ]")) {
                key = key.substring(2, key.length() - 2);
            } else if (key.endsWith(" (deleted)")) {
                key = key.substring(0, key.length() - 10);
            }

            if (!allocMap.containsKey(key)) {
                allocMap.put(key, new KBytes(kbytes));
            } else {
                allocMap.get(key).add(kbytes);
            }
        }

        try {
            out.close();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        try {
            proc.waitFor();
        } catch (Throwable thr) {
            // ignore errors on close
        }

        HashMap<String, Integer> finalMap = new HashMap<String, Integer>();
        for (Map.Entry<String, KBytes> entry : allocMap.entrySet()) {
            finalMap.put(entry.getKey(), entry.getValue().value());
        }

        return finalMap;
    }

    /**
     * Return description of current statistics.
     *
     * @return description of current statistics
     */
    @Override
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
            StringBuilder buf = new StringBuilder("space: {");

            boolean needComma = false;
            for(Map.Entry entry : (Set<Map.Entry>)dfMap.entrySet()) {
                String mountPt = (String) entry.getKey();

                if (needComma) {
                    buf.append(", ");
                }

                long avail = (Long) entry.getValue();
                buf.append(String.format("%s: %d", mountPt,
                                         avail));
                needComma = true;
            }

            dfStr = buf.append("}\n").toString();
        }

        TreeMap<String, String> ioMap = getNetworkIO();
        String ioStr;
        if (ioMap == null || ioMap.size() == 0) {
            ioStr = "";
        } else {
            StringBuilder buf = new StringBuilder("network: {");

            boolean needComma = false;
            for(Map.Entry entry: ioMap.entrySet()) {
                String ioStat = (String)entry.getKey();
                String ioData = (String)entry.getValue();

                if (needComma) {
                    buf.append(", ");
                }
                buf.append(String.format("%s: %s", ioStat, ioData));
                needComma = true;
            }
            ioStr = buf.append("}\n").toString();
        }

        Map<String, long[]> cpuStats = getCPUStatistics();
        String cpuStr;
        if (cpuStats == null) {
            cpuStr = "";
        } else {
            StringBuilder buf = new StringBuilder("cpu: {\n");
            for (Map.Entry<String, long[]> pair : cpuStats.entrySet()) {
                buf.append("    \"").append(pair.getKey()).append("\": [");

                final long[] values = pair.getValue();
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        buf.append(", ");
                    }
                    buf.append(values[i]);
                }
                buf.append("],\n");
            }
            cpuStr = buf.append("}\n").toString();
        }

        return loadStr + dfStr + ioStr + cpuStr;
    }

    /**
     * This is intended for unit testing
     */
    public static void main(String[] args)
    {
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

    /** Track kilobyte values for `pmap` memory segments */
    class KBytes
    {
        /** Total kilobytes */
        private int total;

        /**
         * Create one
         *
         * @param val initial value
         */
        KBytes(int val)
        {
            total = val;
        }

        /**
         * Add kbyte value to the total.
         *
         * @param val kbyte value
         */
        void add(int val)
        {
            total += val;
        }

        /**
         * Return value in kbytes
         *
         * @return kbytes
         */
        int value()
        {
            return total;
        }

        /**
         * Return the kbyte string
         *
         * @return kbyte string
         */
        @Override
        public String toString()
        {
            return Integer.toString(total);
        }
    }
}
