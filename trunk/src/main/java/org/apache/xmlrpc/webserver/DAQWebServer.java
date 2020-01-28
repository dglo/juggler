package org.apache.xmlrpc.webserver;

import org.apache.xmlrpc.server.XmlRpcStreamServer;

public class DAQWebServer
    extends WebServer
{
    private String name;
    public DAQWebServer(String name, int port)
    {
        super(port);
        this.name = name;
    }

    String getThreadPrefix() { return name; }
    protected XmlRpcStreamServer newXmlRpcStreamServer()
    {
        return new XmlRpcStatisticsServer(this);
    }
}
