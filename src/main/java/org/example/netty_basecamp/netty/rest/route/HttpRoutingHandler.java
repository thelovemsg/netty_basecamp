package org.example.netty_basecamp.netty.rest.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.example.netty_basecamp.netty.channel.AuthChannelInboundHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class HttpRoutingHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ExecutorService VIRTUAL_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    private final RouteRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpRoutingHandler(RouteRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = extractPath(request.uri());
        String method = request.method().name();

        RouteMatch match = registry.find(method, path);
        if (match == null) {
            sendJson(ctx, NOT_FOUND, Map.of("error", "Not Found"));
            return;
        }

        // ByteBuf는 channelRead0 리턴 후 release()되므로 EventLoop 스레드에서 미리 복사
        RequestContext requestContext = RequestContext.builder()
                .method(method)
                .path(path)
                .pathVariables(match.getPathVariables())
                .queryParams(extractQueryParams(request.uri()))
                .headers(extractHeaders(request.headers()))
                .body(request.content().toString(CharsetUtil.UTF_8))
                .authInfo(ctx.channel().attr(AuthChannelInboundHandler.AUTH_KEY).get())
                .build();

        // 블로킹 가능한 도메인 로직을 Virtual Thread로 오프로드
        VIRTUAL_EXECUTOR.submit(() -> {
            try {
                Object result = match.getEntry().handle(requestContext);
                sendJson(ctx, OK, result);
            } catch (IllegalArgumentException e) {
                sendJson(ctx, BAD_REQUEST, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                sendJson(ctx, INTERNAL_SERVER_ERROR, Map.of("error", e.getMessage()));
            }
        });
    }

    private String extractPath(String uri) {
        int idx = uri.indexOf('?');
        return idx == -1 ? uri : uri.substring(0, idx);
    }

    private Map<String, String> extractQueryParams(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, String> params = new HashMap<>();
        decoder.parameters().forEach((k, v) -> params.put(k, v.getFirst()));
        return params;
    }

    private Map<String, String> extractHeaders(HttpHeaders headers) {
        Map<String, String> headerMap = new HashMap<>();
        headers.forEach(entry -> headerMap.put(entry.getKey(), entry.getValue()));
        return headerMap;
    }

    private void sendJson(ChannelHandlerContext ctx,
                           HttpResponseStatus status, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, content
            );
            response.headers().set(CONTENT_TYPE, "application/json");
            response.headers().set(CONTENT_LENGTH, content.readableBytes());
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            ctx.close();
        }
    }
}
