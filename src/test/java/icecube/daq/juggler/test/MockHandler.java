package icecube.daq.juggler.test;

import java.util.ArrayList;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Mock java.util.logging handler..
 */
public class MockHandler
    extends Handler
{
    /** <tt>true</tt> if messages should be printed as well as cached. */
    private boolean verbose;
    /** <tt>true</tt> if messages are not kept. */
    private boolean flushMsgs;

    private ArrayList<LogRecord> recordList;

    /**
     * Create a MockHandler which ignores everything below the WARN level.
     */
    public MockHandler()
    {
        this(Level.WARNING);
    }

    /**
     * Create a MockHandler which ignores everything
     * below the specified level.
     *
     * @param minLevel minimum level
     */
    public MockHandler(Level minLevel)
    {
        setLevel(minLevel);
        recordList = new ArrayList<LogRecord>();
    }

    /**
     * Clear the cached logging events.
     */
    public void clear()
    {
        recordList.clear();
    }

    /* (non-API documentation)
     * @see java.util.logging.Handler#close()
     */
    public void close()
        throws SecurityException
    {
        // don't need to do anything
    }

    /* (non-API documentation)
     * @see java.util.logging.Handler#flush()
     */
    public void flush()
    {
        // don't need to do anything
    }

    /* (non-API documentation)
     * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
     */
    public void publish(LogRecord rec)
    {
/*
        if (evt.getLevel().toInt() >= minLevel.toInt()) {
            if (!flushMsgs) {
                recordList.add(evt);
            }

            if (verbose) {
                LocationInfo loc = evt.getLocationInformation();

                System.out.println(evt.getLoggerName() + " " + evt.getLevel() +
                                   " [" + loc.fullInfo + "] " +
                                   evt.getMessage());

                String[] stack = evt.getThrowableStrRep();
                for (int i = 0; stack != null && i < stack.length; i++) {
                    System.out.println("> " + stack[i]);
                }
            }
        }
*/
    }

    private LogRecord getRecord(int idx)
    {
        if (idx < 0 || idx > recordList.size()) {
            throw new IllegalArgumentException("Bad index " + idx);
        }

        return recordList.get(idx);
    }

    public Object getMessage(int idx)
    {
        return getRecord(idx).getMessage();
    }

    public int getNumberOfMessages()
    {
        return recordList.size();
    }

    /**
     * Should log messages be flushed?
     *
     * @param val <tt>false</tt> if log messages should be saved
     */
    public void setFlushMessages(boolean val)
    {
        flushMsgs = val;
    }

    /**
     * Set verbosity.
     *
     * @param val <tt>true</tt> if log messages should be printed
     */
    public MockHandler setVerbose(boolean val)
    {
        verbose = val;

        return this;
    }
}
