package nio.server;

import nio.server.doserver.ServerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class TestServer {

    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);

    public static void main(String[] args) throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        SelectionKey selectionKey = serverSocketChannel.register(selector, 0, serverSocketChannel);
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(8080));
        //初始化NioEventLoop数组
        NioEventLoop[] workGroup = new NioEventLoop[2];
        workGroup[0] = new NioEventLoop();
        workGroup[1] = new NioEventLoop();
        int i = 0;
        while (true) {
            logger.info("main函数阻塞在这里吧。。。。。。。");
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();
                if (key.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel)key.channel();
                    //得到客户端的channel
                    SocketChannel socketChannel = channel.accept();
                    //计算要取值的数组的下表
                    int index = i % workGroup.length;
                    //把客户端的channel注册到新线程的selector上
                    workGroup[index].register(socketChannel,workGroup[index]);
                    i++;
                    logger.info("socketChannel注册到了第{}个单线程执行器上：",index);
                    //连接成功之后，用客户端的channel写回一条消息
                    socketChannel.write(ByteBuffer.wrap("服务端发送成功了".getBytes()));
                }
                //如果接受到的为可读事件，说明要用客户端的channel来处理
//                if (key.isReadable()) {
//                    //同样有两种方式得到客户端的channel，这里只列出一种
//                    SocketChannel channel = (SocketChannel)key.channel();
//                    //分配字节缓冲区来接受客户端传过来的数据
//                    ByteBuffer buffer = ByteBuffer.allocate(1024);
//                    do {
//                        //向buffer写入客户端传来的数据
//                        int len = channel.read(buffer);
//                        logger.info("读到的字节数：" + len);
//                        if (len == -1) {
//                            channel.close();
//                            break;
//                        }
//                        //切换buffer的读模式
//                        buffer.flip();
//                        logger.info(Charset.defaultCharset().decode(buffer).toString());
//                        //切换buffer的写模式，如果仍有数据，可以继续读取
//                        buffer.clear();
//                    } while (true);
//                }
            }
        }
    }
}
