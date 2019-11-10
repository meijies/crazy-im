package com.meijie.crazy.rpc;

import com.google.protobuf.BlockingService;
import com.meijie.crazy.core.concurrency.Threads;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Netty Service Implement originally copy from Atomix
 *
 * @author meijie
 */
public class NettyMessageService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final AtomicBoolean started = new AtomicBoolean(false);

    private EventLoopGroup serverGroup;
    private EventLoopGroup clientGroup;
    private ChannelFuture serverChannelFuture;

    private Class<? extends ServerChannel> serverChannelClass;
    private Class<? extends Channel> clientChannelClass;

    public void init() {
        initEventLoopGroup();
    }

    public void scanProtocolAndRegistry(String servicePackage) {
        Reflections reflections = new Reflections(servicePackage);
        // TODO scan the client and service implement
    }

    public void registryProtocol(String protocol, int version, BlockingService blockingService) {
        ProtocolRegister.registry(new ProtocolRegister.ProtoNameVer(protocol, version), blockingService);
    }

    public void start(Address serverAddress) throws InterruptedException {
        if (started.compareAndSet(false, true)) {
            startServer(serverAddress);
        } else {
            log.warn("Already running at local address: {}", serverAddress.host());
            return;
        }
    }

    private void initEventLoopGroup() {
        try {
            clientGroup = new EpollEventLoopGroup(0, Threads.namedThreads("netty-messaging-event-epoll-client-%d", log));
            serverGroup = new EpollEventLoopGroup(0, Threads.namedThreads("netty-messaging-event-epoll-server-%d", log));
            serverChannelClass = EpollServerSocketChannel.class;
            clientChannelClass = EpollSocketChannel.class;
            return;
        } catch (Throwable e) {
            log.debug("Failed to initialize native (epoll) transport. "
                    + "Reason: {}. Proceeding with nio.", e.getMessage());
        }
        clientGroup = new NioEventLoopGroup(0, Threads.namedThreads("netty-messaging-event-nio-client-%d", log));
        serverGroup = new NioEventLoopGroup(0, Threads.namedThreads("netty-messaging-event-nio-server-%d", log));
        serverChannelClass = NioServerSocketChannel.class;
        clientChannelClass = NioSocketChannel.class;
    }

    private void startServer(Address address) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(serverGroup, clientGroup);
        b.channel(serverChannelClass);
        b.option(ChannelOption.SO_REUSEADDR, true);
        b.option(ChannelOption.SO_BACKLOG, 128);
        b.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(8 * 1024, 32 * 1024));
        b.childOption(ChannelOption.SO_RCVBUF, 1024 * 1024);
        b.childOption(ChannelOption.SO_SNDBUF, 1024 * 1024);
        b.childOption(ChannelOption.SO_KEEPALIVE, true);
        b.childOption(ChannelOption.TCP_NODELAY, true);
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        b.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(CrazyNettyProtocol.initServerChannelHandler());
            }
        });
        b.localAddress(address.host(), address.port());
        serverChannelFuture = b.bind().syncUninterruptibly();
    }

    public void shutdownServer() {
        if (started.compareAndSet(true, false)) {
            if (serverChannelFuture != null) {
                serverChannelFuture.channel().close().awaitUninterruptibly();
                serverChannelFuture = null;
            }

            if (serverGroup != null) {
                serverGroup.shutdownGracefully();
                serverGroup = null;
            }

            if (clientGroup != null) {
                clientGroup.shutdownGracefully();
                clientGroup = null;
            }

            log.info("successfully shutdown message server");
        }
    }


    public ChannelFuture bootstrapClient(Address address) throws InterruptedException {
        final InetAddress resolvedAddress = address.address(true);
        if (resolvedAddress == null) {
            log.error("Failed to bootstrap client (address "
                    + address.toString() + " cannot be resolved)");
            return null;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(10 * 32 * 1024, 10 * 64 * 1024));
        bootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024);
        bootstrap.option(ChannelOption.SO_SNDBUF, 1024 * 1024);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
        bootstrap.group(clientGroup);
        // TODO: Make this faster:DOUBLE_QUOTES_VALUE_WRAPPERDOUBLE_QUOTES_VALUE_WRAPPER
        // http://normanmaurer.me/presentations/2014-facebook-eng-netty/slides.html#37.0
        bootstrap.channel(clientChannelClass);
        bootstrap.remoteAddress(resolvedAddress, address.port());
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            private AtomicBoolean inited = new AtomicBoolean(false);

            @Override
            protected void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(CrazyNettyProtocol.initClientChannelHandler());
            }
        });
        return bootstrap.connect();
    }
}
