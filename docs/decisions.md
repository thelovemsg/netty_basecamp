# Architectural Decision Records

프로젝트의 주요 의사결정 사항을 기록한 문서. Second Brain으로 활용하여 과거 결정의 맥락과 근거를 빠르게 참조할 수 있도록 함.

업데이트 히스토리
- 2026-03-15 생성

---

## 목차
1. [ADR-001: RequestContext 도입 — BiFunction 핸들러 제거](#adr-001)
2. [ADR-002: RouteMatch 도입 — RouteRegistry 매칭 결과 정리](#adr-002)
3. [ADR-003: AuthFilter 최소 구조 도입](#adr-003)
4. [ADR-004: EventLoop 블로킹 이슈 — Phase 2 진입 시 해결 필요](#adr-004)
5. [ADR-005: Controller 레이어 분리 — Inbound Adapter 도입](#adr-005)
6. [ADR-006: Repository 인터페이스를 domain 패키지로 이동 — 의존성 역전 적용](#adr-006)
7. [ADR-007: 리플렉션 없이 명시적 타입 변환 유지](#adr-007)
8. [ADR-008: 인증은 파이프라인 ChannelHandler, 인가는 Controller — AUTH_KEY 단일 전달](#adr-008)

---

## ADR-001: RequestContext 도입 — BiFunction 핸들러 제거 {#adr-001}
**날짜**: 2026-03-15

### 문제
- 핸들러 시그니처가 `BiFunction<Map<String, String>, String, Object>` — 파라미터가 뭘 의미하는지 시그니처만 봐서는 알 수 없음
- header, queryParam 등 새 요청 정보가 필요해지면 시그니처 자체를 변경해야 함
- 각 RouteConfig마다 `ObjectMapper`를 직접 들고 있어서 JSON 파싱이 분산됨

### 결정
- `RequestContext` 불변 객체를 만들어 요청의 모든 정보(method, path, pathVariables, queryParams, headers, body)를 하나로 묶음
- 핸들러 시그니처를 `Function<RequestContext, Object>`로 변경
- `readBody(Class<T>)`, `pathVariableAsLong(String)` 등 편의 메서드를 RequestContext에 집중

### Before
```java
new RouteEntry(HttpMethod.GET, "/api/members/{id}",
    (params, body) -> memberService.findById(Long.parseLong(params.get("id"))))
```

### After
```java
new RouteEntry(HttpMethod.GET, "/api/members/{id}",
    ctx -> memberService.findById(ctx.pathVariableAsLong("id")))
```

### 효과
- 가독성 향상 — 의도가 명확한 메서드 호출
- 확장성 — 새 요청 정보 추가 시 핸들러 시그니처 변경 불필요
- JSON 파싱 일원화 — `ctx.readBody()`로 통일

---

## ADR-002: RouteMatch 도입 — RouteRegistry 매칭 결과 정리 {#adr-002}
**날짜**: 2026-03-15

### 문제
- `RouteRegistry.find(method, path, pathParams)` — 호출 측에서 `new HashMap<>()`을 만들어서 넘겨야 했음
- 매칭 결과(RouteEntry)와 추출된 pathVariables가 분리되어 있어 조립 로직이 산재

### 결정
- `RouteMatch` 객체 도입 — `RouteEntry` + 추출된 `pathVariables`를 하나로 묶음
- `find()` 반환 타입을 `RouteEntry` → `RouteMatch`로 변경
- 외부에서 HashMap을 넘기는 오버로드 제거

### Before
```java
Map<String, String> pathParams = new HashMap<>();
RouteEntry entry = registry.find(method, path, pathParams);
// pathParams와 entry가 따로 논다
```

### After
```java
RouteMatch match = registry.find(method, path);
// match.getEntry() + match.getPathVariables() 하나로 묶임
```

---

## ADR-003: 자체 필터 체인 제거 — Netty 파이프라인으로 통일 {#adr-003}
**날짜**: 2026-03-15
**수정**: 2026-03-15

### 초기 결정
- `RouteFilter` 인터페이스 + `AuthFilter` 구현체로 자체 필터 체인을 만들었음

### 재검토
- Netty 파이프라인 자체가 필터 체인 역할을 함 (`ChannelHandler` 추가/제거)
- Netty를 배우는 프로젝트에서 Netty 파이프라인을 안 쓰고 자체 필터 체인을 만드는 건 방향이 맞지 않음

### 변경된 결정
- `RouteFilter`, `AuthFilter`, `UnauthorizedException` 제거
- 인증 같은 cross-cutting concern이 필요하면 **ChannelHandler를 파이프라인에 추가**하는 방식으로 처리
- `HttpRoutingHandler`는 라우팅과 핸들러 실행에만 집중

### Netty 파이프라인 방식 예시
```
HttpServerCodec → Aggregator → [AuthHandler] → HttpRoutingHandler
                                    ↑ ChannelHandler를 넣고 빼기만 하면 됨
```

### 판단 근거
- Netty의 파이프라인이 곧 필터 체인 — 같은 기능을 이중으로 만들 필요 없음
- 파이프라인 핸들러는 넣고 빼기가 자유롭고, Netty가 스레드 안전성을 보장함

---

## ADR-004: EventLoop 블로킹 이슈 — Phase 2 진입 시 해결 필요 {#adr-004}
**날짜**: 2026-03-15
**상태**: 대기 (Phase 2 진입 시 착수)

### 문제
현재 `HttpRoutingHandler.channelRead0()`에서 도메인 로직이 **Netty Worker EventLoop 스레드에서 직접 실행**된다.

```
Worker EventLoop 스레드 (4개)
  └── channelRead0()
        ├── RequestContext 생성
        ├── Filter 실행
        ├── match.getEntry().handle(ctx)
        │     └── ApplicationService → Repository.save()  ← 블로킹 지점
        └── sendJson()
```

- **현재(Phase 1)**: `InMemoryMemberRepository`(ConcurrentHashMap)라서 논블로킹, 문제 없음
- **Phase 2(RDBMS)부터**: JDBC 호출이 50~200ms 블로킹 → Worker 스레드 4개가 DB I/O에 점유됨 → 전체 서버 처리량 급감
- Netty의 **"EventLoop를 절대 블로킹하지 마라"** 원칙 위반

### 왜 위험한가
| 상황 | Worker 스레드 상태 | 결과 |
|------|-------------------|------|
| InMemory (현재) | ~0ms, 즉시 반환 | 정상 |
| JDBC 단건 조회 | ~10ms 블로킹 | 체감 없음 |
| JDBC 트랜잭션 (락 포함) | 50~200ms 블로킹 | Worker 4개 고갈 → 신규 요청 큐잉 |
| 선착순 쿠폰 오픈런 | 비관적 락 대기 | Worker 전부 멈춤 → 서버 무응답 |

### 해결 방향 (Phase 2 착수 시)

**방법 1: `DefaultEventExecutorGroup` 사용**
```java
// CustomChannelInitializer
EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(16);
p.addLast(businessGroup, new HttpRoutingHandler(registry));
```
- Netty가 제공하는 가장 간단한 방법
- `HttpRoutingHandler`의 `channelRead0()`가 별도 스레드풀에서 실행됨
- 코드 변경 최소 (1줄 추가)

**방법 2: 핸들러 내부에서 직접 오프로드**
```java
// HttpRoutingHandler 내부
private final ExecutorService blockingPool = Executors.newFixedThreadPool(16);

protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
    // RequestContext 생성은 EventLoop에서 (가벼운 작업)
    RequestContext requestContext = buildContext(request);

    blockingPool.submit(() -> {
        Object result = match.getEntry().handle(requestContext);
        ctx.writeAndFlush(buildResponse(result));  // EventLoop로 돌아감
    });
}
```
- 더 세밀한 제어 가능 (어떤 라우트만 오프로드 등)
- 코드 변경 多

### 판단
- Phase 1에서는 **현 상태 유지** (InMemory라 문제 없음)
- Phase 2 RDBMS 도입 시 **방법 1(`DefaultEventExecutorGroup`)로 시작** → 부족하면 방법 2로 전환
- `CustomChannelInboundHandler`, `CustomChannelOutboundHandler`는 현재 빈 껍데기이며 파이프라인 위치상 호출도 안 됨 → Phase 2 시점에 역할 재정의 또는 삭제 결정

### 관련 파일
- `netty/channel/CustomChannelInitializer.java` — 파이프라인 구성
- `netty/rest/route/HttpRoutingHandler.java` — 블로킹 발생 지점
- `netty/channel/CustomChannelInboundHandler.java` — 빈 껍데기 (미사용)
- `netty/channel/CustomChannelOutboundHandler.java` — 빈 껍데기 (미사용)

---

## ADR-005: Controller 레이어 분리 — Inbound Adapter 도입 {#adr-005}
**날짜**: 2026-03-15

### 문제
- `MemberRouteConfig`에서 DI 조립 + 라우트 등록 + 요청 파싱/변환을 람다 안에서 모두 처리
- 헥사고날 아키텍처 관점에서 **Inbound Adapter(Controller)** 레이어가 부재
- 핸들러 로직이 커지면(검증, 응답 변환, 에러 매핑) 람다 안에서 감당 불가

### 결정
- `controller/` 패키지에 `MemberController` 도입 — **HTTP ↔ ApplicationService 변환** 책임만 담당
- `MemberRouteConfig`는 **DI 조립 + RouteEntry 등록**만 담당
- 메서드 레퍼런스(`controller::create`)로 라우트 등록

### Before
```java
// MemberRouteConfig — DI + 라우트 + 요청 파싱이 한 곳에
new RouteEntry(HttpMethod.PUT, "/api/members/{id}",
    ctx -> memberService.update(
            ctx.pathVariableAsLong("id"),
            ctx.readBody(MembersUpdate.class)))
```

### After
```java
// MemberController — Inbound Adapter
public Object update(RequestContext ctx) {
    return memberService.update(
            ctx.pathVariableAsLong("id"),
            ctx.readBody(MembersUpdate.class));
}

// MemberRouteConfig — 조립 + 등록만
new RouteEntry(HttpMethod.PUT, "/api/members/{id}", controller::update)
```

### 헥사고날 매핑
```
[Adapter (In)]        [Port (In)]              [Domain]
MemberController → MemberApplicationService → Members (AR)
      ↑                                           ↑
 RequestContext 변환                        도메인 불변식 보호
```

### 효과
- 책임 분리 — Controller(변환), RouteConfig(조립), ApplicationService(유스케이스)
- Controller 단위 테스트 용이 — RequestContext mock만 넣으면 됨
- 라우트 등록이 메서드 레퍼런스로 간결해짐

### 관련 파일
- `netty/rest/controller/MemberController.java` — 신규 (Inbound Adapter)
- `netty/rest/config/MemberRouteConfig.java` — DI 조립 + 라우트 등록만 남김

---

## ADR-006: Repository 인터페이스를 domain 패키지로 이동 — 의존성 역전 적용 {#adr-006}
**날짜**: 2026-03-15

### 문제
- Repository **인터페이스(Port)**가 `infrastructure/` 패키지에 위치
- 도메인이 인프라 방향을 바라보는 구조 — 헥사고날의 의존성 방향 원칙에 어긋남
- `InMemoryMemberRepository` 구현체가 `netty/repository/`에 있어서 도메인과 무관한 위치에 산재

### 결정
- Repository **인터페이스**를 `domain/` 패키지로 이동 — 도메인이 자신이 필요한 계약을 직접 정의
- Repository **구현체**를 각 도메인의 `infrastructure/` 패키지로 이동

### Before
```
domains/member/
├── domain/              ← AR, VO
├── application/         ← ApplicationService
└── infrastructure/
    └── MemberRepository.java    ← interface가 여기에

netty/repository/
    └── InMemoryMemberRepository.java   ← 구현체가 엉뚱한 곳에
```

### After
```
domains/member/
├── domain/
│   ├── Members.java              ← AR
│   └── MemberRepository.java    ← interface (Port) — 도메인이 계약 정의
├── application/
│   └── MemberApplicationService.java
└── infrastructure/
    └── InMemoryMemberRepository.java   ← 구현체 (Adapter) — 인프라가 계약 구현
```

### 적용 범위
| 도메인 | 인터페이스 이동 | 구현체 이동 |
|--------|----------------|------------|
| member | `infrastructure/` → `domain/` | `netty/repository/` → `infrastructure/` |
| coupon | `infrastructure/` → `domain/` | (구현체 아직 없음) |
| fare | `infrastructure/` → `domain/` (FareRepository, FarePolicyRepository) | (구현체 아직 없음) |

### 효과
- 의존성 방향이 **인프라 → 도메인** 한 방향으로 통일
- 도메인 패키지만 보면 어떤 계약이 필요한지 한눈에 파악 가능
- coupon, fare에 구현체 추가 시 각 도메인의 `infrastructure/`에 넣으면 됨

---

## ADR-007: 리플렉션 없이 명시적 타입 변환 유지 {#adr-007}
**날짜**: 2026-03-15

### 논의 배경
- Controller에서 `ctx.readBody(MembersCreate.class)` 호출이 반복되고, 반환 타입이 `Object`인 것이 아쉬움
- Spring처럼 `@RequestBody` 어노테이션으로 자동 변환할 수 있지 않을까?

### 검토한 대안
| 방법 | 장점 | 단점 |
|------|------|------|
| 현재 방식 (`ctx.readBody()`) | 단순, 명시적, 리플렉션 없음 | Controller에서 한 줄 반복 |
| RouteEntry에 타입 지정 | 파싱 자동화 가능 | 핸들러 시그니처 복잡해짐 |
| 어노테이션 + 리플렉션 | Spring처럼 깔끔 | Spring 재발명, 프로젝트 목적에 어긋남 |

### 결정
- **리플렉션 없이 현재 방식 유지**
- `ctx.readBody(Class)` 한 줄이 리플렉션 없는 최선의 형태
- 반환 타입 `Object`도 현재 수준에서는 허용

### 판단 근거
- 프로젝트 목적이 "Spring 없이 순수 Netty + DDD" 검증
- 리플렉션 도입 시 어노테이션 → 스캐너 → 파라미터 리졸버 → 타입 변환기로 이어져 Spring MVC를 재발명하게 됨
- 학습 프로젝트에서 중요한 건 구조의 본질을 이해하는 것이지, 편의 기능을 만드는 것이 아님

---

## ADR-008: 인증은 파이프라인 ChannelHandler, 인가는 Controller — AUTH_KEY 단일 전달 {#adr-008}
**날짜**: 2026-03-15

### 문제
- 인증(Authentication)과 인가(Authorization)의 책임이 분리되어 있지 않았음
- ADR-003에서 자체 필터 체인을 제거하고 Netty 파이프라인을 쓰기로 했으므로, 인증도 ChannelHandler로 처리해야 함
- 초기 구현에서 `userId`, `role`을 각각 별도 `AttributeKey`로 전달 → 실제 토큰 기반 인증과 거리가 멀고, 필드가 늘어날 때마다 attr이 증가하는 구조

### 결정
- **인증**: `AuthChannelInboundHandler`에서 처리 — Authorization 헤더의 토큰을 디코딩하여 `AuthInfo` VO를 생성, `Channel.attr(AUTH_KEY)` **단일 키**로 전달
- **인가**: `Controller`에서 처리 — `ctx.getAuthInfo().getRole()` 등으로 접근 제어
- **ApplicationService**: 인증/인가와 무관하게 순수 비즈니스 로직만 담당

### 핵심: AUTH_KEY 단일 전달
```java
// AuthChannelInboundHandler
public static final AttributeKey<AuthInfo> AUTH_KEY = AttributeKey.valueOf("AUTH_KEY");

AuthInfo authInfo = decodeToken(token);  // 토큰 디코딩 → AuthInfo VO
ctx.channel().attr(AUTH_KEY).set(authInfo);
ctx.fireChannelRead(request.retain());

// HttpRoutingHandler — 꺼내서 RequestContext에 세팅만
.authInfo(ctx.channel().attr(AuthChannelInboundHandler.AUTH_KEY).get())

// MemberController — 인가 체크
if (!"ADMIN".equals(ctx.getAuthInfo().getRole())) { ... }
```

### 파이프라인 흐름
```
HttpServerCodec → Aggregator → AuthChannelInboundHandler → HttpRoutingHandler → Controller
                                      │                          │                  │
                                  토큰 디코딩              AUTH_KEY에서           인가 체크
                                  AuthInfo 생성            AuthInfo 꺼내서        (role 기반)
                                  AUTH_KEY에 저장          RequestContext에 세팅
```

### 판단 근거
- **단일 책임**: AuthHandler는 인증만, Controller는 인가만, ApplicationService는 비즈니스만
- **AUTH_KEY 단일 전달**: 인증 결과가 아무리 복잡해져도(claims, permissions 등) AuthInfo VO만 확장하면 됨, attr 키는 하나
- **Netty 파이프라인 활용**: ADR-003의 결정과 일관 — cross-cutting concern은 ChannelHandler로 처리
- **HttpRoutingHandler는 전달만**: 인증 로직을 모름, attr에서 꺼내서 RequestContext에 넣을 뿐

### 관련 파일
- `netty/channel/AuthInfo.java` — 인증 결과 VO (신규)
- `netty/channel/AuthChannelInboundHandler.java` — 인증 ChannelHandler (신규)
- `netty/rest/route/RequestContext.java` — `authInfo` 필드 추가
- `netty/rest/route/HttpRoutingHandler.java` — AUTH_KEY에서 꺼내 RequestContext에 세팅
- `netty/rest/controller/MemberController.java` — 인가 체크 TODO 예시
