package nio.server.doserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class ServerBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);

    private ServerSocketChannel serverSocketChannel;

    private Selector selector;

    private SelectionKey selectionKey;

    public ServerBootstrap() {

    }
    //绑定端口号
    public void bind(int port) throws IOException {
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        selectionKey = serverSocketChannel.register(selector,SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(InetAddress.getLocalHost(),port));
        //绑定端口号之后就开始执行轮询
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
                if (selectionKey.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                    SocketChannel socketChannel = channel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector,SelectionKey.OP_READ);
                    logger.info("客户端channel连接成功了:{}",socketChannel.toString());
                    socketChannel.write(ByteBuffer.wrap("我还不是netty，但我知道你上线了".getBytes()));
                    logger.info("服务器发送消息成功！");
                }
                if (selectionKey.isReadable()) {
                    SocketChannel channel = (SocketChannel)selectionKey.channel();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    int len = channel.read(byteBuffer);
                    if (len == -1) {
                        logger.info("客户端通道要关闭！");
                        channel.close();
                    }
                    byte[] bytes = new byte[len];
                    byteBuffer.flip();
                    byteBuffer.get(bytes);
                    logger.info("收到客户端发送的数据:{}",new String(bytes));
                }
            }
        }
    }


    public ServerSocketChannel getServerSocketChannel() {
        return serverSocketChannel;
    }

    public void setServerSocketChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
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
