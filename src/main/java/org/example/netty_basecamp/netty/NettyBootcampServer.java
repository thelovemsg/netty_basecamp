package org.example.netty_basecamp.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.netty_basecamp.netty.channel.CustomChannelInitializer;
import org.example.netty_basecamp.netty.util.ServerUtil;

public class NettyBootcampServer {
    final Logger logger = LogManager.getLogger();

    private final int port;
    final SslContext sslCtx = ServerUtil.buildZeroTrustSslContext();

    public NettyBootcampServer(int port) throws Exception {
        this.port = port;
    }

    public void start() throws InterruptedException {
        logger.info("Netty Server starting on port: {}", port);
        // 1. EventLoopGroup 생성 (Boss와 Worker)
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 기본값: CPU 코어 수 * 2
        try {
            // 2. ServerBootstrap 설정
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // NIO 기반의 채널 사용
                    .option(ChannelOption.SO_BACKLOG, 128) // TCP 연결 대기열 설정
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // Worker 채널의 KeepAlive 설정
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new CustomChannelInitializer(sslCtx))
            ;

            // 4. 서버 바인딩 및 동기화
            ChannelFuture f = b.bind(port).sync();
            logger.info("Netty Server started on port: {}", port);
            f.channel().closeFuture().sync();
        } finally {
            // 6. 우아한 종료 (Graceful Shutdown)
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}