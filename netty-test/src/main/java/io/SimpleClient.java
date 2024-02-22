package io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;


public class SimpleClient {

    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger(SimpleClient.class);

        //得到客户端的channel
        SocketChannel socketChannel = SocketChannel.open();
        //把channel设置成非阻塞的
        socketChannel.configureBlocking(false);
        //得到selector
        Selector selector = Selector.open();
        //把客户端channel注册到selector上
        SelectionKey selectionKey = socketChannel.register(selector, 0);
        //设置channel感兴趣的事件
        selectionKey.interestOps(SelectionKey.OP_CONNECT);
        //客户端去连接服务端
        socketChannel.connect(new InetSocketAddress(8080));
        while (true) {
            //没有事件的时候阻塞
            selector.select();
            //得到事件的集合
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                //判断接受到的是什么什么事件，好做出不同的反应
                if (key.isConnectable()) {
                    //完成连接了
                    if (socketChannel.finishConnect()) {
                        //客户端channel关注读事件
                        socketChannel.register(selector,SelectionKey.OP_READ);
                        logger.info("已经注册了读事件！");
                        socketChannel.write(ByteBuffer.wrap("客户端发送成功了".getBytes()));
                    }
                }
                if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel)key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
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
