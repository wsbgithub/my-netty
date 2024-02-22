package io;


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


/**
 * @author wangshenbing
 */
public class SimpleServer {

    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger(SimpleServer.class);

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        SelectionKey selectionKey = serverSocketChannel.register(selector, 0, serverSocketChannel);
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(8080));
        Work work = new Work();
        while (true) {
            logger.info("main函数阻塞在这里吧。。。。。。。");
            //当没有事件到来的时候，这里是阻塞的,有事件的时候会自动运行
            selector.select();
            //如果有事件到来，这里可以得到注册到该selector上的所有的key，每一个key上都有一个channel
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                //移除已经处理的SelectionKey
                keyIterator.remove();
                if (key.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel)key.channel();
                    //得到客户端的channel
                    SocketChannel socketChannel = channel.accept();
                    //把客户端的channel注册到新线程的selector上
                    work.register(socketChannel);
                    logger.info("客户端在main函数中连接成功！");
                    //连接成功之后，用客户端的channel写回一条消息
                    socketChannel.write(ByteBuffer.wrap("我发送成功了".getBytes()));
                    logger.info("main函数服务器向客户端发送数据成功！");
                }
            }
        }
    }
}
