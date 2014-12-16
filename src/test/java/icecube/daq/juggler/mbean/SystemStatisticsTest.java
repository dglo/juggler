package icecube.daq.juggler.mbean;

import icecube.daq.juggler.test.LoggingCase;

import java.io.File;
import java.util.HashMap;
import java.util.TreeMap;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

public class SystemStatisticsTest
    extends LoggingCase
{
    public SystemStatisticsTest(String name)
    {
        super(name);
    }

    private boolean canReadNetIO()
    {
        File f = new File(SystemStatistics.PNDfilename);
        return f.canRead();
    }

    public static Test suite()
    {
        return new TestSuite(SystemStatisticsTest.class);
    }

    public void testSimple()
    {
        setVerbose(true);

        SystemStatistics sysStat = new SystemStatistics();

        double[] la = sysStat.getLoadAverage();
        System.out.print("Load avg:");
        for (int i = 0; i < la.length; i++) {
            System.out.print(" " + la[i]);
        }
        System.out.println();

        HashMap map = sysStat.getAvailableDiskSpace();
        System.out.println("Disk space:");
        for (Object k : map.keySet()) {
            System.out.println(k.toString() + ": " + map.get(k));
        }

        System.out.flush();
    }

    public void testLoop()
    {
        setVerbose(true);

        SystemStatistics sysStat = new SystemStatistics();

        double tot0 = 0.0;
        HashMap<String, String> replaced = new HashMap<String, String>();

        boolean skipNetIO = !canReadNetIO();
        for (int i = 0; i < 100; i++) {
            if (i % 10 == 0) {
                System.out.println("#" + i + ": Tot0 " + tot0);
            }
            double[] la = sysStat.getLoadAverage();
            tot0 += la[0];

            HashMap disk = sysStat.getAvailableDiskSpace();
            for (Object k : disk.keySet()) {
                replaced.put(k.toString(), disk.get(k).toString());
            }

            if (!skipNetIO) {
                TreeMap<String, String> netio = sysStat.getNetworkIO();
                if (netio == null) {
                    System.err.println("Skipping NetIO check on #" + i);
                    skipNetIO = true;
                } else {
                    for (Object k : netio.keySet()) {
                        replaced.put(k.toString(), netio.get(k));
                    }
                }
            }
        }

        System.out.println("Tot0 " + tot0);
        for (Object k : replaced.keySet()) {
            System.out.println(k + ": " + replaced.get(k));
        }
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
