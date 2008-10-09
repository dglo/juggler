package icecube.daq.juggler.component;

/**
 * DAQ connection description.
 */
public class Connection
{
    /** connection type */
    private String type;
    /** component name */
    private String compName;
    /** component number */
    private int compNum;
    /** host name */
    private String host;
    /** host port */
    private int port;

    /**
     * Create a connection using the specified data.
     *
     * @param map map of arrtibute names to attribute values
     */
    Connection(String type, String compName, int compNum,
               String host, int port)
    {
        this.type = type;
        this.compName = compName;
        this.compNum = compNum;
        this.host = host;
        this.port = port;
    }

    /**
     * Get component name for this connection.
     *
     * @return component name
     */
    public String getComponentName()
    {
        return compName;
    }

    /**
     * Get component number for this connection.
     *
     * @return component number
     */
    public int getComponentNumber()
    {
        return compNum;
    }

    /**
     * Get connection host name.
     *
     * @return connection host name
     */
    public String getHost()
    {
        return host;
    }

    /**
     * Get connection host port.
     *
     * @return connection host port
     */
    public int getPort()
    {
        return port;
    }

    /**
     * Get connection type.
     *
     * @return connection type
     */
    public String getType()
    {
        return type;
    }

    /**
     * Does this connection type match the specified type?
     *
     * @param type connection type being checked
     *
     * @return <tt>true</tt> if this connection matches the specified type
     */
    public boolean matches(String type)
    {
        return type != null && type.equals(this.type);
    }

    /*
     * Return debugging string
     *
     * @return debugging description of the connection
     */
    public String toString()
    {
        return type + "=>" + compName + "#" + compNum + "@" + host + ":" + port;
    }
}
