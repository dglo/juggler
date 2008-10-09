package icecube.daq.juggler.test;

import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.DAQComponentObserver;
import icecube.daq.io.PayloadReceiveChannel;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MockInputEngine
    implements DAQComponentInputProcessor
{
    private Selector selector;
    private int port;
    private Thread server;
    private boolean serving;
    private boolean running;
    private String state;

    public MockInputEngine()
        throws IOException
    {
        selector = Selector.open();

        ServerSocketChannel ssChan = ServerSocketChannel.open();
        ssChan.configureBlocking(false);

        ssChan.socket().bind(null);
        port = ssChan.socket().getLocalPort();

        ssChan.register(selector, SelectionKey.OP_ACCEPT);
    }

    public PayloadReceiveChannel addDataChannel(ReadableByteChannel x0,
                                                IByteBufferCache x1)
    {
        throw new Error("Unimplemented");
    }

    public void destroyProcessor()
    {
        serving = false;
    }

    public void forcedStopProcessing()
    {
        running = false;
    }

    public String getPresentState()
    {
        return state;
    }

    public int getServerPort()
    {
        return port;
    }

    public boolean isDestroyed()
    {
        return server == null;
    }

    public boolean isDisposing()
    {
        throw new Error("Unimplemented");
    }

    public boolean isRunning()
    {
        return running;
    }

    public boolean isStopped()
    {
        return !running;
    }

    public void registerComponentObserver(DAQComponentObserver x0)
    {
        throw new Error("Unimplemented");
    }

    public void setState(String state)
    {
        this.state = state;
    }

    public void start()
    {
        // do nothing
    }

    public void startDisposing()
    {
        throw new Error("Unimplemented");
    }

    public void startProcessing()
    {
        running = true;
    }

    public void startServer(IByteBufferCache x0)
        throws IOException
    {
        server = new Thread(new ServerThread());
        server.setName("MockInputServer");
        server.start();
    }

    class ServerThread
        implements Runnable
    {
        private final Log LOG = LogFactory.getLog(ServerThread.class);

        ServerThread()
        {
        }

        private void addSocketChannel(SocketChannel chan)
        {
            // do nothing
        }

        public void run()
        {
            serving = true;

            while (serving) {
                int numSelected;
                try {
                    numSelected = selector.select(1000);
                } catch (IOException ioe) {
                    LOG.error("Error on selection", ioe);
                    numSelected = 0;
                }

                if (numSelected != 0) {
                    // get iterator for select keys
                    Iterator selectorIterator =
                        selector.selectedKeys().iterator();
                    while (selectorIterator.hasNext()) {
                        // get the selection key
                        SelectionKey selKey =
                            (SelectionKey) selectorIterator.next();

                        if (selKey.isAcceptable()) {
                            selectorIterator.remove();

                            ServerSocketChannel ssChan =
                                (ServerSocketChannel) selKey.channel();

                            try {
                                SocketChannel chan = ssChan.accept();

                                // if server channel is non-blocking,
                                // chan may be null

                                if (chan != null) {
                                    addSocketChannel(chan);
                                }
                            } catch (IOException ioe) {
                                LOG.error("Couldn't accept client socket", ioe);
                            }
                            continue;
                        }
                    }
                }
            }

            server = null;
        }
    }
}
