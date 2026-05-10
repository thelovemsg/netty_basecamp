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
import org.example.netty_basecamp.cartracking.netty.perf.PipelineMetrics;
import org.example.netty_basecamp.cartracking.netty.perf.PipelineTrace;
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
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        String path = extractPath(request.uri());
        String method = request.method().name();
        String body = request.content().toString(CharsetUtil.UTF_8);

        logger.info("→ {} {} {}", method, path, body.isEmpty() ? "" : body);

        // CORS preflight 처리
        if ("OPTIONS".equals(method)) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK);
            setCorsHeaders(response);
            response.headers().set(CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        RouteMatch match = registry.find(method, path);
        if (match == null) {
            sendJson(ctx, NOT_FOUND, Map.of("error", "Not Found"), method, path, null, keepAlive);
            return;
        }

        RequestContext requestContext = RequestContext.builder()
                .method(method)
                .path(path)
                .pathVariables(match.getPathVariables())
                .queryParams(extractQueryParams(request.uri()))
                .headers(extractHeaders(request.headers()))
                .body(body)
                .build();

        PipelineTrace trace = PipelineMetrics.startRest();

        virtualExecutor.submit(() -> {
            trace.mark("VT_WAIT");
            try {
                Object result = match.getEntry().handle(requestContext);
                trace.mark("HANDLER");
                sendJson(ctx, OK, result, method, path, trace, keepAlive);
            } catch (IllegalArgumentException e) {
                trace.end("ERROR");
                sendJson(ctx, BAD_REQUEST, Map.of("error", e.getMessage()), method, path, null, keepAlive);
            } catch (IllegalStateException e) {
                trace.end("ERROR");
                sendJson(ctx, CONFLICT, Map.of("error", e.getMessage()), method, path, null, keepAlive);
            } catch (Exception e) {
                trace.end("ERROR");
                sendJson(ctx, INTERNAL_SERVER_ERROR, Map.of("error", e.getMessage()), method, path, null, keepAlive);
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
        sendJson(ctx, INTERNAL_SERVER_ERROR, Map.of("error", "Internal Server Error"), "?", "?", null, false);
    }

    private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, Object body,
                          String method, String path, PipelineTrace trace, boolean keepAlive) {
        try {
            String json = objectMapper.writeValueAsString(body);
            if (trace != null) trace.end("SERIALIZE");
            logger.info("← {} {} {} {}", method, path, status.code(), json);
            ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
            response.headers().set(CONTENT_TYPE, "application/json");
            response.headers().set(CONTENT_LENGTH, content.readableBytes());
            setCorsHeaders(response);
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.channel().eventLoop().execute(() -> {
                var future = ctx.writeAndFlush(response);
                if (!keepAlive) {
                    future.addListener(ChannelFutureListener.CLOSE);
                }
            });
        } catch (Exception e) {
            logger.error("← {} {} 직렬화 실패", method, path, e);
            ctx.channel().eventLoop().execute(ctx::close);
        }
    }

    private void setCorsHeaders(FullHttpResponse response) {
        response.headers().set("Access-Control-Allow-Origin", "*");
        response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
