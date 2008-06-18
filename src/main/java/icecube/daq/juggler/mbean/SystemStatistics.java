package icecube.daq.juggler.mbean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
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
                    Integer[] array = new Integer[4];
                    for (int i = 0; i < array.length; i++) {
                        try {
                            array[i] = Integer.parseInt(match.group(i + 2));
                        } catch (NumberFormatException nfe) {
                            LOG.error("Couldn't parse df #" + i +
                                      " \"" + match.group(i + 2) +
                                      "\" from \"" + line + "\"", nfe);
                            array[i] = 0;
                        }
                    }

                    if (map == null) {
                        map = new HashMap();
                    }

                    map.put(match.group(6), array);
                } else {
                    Integer avail;
                    try {
                        avail = Integer.valueOf(match.group(4));
                    } catch (NumberFormatException nfe) {
                        LOG.error("Couldn't parse df avail bytes \"" +
                                  match.group(4) + "\" from \"" + line + "\"",
                                  nfe);
                        avail = new Integer(0);
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
            loadStr = String.format("load[%f, %f, %f]", loadAvg[0], loadAvg[1],
                                    loadAvg[2]);
        }

        HashMap dfMap = getAvailableDiskSpace();

        String dfStr;
        if (dfMap == null || dfMap.size() == 0) {
            dfStr = "";
        } else {
            StringBuffer buf = new StringBuffer();

            boolean needComma = loadStr.length() > 0;
            Iterator iter = dfMap.keySet().iterator();
            while (iter.hasNext()) {
                String mountPt = (String) iter.next();

                if (needComma) {
                    buf.append(',');
                }

                Integer avail = (Integer) dfMap.get(mountPt);
                buf.append(String.format("%s[%d]", mountPt,
                                         avail.longValue()));
                needComma = true;
            }

            dfStr = buf.toString();
        }

        return loadStr + dfStr;
    }
}
