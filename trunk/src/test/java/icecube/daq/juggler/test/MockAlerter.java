package icecube.daq.juggler.test;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.payload.IUTCTime;

import java.util.Calendar;
import java.util.Map;

import static org.junit.Assert.*;

public class MockAlerter
    implements Alerter
{
    private boolean inactive;
    private boolean closed;

    private long sendDelay;
    private int numSent;

    public MockAlerter()
    {
    }

    @Override
    public void close()
    {
        closed = true;
    }

    public void deactivate()
    {
        inactive = true;
    }

    public int getNumSent()
    {
        return numSent;
    }

    @Override
    public String getService()
    {
        return DEFAULT_SERVICE;
    }

    @Override
    public boolean isActive()
    {
        return !inactive;
    }

    public boolean isClosed()
    {
        return closed;
    }

    @Override
    public void sendObject(Object obj)
        throws AlertException
    {
        if (sendDelay > 0) {
            try {
                Thread.sleep(sendDelay);
            } catch (InterruptedException ie) {
                // ignore interrupts
            } finally {
                sendDelay = 0;
            }
        }

        numSent++;
    }

    @Override
    public void setAddress(String host, int port)
        throws AlertException
    {
        // ignored
    }

    public void setSendDelay(long millis)
    {
        sendDelay = millis;
    }
}
