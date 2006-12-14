package icecube.daq.juggler;

import java.net.URL;

import org.apache.xmlrpc.XmlRpcException;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

public class Ping
{
    public String rpc_ping()
    {
        return "Java";
    }

    public int rpc_register_component(String name, int num, String host,
                                      int port, Object[] connList)
    {
        return 666;
    }

    public static final void main(String[] args)
        throws Exception
    {
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL("http://127.0.0.1:8080"));

        XmlRpcClient client = new XmlRpcClient();
        client.setConfig(config);

	int i;
	Object reply;
	for(i=0; i<10; i++) {
	    try {
		reply = client.execute("rpc_ping", new Object[0]);
	    } catch (XmlRpcException ex) {
		if (!ex.getMessage().contains("No such handler")) {
		    throw ex;
		}
		
		try {
		    reply = client.execute("CnC.rpc_ping", new Object[0]);
		} catch (XmlRpcException ex2) {
		    throw ex2;
		}
	    }
	    System.out.println("Got " + reply);
	}

    }
}
