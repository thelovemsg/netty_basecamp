package org.example.netty_basecamp.basic.netty.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import org.example.netty_basecamp.basic.netty.rest.route.HttpRoutingHandler;
import org.example.netty_basecamp.basic.netty.rest.route.RouteRegistry;

public class CustomChannelInitializer extends ChannelInitializer<Channel>{

    private final SslContext sslCtx;
    private final RouteRegistry routeRegistry;

    public CustomChannelInitializer(SslContext sslCtx, RouteRegistry routeRegistry) {
        this.sslCtx = sslCtx;
        this.routeRegistry = routeRegistry;
    }


    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();

        // 1. HTTP 요청/응답 인코딩·디코딩
        p.addLast(new HttpServerCodec());
        // 2. HTTP 메시지 조각을 하나로 합침
        p.addLast(new HttpObjectAggregator(65536));

//        p.addLast(new AuthChannelInboundHandler());
        // 3. 우리가 만들 라우팅 핸들러
        p.addLast(new HttpRoutingHandler(routeRegistry));
        // 4. ???
    }
}
