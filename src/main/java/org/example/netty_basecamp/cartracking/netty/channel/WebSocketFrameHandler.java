package org.example.netty_basecamp.cartracking.netty.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger log = LogManager.getLogger(WebSocketFrameHandler.class);

    private final ChannelGroup websocketClients;

    public WebSocketFrameHandler(ChannelGroup websocketClients) {
        this.websocketClients = websocketClients;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        websocketClients.add(ctx.channel());
        log.info("WebSocket 클라이언트 연결 [{}] (총 {}명)", ctx.channel().remoteAddress(), websocketClients.size());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.info("WebSocket 클라이언트 해제 [{}] (총 {}명)", ctx.channel().remoteAddress(), websocketClients.size());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 브라우저에서 보내는 메시지는 무시 — 서버→브라우저 단방향
    }
}
