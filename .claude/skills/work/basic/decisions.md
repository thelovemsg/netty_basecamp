# Architectural Decision Records

프로젝트의 주요 의사결정 사항을 기록한 문서. Second Brain으로 활용하여 과거 결정의 맥락과 근거를 빠르게 참조할 수 있도록 함.

업데이트 히스토리
- 2026-03-15 생성

---

## 목차
1. [ADR-001: RequestContext 도입 — BiFunction 핸들러 제거](#adr-001)
2. [ADR-002: RouteMatch 도입 — RouteRegistry 매칭 결과 정리](#adr-002)
3. [ADR-003: AuthFilter 최소 구조 도입](#adr-003)
4. [ADR-004: EventLoop 블로킹 대응 ~~(파기 → ADR-009 참조)~~](#adr-004)
5. [ADR-005: Controller 레이어 분리 — Inbound Adapter 도입](#adr-005)
6. [ADR-006: Repository 인터페이스를 domain 패키지로 이동 — 의존성 역전 적용](#adr-006)
7. [ADR-007: 리플렉션 없이 명시적 타입 변환 유지](#adr-007)
8. [ADR-008: 인증은 파이프라인 ChannelHandler, 인가는 Controller — AUTH_KEY 단일 전달](#adr-008)
9. [ADR-009: EventLoop 블로킹 대응 재결정 — Java 21 Virtual Thread 오프로드](#adr-009)
10. [ADR-010: ApplicationService에서 여러 Repository + DomainService를 함께 쓰는 것은 정상](#adr-010)

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
```[decisions.md](decisions.md)

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

## ~~ADR-004: EventLoop 블로킹 대응 — `DefaultEventExecutorGroup`으로 오프로드~~ {#adr-004}
**날짜**: 2026-03-15
**수정**: 2026-03-25 (InMemory 폐기 확정 → 방법 1 확정, 방법 2 기각)
**파기**: 2026-04-13 — Netty 4.2에서 `addLast(EventExecutorGroup, handler)` API 제거로 방법 1 실행 불가 → **ADR-009로 대체**
**상태**: ~~파기~~

### 문제
`HttpRoutingHandler.channelRead0()`에서 도메인 로직이 **Netty Worker EventLoop 스레드에서 직접 실행**된다.

```
Worker EventLoop 스레드 (4개)
  └── channelRead0()
        ├── RequestContext 생성
        ├── Filter 실행
        ├── match.getEntry().handle(ctx)
        │     └── ApplicationService → Repository.save()  ← 블로킹 지점
        └── sendJson()
```

- JDBC 호출이 50~200ms 블로킹 → Worker 스레드 4개가 DB I/O에 점유됨 → 전체 서버 처리량 급감
- 선착순 쿠폰 오픈런 시 비관적 락 대기 → Worker 전부 멈춤 → 서버 무응답
- Netty의 **"EventLoop를 절대 블로킹하지 마라"** 원칙 위반

### 왜 위험한가
| 상황 | Worker 스레드 상태 | 결과 |
|------|-------------------|------|
| JDBC 단건 조회 | ~10ms 블로킹 | 체감 없음 |
| JDBC 트랜잭션 (락 포함) | 50~200ms 블로킹 | Worker 4개 고갈 → 신규 요청 큐잉 |
| 선착순 쿠폰 오픈런 | 비관적 락 대기 | Worker 전부 멈춤 → 서버 무응답 |

### 검토한 방법

**방법 1: `DefaultEventExecutorGroup` 사용 (채택)**
```java
// CustomChannelInitializer
EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(16);
p.addLast(businessGroup, new HttpRoutingHandler(registry));
```
- Netty가 제공하는 가장 간단한 방법
- `HttpRoutingHandler`의 `channelRead0()`가 별도 스레드풀에서 실행됨
- 코드 변경 최소 (1줄 추가)

**방법 2: 핸들러 내부에서 직접 오프로드 (기각)**
```java
// HttpRoutingHandler 내부
private final ExecutorService blockingPool = Executors.newFixedThreadPool(16);

protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
    RequestContext requestContext = buildContext(request);
    blockingPool.submit(() -> {
        Object result = match.getEntry().handle(requestContext);
        ctx.writeAndFlush(buildResponse(result));
    });
}
```
- `HttpRoutingHandler` 안에서 별도 `ExecutorService`를 직접 생성하여, 블로킹 로직만 `submit()`으로 넘기는 방식
- 라우트 단위로 "이 API만 오프로드, 저 API는 EventLoop에서 직접" 같은 세밀한 제어가 가능
- 일부 API만 DB를 타고 나머지는 인메모리일 때 유효한 전략

### 결정: 방법 1 확정
- **InMemory 저장소를 사용하지 않고 모든 Repository가 RDBMS를 사용**하기로 확정
- 모든 요청이 JDBC를 타므로 라우트별 오프로드 제어가 무의미 → 방법 2의 세밀함이 불필요
- 방법 1은 파이프라인 설정 1줄로 `HttpRoutingHandler` 전체를 별도 스레드풀에서 실행 → 가장 단순하고 충분
- `CustomChannelInboundHandler`, `CustomChannelOutboundHandler`는 빈 껍데기이며 파이프라인 위치상 호출도 안 됨 → RDBMS 도입 시점에 역할 재정의 또는 삭제 결정

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
**보강**: 2026-03-22 (성능적·구조적 근거 추가)

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

#### 1. 프로젝트 목적과의 정합성
- 프로젝트 목적이 "Spring 없이 순수 Netty + DDD" 검증
- 리플렉션 도입 시 어노테이션 → 스캐너 → 파라미터 리졸버 → 타입 변환기로 이어져 Spring MVC를 재발명하게 됨
- 학습 프로젝트에서 중요한 건 구조의 본질을 이해하는 것이지, 편의 기능을 만드는 것이 아님

#### 2. 리플렉션의 성능적 비용 — Netty 핫 패스에서 치명적
우리 서버의 핫 패스(Hot Path):
```
EventLoop → channelRead0() → RouteRegistry → Controller → ApplicationService
```
이 경로에서 리플렉션이 유발하는 4가지 성능 손해:

**JIT 인라이닝 실패**: 명시적 메서드 호출(`controller::update`)은 JIT 컴파일러가 호출 대상을 특정할 수 있어 인라이닝(코드 병합) 가능. 리플렉션(`Method.invoke()`)은 호출 대상이 런타임에 결정되는 불투명한 블랙박스 → JIT가 최적화를 포기하여 메가모픽(Megamorphic) 상태로 전락.

**박싱/GC 폭발**: `Method.invoke()`는 모든 인자를 `Object[]` 가변인자로 받음 → `long id` 같은 원시 타입이 `Long`으로 박싱 → 매 요청마다 단기 생명주기 임시 객체 대량 생성 → GC 압박 → Stop-the-World 지연. Netty가 ByteBuf 풀링으로 Zero GC를 추구하는 철학과 정면 충돌.

**보안 검사 반복**: 명시적 호출은 컴파일 타임에 접근 권한 검증 완료. 리플렉션은 `invoke()` 호출 때마다 런타임 보안 검사 수행 → 매 요청마다 불필요한 오버헤드.

**런타임 타입 탐색**: 문자열 기반으로 메서드 테이블을 뒤져 이름·파라미터 타입을 동적 매칭 → 컴파일 타임에 고정 오프셋으로 직접 점프하는 것과 비교 불가.

| 비교 항목 | 명시적 직접 호출 | 리플렉션 호출 |
|-----------|-----------------|--------------|
| 대상 확인 시점 | 컴파일 타임 | 런타임 (문자열 기반) |
| JIT 인라이닝 | 완벽한 코드 병합 | 최적화 실패 |
| 데이터 타입 | 원시 타입 직접 사용 | 박싱/언박싱 강제 |
| GC 부하 | Zero GC | 임시 객체 대량 발생 |
| 보안 검사 | 1회 (컴파일/로드 시) | 매 호출마다 반복 |

#### 3. 어노테이션의 구조적 비용 — DDD 계층 오염
- **제어 흐름 은닉**: 어노테이션으로 라우팅이 엮이면 코드를 위→아래로 읽어도 실행 경로를 추적할 수 없음 (블랙박스화)
- **DDD 레이어 오염**: `@RequestBody`, `@PathVariable` 등이 Controller에 침투하면 도메인 계층이 프레임워크에 종속 → 헥사고날 아키텍처의 의존성 방향 원칙 위반
- **디버깅 고통**: 예외 스택 트레이스가 프록시·CGLIB·AOP 인터셉터로 도배 → 실제 문제 지점 파악 난해
- **테스트 복잡도**: 어노테이션 파싱을 위해 프레임워크 컨테이너 전체 구동 필요 → 단위 테스트 불가

#### 4. Netty ChannelPipeline이 이미 해답
우리 프로젝트는 Netty 파이프라인 자체가 명시적 필터 체인 역할을 함 (ADR-003):
```java
pipeline.addLast(new HttpServerCodec());
pipeline.addLast(new HttpObjectAggregator(65536));
pipeline.addLast(new AuthChannelInboundHandler());
pipeline.addLast(new HttpRoutingHandler(registry));
```
- 핸들러 순서가 코드에 그대로 드러남 → 자체 문서화(Self-documenting)
- `ctx.fireChannelRead()`로 다음 핸들러 호출 → 리플렉션 없는 순수 메서드 체인 → JIT 인라이닝 최적화 가능
- `@Sharable` 같은 Netty의 어노테이션도 자동 라우팅용이 아닌 스레드 안전성 보증 표식일 뿐

#### 5. Hot Path vs Cold Path 관점
- **Cold Path** (서버 부팅 시 1회): `AppConfig` → `RouteConfig` → `RouteRegistry` 등록. 이 구간에서 리플렉션을 쓴다 해도 성능 영향 미미
- **Hot Path** (매 요청마다): `EventLoop → Auth → Routing → Controller → Service`. 이 구간에서 리플렉션은 절대 불가
- Spring WebFlux도 핫 패스 코어는 Netty 파이프라인이 처리하고, 어노테이션 라우팅은 그 위의 추상화 계층에서 성능을 '세금'으로 지불하는 구조
- 우리 프로젝트는 그 세금조차 걷어내고 Netty 본연의 성능을 체감하는 것이 목적

### 참고 자료
- [Netty 극한 성능과 명시적 제어 철학 분석](https://docs.google.com/document/d/174oaq8qL1BQhYwVk-gKNpUmJR9IX67wKTOifBXwjNQc) — 리플렉션 비용, JIT 인라이닝, Hot/Cold Path 상세 분석

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

---

## ADR-009: EventLoop 블로킹 대응 재결정 — Java 21 Virtual Thread 오프로드 {#adr-009}
**날짜**: 2026-04-13
**상태**: 확정 + 구현 완료 (2026-04-13)
**배경**: ADR-004 파기 (Netty 4.2에서 `addLast(EventExecutorGroup, handler)` API 제거)

### 문제

ADR-004에서 채택한 방법 1(`DefaultEventExecutorGroup`)이 **Netty 4.2에서 동작하지 않는다**.

Netty 4.2는 `ChannelPipeline.addLast(EventExecutorGroup, ChannelHandler)` 오버로드를 제거했다.
즉, 파이프라인 설정 한 줄로 핸들러 전체를 별도 스레드풀에 넘기는 방식이 더 이상 불가능하다.

결과적으로 현재 `HttpRoutingHandler.channelRead0()`는 **Netty Worker EventLoop 스레드에서 직접 실행**되고 있으며, JDBC 도입 시 블로킹 문제가 그대로 남는다.

```
Worker EventLoop 스레드 (4개)
  └── channelRead0()
        └── match.getEntry().handle(requestContext)
              └── ApplicationService → Repository (JDBC)  ← 여기서 블로킹
```

| 상황 | Worker 스레드 상태 | 결과 |
|------|-------------------|------|
| JDBC 단건 조회 | ~10ms 블로킹 | 체감 없음 |
| JDBC 트랜잭션 (락 포함) | 50~200ms 블로킹 | Worker 4개 고갈 → 신규 요청 큐잉 |
| 선착순 쿠폰 오픈런 | 비관적 락 대기 | Worker 전부 멈춤 → 서버 무응답 |

### 검토한 대안

**방법 A: `newFixedThreadPool`로 직접 오프로드**
```java
private static final ExecutorService BLOCKING_POOL =
    Executors.newFixedThreadPool(16);
```
- 스레드 수를 예측해서 고정해야 함 — 과소하면 큐 쌓임, 과다하면 컨텍스트 스위칭 낭비
- OS 스레드 1개 = blocking I/O 1개 대응 → 고트래픽 시 스레드 수가 병목

**방법 B: Java 21 Virtual Thread (채택)**
```java
private static final ExecutorService VIRTUAL_EXECUTOR =
    Executors.newVirtualThreadPerTaskExecutor();
```
- JVM이 virtual thread를 JDBC 블로킹 시점에 OS 스레드에서 `unmount` → carrier thread(OS 스레드)가 즉시 해방
- OS 스레드는 4~8개로 고정, virtual thread는 수천 개가 동시에 블로킹 대기 가능
- 스레드 풀 크기 튜닝 불필요 — JVM이 자동 관리
- Java 21 정식 기능 (Project Loom), 이 프로젝트 스택과 완전 부합

### 결정: 방법 B (Virtual Thread) 확정

`HttpRoutingHandler.channelRead0()` 안에서 `RequestContext` 빌드까지는 **EventLoop 스레드에서 처리**하고, 블로킹 가능성이 있는 `handle()` 이후만 **Virtual Thread로 오프로드**한다.

```java
public class HttpRoutingHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = extractPath(request.uri());
        String method = request.method().name();

        RouteMatch match = registry.find(method, path);
        if (match == null) {
            sendJson(ctx, NOT_FOUND, Map.of("error", "Not Found"));
            return;
        }

        // ★ ByteBuf 복사는 EventLoop 스레드에서 — channelRead0 리턴 후 release()되므로
        RequestContext requestContext = RequestContext.builder()
            .method(method)
            .path(path)
            .pathVariables(match.getPathVariables())
            .queryParams(extractQueryParams(request.uri()))
            .headers(extractHeaders(request.headers()))
            .body(request.content().toString(CharsetUtil.UTF_8))
            .authInfo(ctx.channel().attr(AuthChannelInboundHandler.AUTH_KEY).get())
            .build();

        // ★ 블로킹 가능한 도메인 로직만 virtual thread로 오프로드
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
}
```

### ByteBuf 복사를 EventLoop에서 해야 하는 이유

`SimpleChannelInboundHandler`는 `channelRead0()` 리턴 직후 `request`의 `ByteBuf`를 자동으로 `release()`한다.
`VIRTUAL_EXECUTOR.submit()` 이후 EventLoop 스레드는 즉시 리턴하므로, virtual thread 안에서 `request.content()`를 읽으면 이미 해제된 메모리를 참조하게 된다.

따라서 `RequestContext` 빌드(ByteBuf → String 복사 포함)는 반드시 EventLoop 스레드에서 완료해야 한다.

### Virtual Thread가 적합한 이유 — JDBC I/O 특성

JDBC는 네트워크 소켓 I/O 위에서 동작한다. Java 21 Virtual Thread는 소켓 I/O 블로킹 시점에 JVM이 carrier thread를 자동 해방하도록 설계되어 있다 (Continuation 기반). 즉 "블로킹처럼 코드를 쓰되, 실제로는 비동기처럼 동작"한다.

| 비교 항목 | `newFixedThreadPool(N)` | Virtual Thread |
|-----------|------------------------|----------------|
| OS 스레드 수 | N개 고정 | 소수의 carrier thread |
| 동시 JDBC 블로킹 | N개까지만 | 사실상 무제한 |
| 풀 크기 튜닝 | 필요 | 불필요 |
| 오픈런 락 대기 | N초과 시 큐잉 | carrier thread 자동 해방 |

### 관련 파일
- `netty/rest/route/HttpRoutingHandler.java` — Virtual Thread 오프로드 적용 지점

---

## ADR-010: ApplicationService에서 여러 Repository + DomainService를 함께 쓰는 것은 정상 {#adr-010}
**날짜**: 2026-04-13

### 고민 배경
`CouponIssueApplicationService`를 보면 `FareRepository`, `FarePolicyRepository`, `CouponRepository` 세 개의 Repository와
`FareCalculationDomainService`, `CouponIssueDomainService` 두 개의 DomainService가 함께 주입되어 있다.
"Repository랑 Service가 한 클래스에 섞여도 되는가?" 혼란이 생겼다.

또한 Coupon 도메인인데 Fare 관련 의존이 섞인 이유도 불명확했다.

### 왜 Coupon 발급에 Fare가 필요한가?

비즈니스 요구사항: **쿠폰 발급 시점에 "실제 적용 금액"이 얼마인지 계산해서 IssuedCoupon에 기록해야 한다.**

```
Fare (기본 요금)
  + List<FarePolicy> (할인/할증 정책들: 주말 할증, 장기 할인, 고정 할인...)
  ↓ FareCalculationPipeline (정책을 우선순위 순으로 순차 적용)
  = Money finalPrice
  ↓
IssuedCoupon (발급 이력에 finalPrice 박힘)
```

Coupon 발급은 "요금 계산 결과를 받아서 쿠폰에 적용"하는 유스케이스이므로,
**Coupon 도메인이 Fare 도메인의 결과를 소비하는 것**은 자연스러운 의존이다.

### ApplicationService에 Repository + DomainService가 섞여도 되는가?

**된다. 오히려 ApplicationService가 해야 할 일이 그것이다.**

| 레이어 | 알아야 하는 것 | 몰라야 하는 것 |
|--------|---------------|--------------|
| `ApplicationService` | 어디서 꺼내고(Repository), 무엇을 시키고(DomainService), 어디에 저장할지 | 도메인 내부 불변식 |
| `DomainService` | 넘겨받은 도메인 객체 간의 도메인 규칙 | Repository, 인프라 |
| `Repository` | 저장/조회 | 비즈니스 로직 |

`CouponIssueDomainService`와 `FareCalculationDomainService`를 보면 둘 다 **Repository를 전혀 받지 않는다**.
이미 꺼내진 도메인 객체(`Coupon`, `Fare`, `List<FarePolicy>`)만 받아서 도메인 규칙만 처리한다.

ApplicationService가 "누가 무엇을 어떤 순서로" 를 조율하는 자리이기 때문에
Repository 의존이 없으면 오히려 이상한 것이다.

### 실제 흐름 정리

```
CouponIssueApplicationService.issueCoupon(fareId, couponId, memberId)
│
├── [인프라 접촉] Repository 세 개로 데이터 조회
│       fareRepository.findById(fareId)           → Fare
│       farePolicyRepository.findByFareId(fareId) → List<FarePolicy>
│       couponRepository.findById(couponId)        → Coupon
│
├── [도메인 위임] FareCalculationDomainService
│       .calculateFinalPrice(fare, policies)
│       └── FareCalculationPipeline: 정책 순차 적용
│       → Money finalPrice
│
└── [도메인 위임] CouponIssueDomainService
        .issueToMember(coupon, memberId, finalPrice)
        ├── coupon.reserve() — 재고 감소 (AR 내부에서 불변식 보호)
        ├── numberGenerator.generate() — 발급번호 생성
        └── IssuedCoupon.issue() — 발급 이력 엔티티 조립
```

### 판단 기준 — DomainService에 Repository가 있으면 냄새

- `DomainService`가 Repository를 직접 주입받으면 → **ApplicationService의 역할을 침범한 것**
- `ApplicationService`가 Repository를 주입받으면 → **정상 (오케스트레이션 책임)**

### 관련 파일
- `domains/coupon/application/CouponIssueApplicationService.java` — 오케스트레이션 진입점
- `domains/coupon/domain/service/CouponIssueDomainService.java` — Repository 없음, 도메인 규칙만
- `domains/fare/domain/service/FareCalculationDomainService.java` — Repository 없음, 계산 규칙만
- `domains/fare/domain/calculation/FareCalculationPipeline.java` — 정책 파이프라인
