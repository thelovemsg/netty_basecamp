package org.example.netty_basecamp.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.netty_basecamp.netty.channel.CustomChannelInitializer;
import org.example.netty_basecamp.netty.rest.RouteRegistry;
import org.example.netty_basecamp.netty.util.ServerUtil;

public class NettyBootcampServer {
    final Logger logger = LogManager.getLogger();

    private final int port;
    private final SslContext sslCtx = ServerUtil.buildZeroTrustSslContext();
    private final RouteRegistry routeRegistry;

    public NettyBootcampServer(int port, RouteRegistry routeRegistry) throws Exception {
        this.port = port;
        this.routeRegistry = routeRegistry;
    }

    public void start() throws InterruptedException {
        logger.info("Netty Server starting on port: {}", port);
        // 1. EventLoopGroup 생성 (Boss와 Worker)
        IoHandlerFactory ioHandlerFactory = NioIoHandler.newFactory();

        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(4, ioHandlerFactory); // 기본값: CPU 코어 수 * 2
        try {
            // 2. ServerBootstrap 설정
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // NIO 기반의 채널 사용
                    .option(ChannelOption.SO_BACKLOG, 128) // TCP 연결 대기열 설정
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // Worker 채널의 KeepAlive 설정
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new CustomChannelInitializer(sslCtx, routeRegistry))
            ;

            // 4. 서버 바인딩 및 동기화
            logger.info("Netty Server started on port: {}", port);
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } finally {
            // 6. 우아한 종료 (Graceful Shutdown)
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}