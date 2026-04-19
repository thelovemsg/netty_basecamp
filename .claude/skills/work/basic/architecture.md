# 시스템 아키텍처

업데이트 히스토리
- 2026-04-16 초안 작성

---

## 프로젝트 목적

"Spring 없이, 순수 Netty + DDD 아키텍처가 실제로 동작 가능한가?"를 검증하는 학습 프로젝트.
선착순 쿠폰 발급(고트래픽 "오픈런" 시나리오)을 도메인으로 시작, 차량 추적 시스템으로 확장 중.

---

## 레이어 구조

```
┌──────────────────────────────────────────────────────────────┐
│  netty/          Netty 인프라 (HTTP, TLS, 인증, 라우팅)        │
│  mqtt/           MQTT 인프라 (차량 텔레미트리 수신/발행)         │
│  simulator/      가상 차량 시뮬레이터 (독립 스레드)              │
├──────────────────────────────────────────────────────────────┤
│  domains/        순수 Java DDD (프레임워크 무의존)              │
│    member/       회원 관리                                    │
│    coupon/       쿠폰 발급 (선착순 오픈런 시나리오)              │
│    fare/         요금 계산 (Strategy 파이프라인)                │
│    vehicle/      차량 추적 (Trip, LocationSnapshot)           │
└──────────────────────────────────────────────────────────────┘
```

**의존 방향**: `netty/ → domains/`, `mqtt/ → domains/` (단방향, 역방향 금지)

**DI 조립**: `XxxRouteConfig`에서 수동 생성자 주입 → `AppConfig`에서 일괄 등록

---

## HTTP 파이프라인 흐름

```
HttpServerCodec
  → HttpObjectAggregator
  → AuthChannelInboundHandler     (인증: 토큰 디코딩 → AuthInfo → Channel Attribute)
  → HttpRoutingHandler            (라우팅: RouteRegistry 매칭 → Controller)
       └→ Virtual Thread 오프로드  (JDBC 블로킹 방지, ADR-009)
            └→ Controller → ApplicationService → Domain
```

### 라우트 등록 흐름

```
AppConfig.initRoutes()
  └→ XxxRouteConfig.routes() → List<RouteEntry>
       └→ RouteRegistry
            ├── exactRoutes (HashMap, O(1))    → "GET /api/members"
            └── paramRoutes (List)             → "/api/members/{id}"
```

---

## MQTT + WebSocket 파이프라인 흐름 (차량 추적)

```
VehicleSimulator
  → MQTT publish(vehicle/{id}/telemetry)
  → Broker (Mosquitto)
  → VehicleTelemetrySubscriber (mqtt/)
       ├→ VehicleApplicationService.handleTelemetry()  (도메인 상태 갱신)
       └→ VehicleLocationBroadcaster (netty/websocket/) (브라우저 push)
            └→ WebSocket → Leaflet.js 지도 마커 갱신
```

---

## 도메인별 책임

| 도메인 | AR | 핵심 불변식 |
|--------|----|-----------:|
| Member | Members | - |
| Coupon | Coupon | 재고(Inventory) 초과 발급 불가 |
| Fare | Fare, FarePolicy | 정책 우선순위 중복 불가 |
| Vehicle | Vehicle, Trip | ON_TRIP 중 재출발 불가, 완료된 Trip에 Snapshot 추가 불가 |

### 패키지 패턴 (도메인별 공통)

```
domains/{domain}/
  domain/
    {AR}.java                  ← Aggregate Root
    {AR}Repository.java        ← 인터페이스 (Port, 의존성 역전)
    vo/                        ← Value Objects (불변)
    service/                   ← Domain Services (단일 AR을 넘는 도메인 로직)
  application/
    {Domain}ApplicationService.java   ← 오케스트레이션 + 트랜잭션 경계
  infrastructure/
    InMemory{AR}Repository.java       ← 구현체 (Adapter)
```

---

## 기술 스택

| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 21 | 순수 DDD, Virtual Thread |
| Netty | 4.2.8.Final | 비동기 HTTP/WebSocket 서버 |
| Eclipse Paho | 1.2.5 | MQTT 클라이언트 |
| Mosquitto | - | MQTT Broker (Docker) |
| Jackson | 2.18.3 | JSON 직렬화/역직렬화 |
| Bouncy Castle | 1.79 | TLS 자체 서명 인증서 |
| Log4j2 + Disruptor | 2.25.3 / 4.0.0 | 비동기 로깅 |

---

## 주요 설계 결정 요약

| ADR | 결정 | 이유 |
|-----|------|------|
| ADR-001 | `RequestContext`로 핸들러 시그니처 통일 | BiFunction 가독성/확장성 문제 |
| ADR-003 | 자체 필터 체인 제거, Netty 파이프라인으로 통일 | Netty가 이미 필터 체인 |
| ADR-006 | Repository 인터페이스를 `domain/`으로 이동 | 의존성 역전 (인프라 → 도메인) |
| ADR-007 | 리플렉션 없이 명시적 타입 변환 유지 | JIT 인라이닝, GC 최적화, 프로젝트 목적 |
| ADR-008 | 인증은 ChannelHandler, 인가는 Controller | 단일 책임, AUTH_KEY 단일 전달 |
| ADR-009 | Virtual Thread로 블로킹 오프로드 | Netty 4.2 EventExecutorGroup API 제거 |

상세 내용: `decisions.md`
