package nio.client.doclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class Bootstrap {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    private SocketChannel socketChannel;

    private Selector selector;

    private SelectionKey selectionKey;


    public Bootstrap() {

    }

    public void connect(String host, int port) throws IOException {
        selector = Selector.open();
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        selectionKey = socketChannel.register(selector,SelectionKey.OP_CONNECT);
        socketChannel.connect(new InetSocketAddress(host,port));
        doSelect();
    }

    private void doSelect() throws IOException {
        while (true) {
            logger.info("阻塞在这里吧。。。。。。。");
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                if (selectionKey.isConnectable()) {
                    SocketChannel channel = (SocketChannel)selectionKey.channel();
                    if (channel.finishConnect()) {
                        channel.register(selector,SelectionKey.OP_READ);
                        logger.info("客户端连接成功！:{}",channel.toString());
                    }
                }
                if (selectionKey.isReadable()) {
                    SocketChannel channel = (SocketChannel)selectionKey.channel();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    int len = channel.read(byteBuffer);
                    byte[] bytes = new byte[len];
                    byteBuffer.flip();
                    byteBuffer.get(bytes);
                    logger.info("收到服务器发送的数据:{}",new String(bytes));
                }
            }
        }
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }
}
