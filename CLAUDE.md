# Netty Basecamp — CLAUDE.md

## 프로젝트 목적

**"Spring 없이, 순수 Netty + DDD 아키텍처가 실제로 동작 가능한가?"** 를 검증하는 학습 프로젝트.

선착순 쿠폰 발급 시스템(고트래픽 "오픈런" 시나리오)을 도메인으로 삼아, Netty I/O 레이어 위에서 DDD 원칙이 얼마나 깔끔하게 적용되는지 실험한다.

---

## 학습 로드맵

| Phase | 목표 | 상태 |
|-------|------|------|
| 1 | TDD 기반 엔티티/VO 모델링 | 진행 중 |
| 2 | RDBMS 비관적/낙관적 락으로 동시성 제어 | 예정 |
| 3 | Redis 분산 락 + 큐 관리 | 예정 |
| 4 | 이벤트 드리븐 비동기 워크플로우 (메시지 유실 대응) | 예정 |

---

## 아키텍처 개요

```
src/main/java/org/example/netty_basecamp/
├── domain/                  # 순수 Java DDD 레이어 (프레임워크 의존 없음)
│   ├── common/
│   │   ├── service/         # NumberGenerator, TimeGenerator, UuidGenerator
│   │   └── vo/              # Money (BigDecimal), Inventory (total/used)
│   ├── coupon/
│   │   ├── domain/          # Coupon (AR), IssuedCoupon, CouponCreate/Update
│   │   │   ├── service/     # CouponIssueDomainService
│   │   │   └── vo/          # IssuedCouponStatusEnum (UNUSED/USED/EXPIRED)
│   │   ├── application/     # CouponIssueApplicationService
│   │   └── infrastructure/  # CouponRepository (interface)
│   ├── fare/
│   │   ├── domain/          # Fare (AR), FareStatusEnum, FareTypeEnum
│   │   │   ├── policy/      # FarePolicy, FarePolicyTypeEnum, CalculationBasisEnum
│   │   │   ├── calculation/ # FareCalculationPipeline, FareCalculationContext,
│   │   │   │                # FarePolicyStrategy (interface), FarePolicyStrategyRegistry
│   │   │   │   └── strategies/ # FixedAmountDiscount, LongStayDiscount, WeekendSurcharge
│   │   │   └── service/     # FareCalculationDomainService
│   │   └── infrastructure/  # FareRepository, FarePolicyRepository (interface)
│   └── member/
│       ├── application/     # MemberApplicationService
│       ├── domain/          # Members (AR), MembersCreate/Update
│       └── infrastructure/  # MemberRepository (interface)
└── netty/                   # Netty 인프라 레이어
    ├── NettyBootcampServer.java   # Boss(1) + Worker(4) NIO EventLoopGroup
    ├── channel/
    │   ├── CustomChannelInitializer.java   # HttpServerCodec → Aggregator → RoutingHandler
    │   ├── CustomChannelInboundHandler.java
    │   └── CustomChannelOutboundHandler.java
    ├── repository/
    │   └── InMemoryMemberRepository.java   # ConcurrentHashMap 기반 구현체
    ├── rest/
    │   ├── HttpRoutingHandler.java   # SimpleChannelInboundHandler<FullHttpRequest>
    │   ├── RouteRegistry.java        # 정확 매칭 + path variable 패턴 매칭
    │   ├── RouteEntry.java           # HttpMethod + path + BiFunction + path variable 추출
    │   ├── AppConfig.java            # 도메인별 RouteConfig 조립 진입점
    │   └── config/
    │       └── MemberRouteConfig.java  # Member DI 조립 + 라우트 정의
    └── util/
        └── ServerUtil.java           # Zero Trust TLS, Bouncy Castle 자체 서명 인증서
```

---

## 핵심 설계 원칙

### DDD 레이어 (domain/)
- **Aggregate Root**: `Coupon`, `Fare`, `Members` — 내부 불변식(invariant)을 스스로 보호
- **Value Object**: `Money`, `Inventory` — 불변(immutable), 동등성은 값으로 비교
- **Domain Service**: 단일 Aggregate를 넘는 도메인 로직 (`CouponIssueDomainService`, `FareCalculationDomainService`)
- **Application Service**: 도메인 객체 오케스트레이션 + 트랜잭션 경계 (`CouponIssueApplicationService`, `MemberApplicationService`)
- **Repository**: 인터페이스만 domain 레이어에 존재, 구현은 `netty/repository/`에 배치

### Strategy Pattern (Fare Calculation)
```
FarePolicyStrategy (interface)
    └── FarePolicyStrategyRegistry  →  FareCalculationPipeline  →  FareCalculationContext (immutable)
            ├── FixedAmountDiscountStrategy
            ├── LongStayDiscountStrategy
            └── WeekendSurchargeStrategy
```
정책 타입별로 Registry에서 Strategy를 조회하고, Pipeline이 우선순위 순으로 실행한다.

### Netty HTTP 라우팅
```
Request → HttpServerCodec → HttpObjectAggregator → HttpRoutingHandler
                                                         └── RouteRegistry.find(method, path, pathParams)
                                                               ├── 1순위: 정확 매칭 (exactRoutes)
                                                               └── 2순위: path variable 패턴 매칭 (/api/members/{id})
                                                                     └── RouteEntry.handle(params, body)
                                                                           └── ApplicationService 호출
```
- 도메인별 `XxxRouteConfig.routes()` → `AppConfig`에서 `RouteRegistry`에 일괄 등록
- Spring 없이 `BiFunction<Map<String, String>, String, Object>` 람다로 라우트 핸들러를 등록한다.

---

## 기술 스택

| 기술 | 버전          | 용도 |
|------|-------------|------|
| Netty | 4.2.8.Final | 비동기 HTTP 서버 |
| Java | 21          | 순수 DDD (프레임워크 없음) |
| Jackson | 2.18.3      | JSON 직렬화/역직렬화 |
| Bouncy Castle | 1.79        | TLS 자체 서명 인증서 생성 |
| LMAX Disruptor | 4.0.0       | 비동기 이벤트 큐 (Log4j2 Async) |
| Log4j2 | 2.25.3      | 로깅 (RollingFile, 30일 보관) |

---

## 주요 파일 위치

| 파일 | 역할 |
|------|------|
| `netty/rest/AppConfig.java` | 도메인별 RouteConfig 조립 진입점 |
| `netty/rest/config/MemberRouteConfig.java` | Member DI 조립 + CRUD 라우트 정의 |
| `netty/rest/HttpRoutingHandler.java` | HTTP 요청 처리 핵심 |
| `domain/member/application/MemberApplicationService.java` | 회원 CRUD 유스케이스 |
| `domain/coupon/application/CouponIssueApplicationService.java` | 쿠폰 발급 유스케이스 |
| `domain/fare/domain/calculation/FareCalculationPipeline.java` | 요금 계산 파이프라인 |
| `NettyBaseCampApplication.java` | 애플리케이션 진입점 (port 8080) |
| `resources/netty_config.properties` | Boss/Worker 스레드 수 설정 |

---

## 검증하고 싶은 것

1. **DDD Aggregate가 Netty EventLoop 스레드 모델과 충돌하지 않는가?**
2. **Repository 인터페이스만으로 도메인을 완전히 격리할 수 있는가?**
3. **ApplicationService가 Spring DI 없이도 Netty 핸들러와 자연스럽게 연결되는가?**
4. **고트래픽(선착순 쿠폰)에서 Inventory VO의 원자성을 어떻게 보장할 것인가?*

## Reference Docs

Details specs in `docs/` - Claude reads these on-demand hwen relavant:
- `docs/api-spec.md` - API endpoints, schemas, enums
- `docs/commit-template.md` - commit template
- `docs/decisions.md` - working history
- `docs/todo/` - 오늘 날짜에 해당하는 todo 파일을 읽어서 작업을 해야함. 
