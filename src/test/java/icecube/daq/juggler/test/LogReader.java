package icecube.daq.juggler.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogReader
{
    private static final Pattern oldLogPat =
        Pattern.compile("^(\\S+)\\s*(\\S+)?-(\\S+)?\\s+(\\S+)\\s+" +
                        "\\[(\\d+-\\d+-\\d+ \\d+:\\d+:\\d+(\\.\\d+)?)\\]\\s+" +
                        "(.*)$");
    private static final Pattern liveLogPat =
        Pattern.compile("^(\\S+)\\((\\S+):(\\S+)\\)\\s+(\\d+)\\s+" +
                        "\\[(\\d+-\\d+-\\d+ \\d+:\\d+:\\d+(\\.\\d+)?)\\]\\s+" +
                        "(.*)$");

    private String name;
    private boolean isLiveLog;
    private boolean validate;

    private DatagramSocket sock;
    private int port;
    private ArrayList<String> expList;
    private ArrayList<String> errorList;

    private boolean running;
    private boolean verbose;

    public LogReader(String name)
        throws IOException
    {
        this(name, false, false);
    }

    public LogReader(String name, boolean isLiveLog)
        throws IOException
    {
        this(name, isLiveLog, true);
    }

    private LogReader(String name, boolean isLiveLog, boolean validate)
        throws IOException
    {
        this.name = name;
        this.isLiveLog = isLiveLog;
        this.validate = validate;

        sock = new DatagramSocket();
        port = sock.getLocalPort();

        expList = new ArrayList();
        errorList = new ArrayList();

        Thread thread = new Thread(new ReaderThread());
        thread.setName("ReaderThread");
        thread.start();
    }

    public void addExpected(String msg)
    {
        expList.add(msg);
    }

    public void close()
    {
        running = false;
    }

    public String getExpected(int idx)
    {
        return expList.get(idx);
    }

    public String getNextError()
    {
        if (errorList.isEmpty()) {
            return null;
        }

        return errorList.remove(0);
    }

    public int getNumberOfExpectedMessages()
    {
        return expList.size();
    }

    public int getPort()
    {
        if (sock == null) {
            return -1;
        }

        return sock.getLocalPort();
    }

    private String getReaderName()
    {
        if (sock == null) {
            return name + "<closed>";
        }

        return name + "@" + sock.getLocalPort();
    }

    public boolean hasError()
    {
        return !errorList.isEmpty();
    }

    public boolean isFinished()
    {
        return expList.isEmpty();
    }

    public void setVerbose(boolean val)
    {
        verbose = val;
    }

    public String toString()
    {
        return getReaderName();
    }

    class ReaderThread
        implements Runnable
    {
        ReaderThread()
        {
        }

        public void run()
        {
            running = true;

            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (running) {
                try {
                    sock.receive(packet);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    continue;
                }

                String fullMsg = new String(buf, 0, packet.getLength());

                final int len = fullMsg.length();
                if (fullMsg.charAt(len - 1) == '\n') {
                    fullMsg = fullMsg.substring(0, len - 1);
                }

                if (verbose) {
                    System.out.println(name + "@" + getReaderName() + ": " +
                                       fullMsg);
                }

                String style;
                if (!isLiveLog) {
                    style = "pDAQ";
                } else {
                    style = "I3Live";
                }

                if (validate) {
                    Pattern pat;
                    if (!isLiveLog) {
                        pat = oldLogPat;
                    } else {
                        pat = liveLogPat;
                    }

                    Matcher match = pat.matcher(fullMsg);
                    if (!match.find()) {
                        errorList.add(name + ": Got illegal " + style +
                                      " log message \"" + fullMsg + "\"");
                        continue;
                    }
                }

                if (expList.isEmpty()) {
                    errorList.add(name + ": Got unexpected " + style +
                                  " log message: " + fullMsg);
                    continue;
                }

                String expMsg = expList.remove(0);
                if (!fullMsg.startsWith(expMsg) &&
                    !fullMsg.endsWith(expMsg))
                {
                    errorList.add(name + ": Expected " + style + " \"" +
                                  expMsg + "\", got \"" + fullMsg + "\"");
                    continue;
                }
            }

            sock.close();
            sock = null;
        }
    }
}
