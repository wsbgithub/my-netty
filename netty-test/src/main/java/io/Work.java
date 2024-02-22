package io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

/**
 * @author wangshenbing
 */
public class Work implements Runnable{

    private static final Logger logger = LoggerFactory.getLogger(Work.class);

    private volatile boolean start;

    private final SelectorProvider provider;

    private Selector selector;

    private Thread thread;

    private SelectionKey selectionKey;

    private SocketChannel socketChannel;

    public Work() {
        //java中的方法，通过provider不仅可以得到selector，还可以得到ServerSocketChannel和SocketChannel
        provider = SelectorProvider.provider();
        this.selector = openSecector();
        thread = new Thread(this);
    }

    public void register(SocketChannel socketChannel) {
        try {
            this.socketChannel = socketChannel;
            socketChannel.configureBlocking(false);
            //在这里注册有用吗？这里仍然是主线程注册channel到新的selector上
            selectionKey = socketChannel.register(selector,SelectionKey.OP_READ);
            start();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:得到用于多路复用的selector
     */
    private Selector openSecector() {
        try {
            selector = provider.openSelector();
            return selector;
        } catch (IOException e) {
            throw new RuntimeException("failed to open a new selector", e);
        }
    }

    public void start() {
        if (start) {
            return;
        }
        start = true;
        thread.start();
    }

    @Override
    public void run() {
        while (true) {
            logger.info("新线程阻塞在这里吧。。。。。。。");
            try {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    if (selectionKey.isReadable()) {
                        SocketChannel channel = (SocketChannel)selectionKey.channel();
                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                        int len = channel.read(byteBuffer);
                        if (len == -1) {
                            logger.info("客户端通道要关闭！");
                            channel.close();
                            break;
                        }
                        byte[] bytes = new byte[len];
                        byteBuffer.flip();
                        byteBuffer.get(bytes);
                        logger.info("新线程收到客户端发送的数据:{}",new String(bytes));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
