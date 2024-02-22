package nio.server.doserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class SelectorBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);

    private ServerSocketChannel serverSocketChannel;

    /**
     * @Author: PP-jessica
     * @Description:服务端的selector
     */
    private Selector ServerSelector;

    /**
     * @Author: PP-jessica
     * @Description:客户端的selector
     */
    private Selector clientSelector;

    private SelectionKey selectionKey;

    public SelectorBootstrap() {

    }
    //绑定端口号
    public void bind(int port) throws IOException {
        ServerSelector = Selector.open();
        clientSelector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        selectionKey = serverSocketChannel.register(ServerSelector,SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(InetAddress.getLocalHost(),port));
        //绑定端口号之后就开始执行轮询
        doSelect();
    }

    private void doSelect() throws IOException {
        while (true) {
            logger.info("阻塞在这里吧。。。。。。。");
            ServerSelector.select();
            Iterator<SelectionKey> iterator = ServerSelector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                if (selectionKey.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                    SocketChannel socketChannel = channel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(clientSelector,SelectionKey.OP_READ);
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
            clientSelector.select();
        }
    }

    public ServerSocketChannel getServerSocketChannel() {
        return serverSocketChannel;
    }

    public void setServerSocketChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
    }

    public Selector getSelector() {
        return ServerSelector;
    }

    public void setSelector(Selector selector) {
        this.ServerSelector = selector;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }
}
