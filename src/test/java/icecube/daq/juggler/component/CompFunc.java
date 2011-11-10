package icecube.daq.juggler.component;

public final class CompFunc
{
    public static final int NONE = 0x0000;
    public static final int CONFIGURING = 0x0001;
    public static final int SET_RUN_NUMBER = 0x0002;
    public static final int START_ENGINES = 0x0004;
    public static final int STARTING = 0x0008;
    public static final int STARTED = 0x0010;
    public static final int STOPPING = 0x0020;
    public static final int STOPPED = 0x0040;
    public static final int DISCONNECTED = 0x0080;
    public static final int STOP_MBEAN_AGENT = 0x0100;
    public static final int STOP_STATE_TASK = 0x0200;
    public static final int RESETTING = 0x0400;
    public static final int FLUSH_CACHES = 0x0800;
    public static final int SWITCHING = 0x1000;

    private static final boolean checkBitmap(int bitmap, int bit)
    {
        return (bitmap & bit) == bit;
    }

    public static boolean didConfiguring(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.CONFIGURING);
    }
    public static boolean didDisconnected(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.DISCONNECTED);
    }
    public static boolean didFlushCaches(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.FLUSH_CACHES);
    }
    public static boolean didResetting(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.RESETTING);
    }
    public static boolean didSetRunNumber(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.SET_RUN_NUMBER);
    }
    public static boolean didStartEngines(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.START_ENGINES);
    }
    public static boolean didStarted(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.STARTED);
    }
    public static boolean didStarting(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.STARTING);
    }
    public static boolean didStopMBeanAgent(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.STOP_MBEAN_AGENT);
    }
    public static boolean didStopStateTask(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.STOP_STATE_TASK);
    }
    public static boolean didStopped(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.STOPPED);
    }
    public static boolean didStopping(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.STOPPING);
    }
    public static boolean didSwitching(int bitmap)
    {
        return checkBitmap(bitmap, CompFunc.SWITCHING);
    }
}
