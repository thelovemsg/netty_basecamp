package org.example.netty_basecamp.netty.util;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

public final class ServerUtil {

    private static final boolean SSL = System.getProperty("ssl") != null;

    private ServerUtil() {
    }

    public static SslContext buildZeroTrustSslContext() throws Exception {
        if (!SSL) {
            return null;
        }

        // 1. 서버 자신의 신분증 (Server Identity) 생성
        // Bouncy Castle을 이용하여 런타임에 자체 서명 인증서를 자동 생성합니다.
        SelfSignedCertificate serverCert = new SelfSignedCertificate("localhost-server");

        // 2. 클라이언트의 신분증을 검증할 '신뢰할 수 있는 기관(CA)'의 인증서
        // (로컬 테스트 환경이므로 서버 자신의 인증서를 임시 CA로 취급합니다)
        SelfSignedCertificate trustedClientCaCert = serverCert;

        return SslContextBuilder
                // [기존 로직] 서버가 자신의 인증서와 개인키를 세팅
                .forServer(serverCert.certificate(), serverCert.privateKey())

                // [제로 트러스트 1] 클라이언트에게 인증서 제출을 강제
                .clientAuth(ClientAuth.REQUIRE)

                // [제로 트러스트 2] 클라이언트가 제출한 인증서가 맞는지 검증
                .trustManager(trustedClientCaCert.certificate())

                .build();
    }
}