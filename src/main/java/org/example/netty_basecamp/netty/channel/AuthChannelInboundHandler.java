package org.example.netty_basecamp.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.netty_basecamp.netty.auth.AuthInfo;

/**
 * 인증(Authentication) 전담 ChannelHandler.
 * <p>
 * Authorization 헤더의 Bearer 토큰을 디코딩하여 {@link AuthInfo}를 생성하고,
 * {@code Channel.attr(AUTH_KEY)}에 저장한 뒤 다음 핸들러로 전달한다.
 * 토큰이 유효하지 않으면 401 응답 후 연결을 종료한다.
 */
public class AuthChannelInboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LogManager.getLogger();

    public static final AttributeKey<AuthInfo> AUTH_KEY = AttributeKey.valueOf("AUTH_KEY");

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // 인증 헤더 없음 → 익명 사용자로 통과 (공개 API 허용)
            // TODO: 공개/비공개 경로 구분이 필요하면 여기서 path 기반 분기 추가
            logger.debug("No Authorization header — passing as anonymous");
            ctx.fireChannelRead(request.retain());
            return;
        }

        String token = authorization.substring("Bearer ".length());

        // TODO: 실제 토큰 디코딩 로직으로 교체 (JWT 디코딩, 서명 검증 등)
        AuthInfo authInfo = decodeToken(token);

        if (authInfo == null) {
            logger.warn("Invalid token received");
            sendUnauthorized(ctx);
            return;
        }

        // 인증 성공 → Channel.attr()에 AuthInfo 저장
        ctx.channel().attr(AUTH_KEY).set(authInfo);

        logger.debug("Authenticated: {}", authInfo);
        ctx.fireChannelRead(request.retain());
    }

    /**
     * 토큰을 디코딩하여 AuthInfo를 생성한다.
     * TODO: JWT 라이브러리를 사용한 실제 디코딩/서명 검증으로 교체
     */
    private AuthInfo decodeToken(String token) {
        if (token.isEmpty()) {
            return null;
        }
        // 스켈레톤: 토큰을 userId로, 기본 role은 USER
        return new AuthInfo(token, "USER");
    }

    private void sendUnauthorized(ChannelHandlerContext ctx) {
        String json = "{\"error\":\"Unauthorized\"}";
        ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, content
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("AuthHandler error", cause);
        ctx.close();
    }
}
