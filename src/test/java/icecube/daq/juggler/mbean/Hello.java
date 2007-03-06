package icecube.daq.juggler.mbean;

public class Hello
    implements HelloMBean
{
    private String message;

    public Hello()
    {
        this("Hello there");
    }

    public Hello(String message)
    {
        setMessage(message);
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getMessage()
    {
        return message;
    }

    public void sayHello()
    {
        System.out.println(message);
    }
}
