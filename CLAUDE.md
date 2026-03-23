# Netty Basecamp — CLAUDE.md

## 절대 규칙

- `domains/` 패키지에 Netty, Jackson 등 프레임워크 import 금지 — 순수 Java만 허용
- EventLoop 스레드에서 블로킹 호출(JDBC, Thread.sleep 등) 금지
- Aggregate Root(`Coupon`, `Fare`, `Members`)의 불변식은 AR 내부에서만 보호 — 외부에서 직접 상태 변경 금지
- Repository 인터페이스는 `domains/*/domain/`에, 구현체는 `infrastructure/`에 배치 — 역전 방향 위반 금지

---

## 아키텍처

### 레이어 규칙
```
domains/  → 순수 Java DDD (프레임워크 무의존)
netty/    → Netty 인프라 (HTTP, TLS, 인증, 라우팅)
```
- 의존 방향: `netty/ → domains/` (단방향, 역방향 금지)
- DI 조립: `XxxRouteConfig`에서 수동 생성자 주입 → `AppConfig`에서 일괄 등록

### HTTP 파이프라인 흐름
```
HttpServerCodec → HttpObjectAggregator → AuthChannelInboundHandler → HttpRoutingHandler
                                              │                            └→ RouteRegistry → Controller → ApplicationService
                                              └→ 인증 (AuthInfo → Channel Attribute)
```

### 기술 스택

| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 21 | 순수 DDD |
| Netty | 4.2.8.Final | 비동기 HTTP 서버 |
| Jackson | 2.18.3 | JSON 직렬화/역직렬화 |
| Bouncy Castle | 1.79 | TLS 자체 서명 인증서 |
| Log4j2 + Disruptor | 2.25.3 / 4.0.0 | 비동기 로깅 |

---

## 빌드 / 테스트

```bash
./gradlew build          # 빌드
./gradlew test           # 테스트 실행
```
- 서버 진입점: `NettyBaseCampApplication.java` (port 8080, HTTPS)
- 스레드 설정: `resources/netty_config.properties` (Boss 1 / Worker 4)

---

## 도메인 컨텍스트

### 프로젝트 목적
"Spring 없이, 순수 Netty + DDD 아키텍처가 실제로 동작 가능한가?"를 검증하는 학습 프로젝트.
선착순 쿠폰 발급(고트래픽 "오픈런" 시나리오)을 도메인으로 사용.

### 도메인 모델
- **Member**: 회원 CRUD — 현재 InMemory 구현
- **Coupon**: 쿠폰 생성 + 선착순 발급 — `Inventory` VO로 재고 관리, `IssuedCoupon`으로 발급 이력 추적
- **Fare**: 요금 계산 — Strategy 패턴으로 정책(`FarePolicy`)을 Pipeline에서 우선순위 순 실행

### 학습 로드맵

| Phase | 목표 | 상태 |
|-------|------|------|
| 1 | TDD 기반 엔티티/VO 모델링 | 진행 중 |
| 2 | RDBMS 비관적/낙관적 락 동시성 제어 | 예정 |
| 3 | Redis 분산 락 + 큐 관리 | 예정 |
| 4 | 이벤트 드리븐 비동기 워크플로우 | 예정 |

---

## 코딩 컨벤션

### 커밋 메시지
```
<type>(<scope>): <한글 과거형, 왜 했는지 중심>

1. 구체적 변경사항
2. ...
```
- type: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
- scope: `route`, `member`, `coupon`, `fare`, `handler`, `config` 등
- 상세 템플릿: `.claude/skills/work/commit_template.md`

### DDD 패턴
- **Value Object**: 불변, 값으로 동등성 비교 (`Money`, `Inventory`)
- **Domain Service**: 단일 AR을 넘는 도메인 로직
- **Application Service**: 오케스트레이션 + 트랜잭션 경계
- **라우트 핸들러**: `Function<RequestContext, Object>` 람다로 등록

---

## Reference Docs

상세 스펙은 `.claude/skills/work/`에 위치 — 필요 시 on-demand로 참조:
- `api-spec.md` — API endpoints, schemas, enums
- `commit_template.md` — 커밋 메시지 상세 템플릿
- `architecture.md` — 아키텍처 상세
- `decisions.md` — 설계 결정 이력 (ADR)
- `todo/` — 오늘 날짜에 해당하는 todo 파일을 읽어서 작업을 해야함.
