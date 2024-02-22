import nio.client.doclient.Bootstrap;
import nio.client.doclient.SelectorClientstrap;
import nio.server.doserver.SelectorBootstrap;
//import nio.server.doserver.ServerBootstrap;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class NioTest {
    private static final Logger logger = LoggerFactory.getLogger(NioTest.class);

//    @Test
//    public void testServer() throws IOException {
//        ServerBootstrap bootstrap = new ServerBootstrap();
//        bootstrap.bind(8080);
//    }

    @Test
    public void testClient() throws IOException {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.connect("127.0.0.1",8080);
    }

    @Test
    public void testServerSelector() throws IOException {
        SelectorBootstrap bootstrap = new SelectorBootstrap();
        bootstrap.bind(8080);
    }

    @Test
    public void testClientSelector() throws IOException {
        SelectorClientstrap bootstrap = new SelectorClientstrap();
        bootstrap.connect("127.0.0.1",8080);
    }

    @Test
    public void testFor() {
        for (; ; ) {
            logger.info("我能循环多少次？");
        }
    }

    @Test
    public void testSelector() {
        try {
            SelectorProvider provider = SelectorProvider.provider();
            Selector selector = provider.openSelector();
            int select = selector.select(1000);
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            if (select == 0) {
                System.out.println("我能不能运行呢？");
            }
            if (selectionKeys.isEmpty()) {
                System.out.println("我还是空的");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
