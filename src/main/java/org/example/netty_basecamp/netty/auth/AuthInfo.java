package org.example.netty_basecamp.netty.auth;

/**
 * 인증 결과를 담는 불변 VO.
 * AuthChannelInboundHandler가 토큰 디코딩 후 생성하여 Channel.attr(AUTH_KEY)에 저장한다.
 */
public class AuthInfo {

    private final String userId;
    private final String role;

    public AuthInfo(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "AuthInfo{userId='" + userId + "', role='" + role + "'}";
    }
}
