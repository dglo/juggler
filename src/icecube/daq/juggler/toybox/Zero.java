package icecube.daq.juggler.toybox;

import icecube.daq.juggler.component.DAQCompConfig;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;

import icecube.daq.payload.IByteBufferCache;

/**
 * Timer task.
 */
class TimerTask
    implements Runnable
{
    private Zero zero;
    private int delay;

    /**
     * Create a timer task.
     *
     * @param delay number of milliseconds to 'run'
     */
    TimerTask(Zero zero, int delay)
    {
        this.zero = zero;
        this.delay = delay;
    }

    /**
     * Delay for the specified number of seconds, then end the 'run'
     */
    public void run()
    {
        System.err.println("ZERO start");

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            // ignore interrupts
        }

        zero.endRun();

        System.err.println("ZERO stop");
    }
}

/**
 * Standalone component.
 */
public class Zero
    extends DAQComponent
{
    private int delay = 10000;
    private boolean running;

    /**
     * Create a standalone component.
     *
     * @param config configuration info
     */
    public Zero(DAQCompConfig config)
    {
        super("zero", 0);
    }

    /**
     * Emergency stop of component.
     *
     * @throws DAQCompException if there is a problem
     */
    public void emergencyStop()
        throws DAQCompException
    {
        endRun();

        super.emergencyStop();
    }

    void endRun()
    {
        running = false;
    }

    /**
     * Are I/O engines still running?
     *
     * @return <tt>true</tt> if any engine is still running
     */
    public boolean isRunning()
    {
        return running;
    }

    /**
     * Are I/O engines still running?
     *
     * @return <tt>true</tt> if any engine is still running
     */
    public boolean isStopped()
    {
        return !running;
    }

    /**
     * Start engines.
     *
     * @throws DAQCompException if there is a problem
     */
    public void startEngines()
        throws DAQCompException
    {
        running = true;

        Thread timerTask = new Thread(new TimerTask(this, delay));
        timerTask.setName("zeroTimer");
        timerTask.start();
    }
}
