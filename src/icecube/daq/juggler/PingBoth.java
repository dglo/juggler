package icecube.daq.juggler;

import icecube.daq.juggler.mock.MockAppender;

import java.net.InetAddress;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;

import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;

import org.apache.xmlrpc.XmlRpcException;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import java.net.URL;

import org.apache.xmlrpc.webserver.WebServer;

public class PingBoth
{
    private static final int port = 9000;

    public static void main(String[] args)
        throws Exception
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(new MockAppender(Level.ERROR));

        WebServer webServer = new WebServer(port);
        
        XmlRpcServer xmlRpcServer = webServer.getXmlRpcServer();
        
        PropertyHandlerMapping phm = new PropertyHandlerMapping();
        phm.addHandler("CnC", Ping.class);

        xmlRpcServer.setHandlerMapping(phm);
      
        XmlRpcServerConfigImpl serverConfig =
            (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
        serverConfig.setEnabledForExtensions(true);
        serverConfig.setContentLengthOptional(false);

	/* Before starting server, spawn of client ping'er */
	
        webServer.start();
	System.out.println("Started Webserver!");
	XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL("http://localhost:9001"));
	XmlRpcClient client = new XmlRpcClient();
        client.setConfig(config);

	while(true) {
	    Object reply = null;
            try {
		reply = client.execute("rpc_ping", new Object[0]);
	    } catch (XmlRpcException ex) {
		System.err.println(ex);
	    }
	    System.out.println("Got " + reply);
	    Thread.sleep(1000);
	}
    }
}
