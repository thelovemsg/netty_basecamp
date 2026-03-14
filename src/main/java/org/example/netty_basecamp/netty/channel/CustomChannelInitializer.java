package org.example.netty_basecamp.netty.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;

public class CustomChannelInitializer extends ChannelInitializer<Channel>{

    private final SslContext sslCtx;

    public CustomChannelInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();

        // HTTP 요청/응답 인코딩·디코딩
        p.addLast(new HttpServerCodec());
        // HTTP 메시지 조각을 하나로 합침
        p.addLast(new HttpObjectAggregator(65536));
        // 우리가 만들 라우팅 핸들러
    }
}
