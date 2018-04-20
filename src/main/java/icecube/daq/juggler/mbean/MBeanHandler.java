package icecube.daq.juggler.mbean;

import java.util.HashMap;
import java.util.Map;

public class MBeanHandler
{
    private static XMLRPCServer server;

    public MBeanHandler()
    {
    }

    static final void clearServer(XMLRPCServer xmlRpcServer)
    {
        if (server == null) {
            throw new Error("XML-RPC server has already been cleared");
        } else if (server != xmlRpcServer) {
            throw new Error("Cannot clear different XML-RPC server");
        }

        server = null;
    }

    public Object get(String mbeanName, String attrName)
        throws MBeanAgentException
    {
        if (server == null) {
            throw new MBeanAgentException("XML-RPC server is unknown");
        }

        return server.get(mbeanName, attrName);
    }

    public HashMap getAttributes(String mbeanName, Object[] objList)
        throws MBeanAgentException
    {
        if (server == null) {
            throw new MBeanAgentException("XML-RPC server is unknown");
        }

        String[] attrList;
        if (objList == null) {
            attrList = new String[0];
        } else {
            attrList = new String[objList.length];
        }

        for (int i = 0; i < attrList.length; i++) {
            attrList[i] = (String) objList[i];
        }

        return server.getAttributes(mbeanName, attrList);
    }

    public Map<String, Object> getDictionary()
        throws MBeanAgentException
    {
        if (server == null) {
            throw new MBeanAgentException("XML-RPC server is unknown");
        }

        return server.getDictionary();
    }

    public Object[] getList(String mbeanName, Object[] objList)
        throws MBeanAgentException
    {
        if (server == null) {
            throw new MBeanAgentException("XML-RPC server is unknown");
        }

        String[] attrList;
        if (objList == null) {
            attrList = new String[0];
        } else {
            attrList = new String[objList.length];
        }

        for (int i = 0; i < attrList.length; i++) {
            attrList[i] = (String) objList[i];
        }

        return server.getList(mbeanName, attrList);
    }

    static final XMLRPCServer getServer()
    {
        return server;
    }

    public String[] listGetters(String mbeanName)
        throws MBeanAgentException
    {
        if (server == null) {
            throw new MBeanAgentException("XML-RPC server is unknown");
        }

        return server.listGetters(mbeanName);
    }

    public String[] listMBeans()
        throws MBeanAgentException
    {
        if (server == null) {
            throw new MBeanAgentException("XML-RPC server is unknown");
        }

        return server.listMBeans();
    }

    static final void setServer(XMLRPCServer xmlRpcServer)
    {
        if (server != null && server != xmlRpcServer) {
            throw new Error("XML-RPC server has already been set");
        }

        server = xmlRpcServer;
    }
}
