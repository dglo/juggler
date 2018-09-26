package icecube.daq.juggler.mbean;

import java.lang.reflect.Method;
import java.util.HashMap;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MBeanWrapper
    implements DynamicMBean
{
    private static final Log LOG = LogFactory.getLog(MBeanWrapper.class);

    private Object wrappedObj;
    private String[] methodNames;
    private HashMap methods;

    public MBeanWrapper(Object wrappedObj, String[] methodNames)
    {
        this.wrappedObj = wrappedObj;
        this.methodNames = methodNames;
        methods = new HashMap();
    }

    public Object getAttribute(String attribute)
        throws AttributeNotFoundException, MBeanException
    {
        if (!methods.containsKey(attribute)) {
            throw new AttributeNotFoundException("Couldn't find attribute " +
                                                 attribute);
        }

        Method method = (Method) methods.get(attribute);
        try {
            return method.invoke(wrappedObj, null);
        } catch (Exception ex) {
            throw new MBeanException(ex, "Could not invoke " +
                                     wrappedObj.getClass().getName() +
                                     " attribute " + attribute);
        }
    }

    public AttributeList getAttributes(String[] attributes)
    {
        AttributeList list = new AttributeList();

        for (int i = 0; i < attributes.length; i++) {
            Attribute attr;
            try {
                attr = new Attribute(attributes[i],
                                     getAttribute(attributes[i]));
            } catch (Exception ex) {
                continue;
            }

            list.add(attr);
        }

        return list;
    }

    public MBeanInfo getMBeanInfo()
    {
        MBeanAttributeInfo[] attrs =
            new MBeanAttributeInfo[methodNames.length];
        for (int i = 0; i < methodNames.length; i++) {
            String methodName = "get" + methodNames[i];

            Method method;
            try {
                method =
                    wrappedObj.getClass().getMethod(methodName, null);
            } catch (Exception ex) {
                final String errMsg = "Couldn't find method " + methodName +
                    " info for " + wrappedObj.getClass().getName();
                LOG.error(errMsg, ex);
                continue;
            }

            try {
                attrs[i] = new MBeanAttributeInfo(methodNames[i],
                                                  methodNames[i],
                                                  method, null);

            } catch (IntrospectionException ie) {
                final String errMsg = "Couldn't build attribute " +
                    methodNames[i] + " info for " +
                    wrappedObj.getClass().getName();
                LOG.error(errMsg, ie);
                throw new Error(errMsg, ie);
            }

            methods.put(methodNames[i], method);
        }

        return new MBeanInfo(wrappedObj.getClass().getName(), "???",
                             attrs, null, null, null);
    }

    public Object invoke(String actionName, Object[] params,
                         String[] signature)
    {
        try {
            throw new Error("Unimplemented");
        } catch (Error err) {
            LOG.error("Unimplemented", err);
            throw err;
        }
    }

    public void setAttribute(Attribute attribute)
    {
        try {
            throw new Error("Unimplemented");
        } catch (Error err) {
            LOG.error("Unimplemented", err);
            throw err;
        }
    }

    public AttributeList setAttributes(AttributeList attributes)
    {
        try {
            throw new Error("Unimplemented");
        } catch (Error err) {
            LOG.error("Unimplemented", err);
            throw err;
        }
    }

    @Override
    public String toString()
    {
        return "MBeanWrapper[" + wrappedObj.getClass().getName() + ":" +
            wrappedObj + "]";
    }
}
