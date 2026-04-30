package org.example.netty_basecamp.cartracking.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.netty_basecamp.cartracking.netty.channel.CarTrackingChannelInitializer;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteRegistry;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CarTrackingServer {

    private static final Logger logger = LogManager.getLogger();

    private final int port;
    private final RouteRegistry routeRegistry;
    private final ChannelGroup websocketClients;

    public CarTrackingServer(int port, RouteRegistry routeRegistry, ChannelGroup websocketClients) {
        this.port = port;
        this.routeRegistry = routeRegistry;
        this.websocketClients = websocketClients;
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = CarTrackingServer.class
                .getClassLoader().getResourceAsStream("netty_config.properties")) {
            if (is != null) props.load(is);
        } catch (Exception e) {
            logger.warn("netty_config.properties 로드 실패 — 기본값 사용");
        }
        return props;
    }

    public void start() throws InterruptedException {
        Properties config = loadConfig();
        int bossCount   = Integer.parseInt(config.getProperty("netty.thread.group_count", "1"));
        int workerCount = Integer.parseInt(config.getProperty("netty.thread.child_count", "4"));

        logger.info("CarTracking Server starting on port: {} (boss={}, worker={})", port, bossCount, workerCount);

        // RDBMS / ConcurrentMap 등 블로킹 작업을 EventLoop 스레드에서 분리
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        IoHandlerFactory ioHandlerFactory = NioIoHandler.newFactory();
        EventLoopGroup bossGroup   = new MultiThreadIoEventLoopGroup(bossCount, ioHandlerFactory);
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(workerCount, ioHandlerFactory);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new CarTrackingChannelInitializer(routeRegistry, virtualExecutor, websocketClients));

            logger.info("CarTracking Server started on port: {}", port);
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } finally {
            virtualExecutor.shutdown();
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
