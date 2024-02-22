package nio.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class SimpleClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        Logger logger = LoggerFactory.getLogger(SimpleClient.class);


        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        socketChannel.connect(new InetSocketAddress(8080));
        SelectionKey selectionKey = socketChannel.register(selector, 0);
        selectionKey.interestOps(SelectionKey.OP_CONNECT);
        while (true) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isConnectable()) {
                    if (socketChannel.finishConnect()) {
                        socketChannel.register(selector,SelectionKey.OP_READ);
                        logger.info("已经注册了读事件！");
                        //紧接着向服务端写发送一条消息
                        socketChannel.write(ByteBuffer.wrap("客户端发送成功了".getBytes()));
                    }
                }
                if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel)key.channel();
                    //分配字节缓冲区来接受客户端传过来的数据
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                        //向buffer写入客户端传来的数据
                        int len = channel.read(buffer);
                        byte[] readByte = new byte[len];
                        buffer.flip();
                        buffer.get(readByte);
                        logger.info("读到来自服务端的数据：" + new String(readByte));
                }
            }
        }
    }
}
