package icecube.daq.juggler.mbean;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.HashMap;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

class MyMonitor
    extends LocalMonitor
{
    private StringWriter writer;

    MyMonitor(String compName, int compNum, int interval, MBeanData data)
    {
        super(compName, compNum, interval, data);
    }

    String getOutput()
    {
        return writer.toString();
    }

    public PrintWriter openWriter()
    {
        writer = new StringWriter();

        return new PrintWriter(writer);
    }
}

class MockData
    implements MBeanData
{
    private int countUp;
    private int countByTwo;
    private String growStr = "a";

    public HashMap getAttributes(String mbeanName, String[] attrNames)
        throws MBeanAgentException
    {
        HashMap map = new HashMap();

        for (int i = 0; i < attrNames.length; i++) {
            if (attrNames[i].equals("countUp")) {
                map.put(attrNames[i], new Integer(countUp++));
            } else if (attrNames[i].equals("countByTwo")) {
                map.put(attrNames[i], new Integer(countByTwo));
                countByTwo += 2;
            } else if (attrNames[i].equals("growStr")) {
                map.put(attrNames[i], growStr);

                if (growStr.length() < 8) {
                    growStr = growStr + growStr.substring(0, 1);
                } else {
                    growStr = "" + (char) (((int) growStr.charAt(0)) + 1);
                }
            }
        }

        return map;
    }

    public String[] listGetters(String mbeanName)
        throws MBeanAgentException
    {
        if (mbeanName.equals("aBean")) {
            return new String[] { "countUp", "growStr" };
        } else {
            return new String[] { "countByTwo", "growStr" };
        }
    }

    public String[] listMBeans()
        throws MBeanAgentException
    {
        return new String[] { "aBean", "otherBean" };
    }
}

public class LocalMonitorTest
    extends TestCase
{
    public LocalMonitorTest(String name)
    {
        super(name);
    }

    private int getNumberOfLinesPerEntry(MBeanData data)
        throws MBeanAgentException
    {
        int numLines = 0;

        String[] beans = data.listMBeans();
        for (int i = 0; i < beans.length; i++) {
            numLines += 1 + data.listGetters(beans[i]).length;
        }

        return numLines;
    }

    public static Test suite()
    {
        return new TestSuite(LocalMonitorTest.class);
    }

    public void testRun()
        throws MBeanAgentException
    {
        MockData mockData = new MockData();

        final int interval = 5;
        final int numReps = 1;

        MyMonitor moni = new MyMonitor("foo", 0, interval, mockData);

        moni.startMonitoring();

        try {
            Thread.sleep((interval * numReps * 1000) + (interval * 1000) / 2);
        } catch (InterruptedException ie) {
            // ignore interrupts
        }

        moni.stopMonitoring();

        final int linesPerEntry = getNumberOfLinesPerEntry(mockData);
        final int expLines = (linesPerEntry * (numReps + 1)) + numReps;

        String output = moni.getOutput();
        String[] lines = output.split("\n");

        assertEquals("Bad number of lines", expLines, lines.length);
    }

    public static void main(String argv[])
    {
        junit.textui.TestRunner.run(suite());
    }
}
