package org.example.netty_basecamp.cartracking.netty.rest.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.netty.util.CharsetUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class HttpRoutingHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LogManager.getLogger();

    private final RouteRegistry registry;
    private final ExecutorService virtualExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpRoutingHandler(RouteRegistry registry, ExecutorService virtualExecutor) {
        this.registry = registry;
        this.virtualExecutor = virtualExecutor;
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

        RequestContext requestContext = RequestContext.builder()
                .method(method)
                .path(path)
                .pathVariables(match.getPathVariables())
                .queryParams(extractQueryParams(request.uri()))
                .headers(extractHeaders(request.headers()))
                .body(request.content().toString(CharsetUtil.UTF_8))
                .build();

        virtualExecutor.submit(() -> {
            try {
                Object result = match.getEntry().handle(requestContext);
                sendJson(ctx, OK, result);
            } catch (IllegalArgumentException e) {
                sendJson(ctx, BAD_REQUEST, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                sendJson(ctx, CONFLICT, Map.of("error", e.getMessage()));
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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Unhandled exception in pipeline", cause);
        sendJson(ctx, INTERNAL_SERVER_ERROR, Map.of("error", "Internal Server Error"));
    }

    private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
            response.headers().set(CONTENT_TYPE, "application/json");
            response.headers().set(CONTENT_LENGTH, content.readableBytes());
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            ctx.close();
        }
    }
}
