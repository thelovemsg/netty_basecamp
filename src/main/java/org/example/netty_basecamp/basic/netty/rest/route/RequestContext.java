package org.example.netty_basecamp.basic.netty.rest.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.netty_basecamp.basic.netty.auth.AuthInfo;

import java.util.Collections;
import java.util.Map;

public class RequestContext {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String method;
    private final String path;
    private final Map<String, String> pathVariables;
    private final Map<String, String> queryParams;
    private final Map<String, String> headers;
    private final String body;
    private final AuthInfo authInfo;

    private RequestContext(Builder builder) {
        this.method = builder.method;
        this.path = builder.path;
        this.pathVariables = Collections.unmodifiableMap(builder.pathVariables);
        this.queryParams = Collections.unmodifiableMap(builder.queryParams);
        this.headers = Collections.unmodifiableMap(builder.headers);
        this.body = builder.body;
        this.authInfo = builder.authInfo;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }

    public String pathVariable(String name) {
        return pathVariables.get(name);
    }

    public Long pathVariableAsLong(String name) {
        return Long.parseLong(pathVariables.get(name));
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public String queryParam(String name) {
        return queryParams.get(name);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public String getBody() {
        return body;
    }

    public AuthInfo getAuthInfo() {
        return authInfo;
    }

    public boolean isAuthenticated() {
        return authInfo != null;
    }

    public <T> T readBody(Class<T> clazz) {
        try {
            return objectMapper.readValue(body, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String method = "";
        private String path = "";
        private Map<String, String> pathVariables = Map.of();
        private Map<String, String> queryParams = Map.of();
        private Map<String, String> headers = Map.of();
        private String body = "";
        private AuthInfo authInfo;

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder pathVariables(Map<String, String> pathVariables) {
            this.pathVariables = pathVariables;
            return this;
        }

        public Builder queryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder authInfo(AuthInfo authInfo) {
            this.authInfo = authInfo;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }
}
