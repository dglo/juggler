package icecube.daq.juggler.component;

/**
 * Define all DAQ component states.
 */
public enum DAQState {
    UNKNOWN, IDLE, CONNECTING, CONNECTED, CONFIGURING, READY, STARTING,
        RUNNING, STOPPING, DISCONNECTING, DESTROYED, FORCING_STOP, DESTROYING,
        RESETTING;

    /**
     * Convert Java constant name to "camel case" readable name.
     *
     * @return mixed-case name
     */
    public String toString()
    {
        String str = super.toString().toLowerCase();
        while (true) {
            int idx = str.indexOf('_');
            if (idx < 0) {
                break;
            }

            String front;
            if (idx == 0) {
                front = "";
            } else {
                front = str.substring(0, idx);
            }

            String back;
            if (idx + 1 >= str.length()) {
                back = "";
            } else {
                back = str.substring(idx + 1, idx + 2).toUpperCase() +
                    str.substring(idx + 2);
            }
            str = front + back;
        }
        return str;
    }
}
