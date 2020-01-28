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

    @Override
    public void setMessage(String message)
    {
        this.message = message;
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    @Override
    public void sayHello()
    {
        System.out.println(message);
    }
}
