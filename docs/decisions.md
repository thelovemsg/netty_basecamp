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

## ADR-003: AuthFilter 최소 구조 도입 {#adr-003}
**날짜**: 2026-03-15

### 문제
- 나중에 Authorization header 검증 같은 보안 처리가 필요해질 것
- 하지만 학습 프로젝트이므로 Spring Security 같은 거창한 구조는 과하다

### 결정
- `RouteFilter` 인터페이스 (FunctionalInterface) + `AuthFilter` 구현체 + `UnauthorizedException`
- `HttpRoutingHandler`에서 `List<RouteFilter>`를 for문으로 순회 (3줄)
- 현재는 필터 없이(`List.of()`) 동작 → 기존 동작에 영향 없음
- Spring Security의 FilterChain, SecurityContext, FilterOrder 같은 구조는 **의도적으로 배제**

### 판단 근거
- 학습 프로젝트에서 중요한 건 "이런 구조가 가능하구나"를 확인하는 것
- 확장 가능성만 열어두되, 실제로 필터를 여러 개 쌓을 일이 오면 그때 고민해도 늦지 않음

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
