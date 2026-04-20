package org.example.netty_basecamp.cartracking.netty.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.example.netty_basecamp.cartracking.netty.rest.route.HttpRoutingHandler;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteRegistry;

import java.util.concurrent.ExecutorService;

public class CarTrackingChannelInitializer extends ChannelInitializer<Channel> {

    private final RouteRegistry routeRegistry;
    private final ExecutorService virtualExecutor;

    public CarTrackingChannelInitializer(RouteRegistry routeRegistry, ExecutorService virtualExecutor) {
        this.routeRegistry = routeRegistry;
        this.virtualExecutor = virtualExecutor;
    }

    @Override
    protected void initChannel(Channel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(65536));
        p.addLast(new HttpRoutingHandler(routeRegistry, virtualExecutor));
    }
}
