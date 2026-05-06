# Todo — 2026-05-05

## Netty 서버 부하 테스트 + 병목 개선 계획

### 목표
차량 수를 10 → 100 → 1000대로 스케일업하면서 Netty 서버의 병목점을 찾고, 개선하여 성능을 높인다.
"이 병목을 이렇게 고쳤더니 TPS가 X → Y로 올랐다"를 증명하는 것이 최종 산출물.

---

## 현재 파이프라인 (측정 대상)

```
[REST 요청]
  Client → HttpServerCodec → HttpObjectAggregator → HttpRoutingHandler
             → Virtual Thread에서 도메인 로직 실행
             → eventLoop.execute()로 응답 전송

[WebSocket broadcast]
  MQTT Subscriber callback → ChannelGroup.writeAndFlush(TextWebSocketFrame)
             → 연결된 모든 브라우저에 동시 전송

[서버 설정]
  Boss: 1 thread / Worker: 4 threads (NIO EventLoop)
  Virtual Thread: newVirtualThreadPerTaskExecutor()
  SO_BACKLOG: 128 / TCP_NODELAY: true / SO_KEEPALIVE: true
```

---

## 0. 사전 준비

### JMeter 설치 및 플러그인

1. Apache JMeter 다운로드: https://jmeter.apache.org/download_jmeter.cgi
2. Plugin Manager 설치 후 추가 플러그인:
   - **WebSocket Samplers by Peter Doornbosch** — WebSocket 부하용
   - **Custom Thread Groups** — Stepping Thread Group (점진적 부하 증가)
   - **3 Basic Graphs** — Response Times Over Time, TPS 그래프
3. JMeter 실행: `bin/jmeter.bat` (GUI로 시나리오 설계 → CLI로 실제 부하 실행)

### 서버 환경 확인

- 서버 시작: `./gradlew run` 또는 CarTrackingApplication 직접 실행
- 서버 포트: `localhost:8081`
- HiveMQ CE: `docker compose up -d` (MQTT 브로커)
- VisualVM 연결: 서버 JVM에 attach하여 힙/스레드 실시간 모니터링

### 부하 테스트 전 체크리스트

- [ ] 서버 정상 동작 확인: `curl http://localhost:8081/api/cartracking/vehicles`
- [ ] HiveMQ CE 정상 동작 확인: `docker ps`
- [ ] VisualVM에서 서버 JVM 연결 확인
- [ ] JMeter에서 간단한 1회 요청 성공 확인

---

## Phase 1: REST API 부하 테스트

### 테스트 시나리오

JMeter로 Netty REST 엔드포인트에 동시 요청을 쏜다.

#### JMeter Test Plan 구성

```
Test Plan
├── Thread Group (동시 사용자 수 조절)
│   ├── HTTP Request: GET /api/cartracking/vehicles
│   ├── HTTP Request: GET /api/cartracking/vehicles/1
│   └── HTTP Request: POST /api/cartracking/vehicles (body: JSON)
├── Listener: Summary Report
├── Listener: Response Times Over Time
├── Listener: Transactions Per Second
└── Listener: View Results Tree (디버깅용, 부하 시 끄기)
```

#### JMeter HTTP Request 설정

```
Server Name: localhost
Port: 8081
Protocol: http
Path: /api/cartracking/vehicles
Method: GET
```

#### 단계별 실행

| 단계 | Thread Group 설정 | 요청 | 뭘 보는가 |
|------|------------------|------|----------|
| R1 | 10 threads, Ramp-up 5초, Loop 100 | GET /vehicles | baseline: "정상 상태에서 TPS 몇이야?" |
| R2 | 100 threads, Ramp-up 10초, Loop 100 | GET /vehicles | "100명 동시에 오면 응답시간 얼마나 느려져?" |
| R3 | 500 threads, Ramp-up 20초, Loop 50 | GET /vehicles | "에러 나기 시작하는 지점이 어디야?" |
| R4 | 1000 threads, Ramp-up 30초, Loop 30 | GET /vehicles | "한계점: connection refused? timeout?" |
| R5 | 100 threads, Ramp-up 10초, Loop 100 | POST /vehicles | "쓰기 요청도 같은 성능이야?" |
| R6 | 500 threads (GET 70% + POST 30%) | 혼합 | "읽기/쓰기 섞이면?" |

#### 결과 해석 기준

| 지표 | 좋음 | 보통 | 나쁨 (병목) |
|------|------|------|------------|
| Avg Response Time | < 10ms | 10~100ms | > 100ms |
| TPS (Transactions/sec) | > 5000 | 1000~5000 | < 1000 |
| Error % | 0% | < 1% | > 1% |
| p99 Response Time | < 50ms | 50~500ms | > 500ms |

> 참고: InMemory라 DB I/O가 없으므로 Netty 순수 성능에 가깝다.
> 여기서 느리면 Netty 설정/코드 문제임이 확실.

#### JMeter CLI 실행 (실제 부하 시 GUI 끄고 CLI로 돌린다)

```bash
jmeter -n -t rest-loadtest.jmx -l result.jtl -e -o report/
```
- `-n`: non-GUI 모드
- `-t`: 테스트 계획 파일
- `-l`: 결과 로그
- `-e -o report/`: HTML 리포트 자동 생성

---

### 예상 병목 지점 (REST)

| # | 위치 | 현재 코드 | 왜 병목인가 | 어떻게 확인하나 |
|---|------|----------|------------|---------------|
| 1 | **HTTP Keep-Alive 미사용** | `ChannelFutureListener.CLOSE` (매 응답 후 연결 종료) | 매 요청마다 TCP 3-way handshake 반복 → 동시 요청 시 연결 생성/종료 오버헤드 폭증 | JMeter에서 Keep-Alive 켜고 vs 끄고 TPS 비교 |
| 2 | **SO_BACKLOG 128** | `ChannelOption.SO_BACKLOG, 128` | 동시 접속이 128 초과 시 OS TCP 큐에서 drop → connection refused | R3/R4에서 Error% 확인 |
| 3 | **Worker 4 threads** | `MultiThreadIoEventLoopGroup(4)` | CPU 코어가 8개인데 Worker 4개면 절반만 활용 | worker 수 변경 후 TPS 비교 |
| 4 | **Unpooled ByteBuf** | `Unpooled.copiedBuffer(json, UTF_8)` | 매 응답마다 힙에 byte[] 할당 → GC 빈번 | VisualVM에서 GC 횟수/시간 관찰 |
| 5 | **매 요청 로깅** | `logger.info("→ {} {} {}")` | 1000 req/s면 1000줄/s 로그 I/O | 로그 끄고 TPS 비교 |
| 6 | **ObjectMapper 직렬화** | 매 응답마다 `writeValueAsString` | CPU 시간 소모 | 프로파일러에서 hot method 확인 |

---

## Phase 2: WebSocket broadcast 부하 테스트

### 테스트 시나리오

N개 WebSocket 클라이언트를 연결한 상태에서 시뮬레이터를 돌려 broadcast 성능을 측정한다.

#### JMeter WebSocket Test Plan 구성

```
Test Plan
├── Thread Group (WebSocket 연결 수)
│   ├── WebSocket Open Connection
│   │     URL: ws://localhost:8081/ws/vehicles
│   ├── WebSocket Read (서버에서 오는 메시지 수신 대기)
│   └── WebSocket Close
├── Listener: Summary Report
└── Listener: Response Times Over Time
```

#### 동시에 다른 터미널에서 시뮬레이터 시작

```bash
curl -X POST http://localhost:8081/api/cartracking/simulator/start
```

#### 단계별 실행

| 단계 | WS 연결 수 | 차량 수 (msg/s) | 뭘 보는가 |
|------|-----------|----------------|----------|
| W1 | 10 | 10대 (2 msg/s) | baseline: "broadcast 잘 되나?" |
| W2 | 100 | 10대 (2 msg/s) | "연결만 많아도 느려지나?" |
| W3 | 100 | 100대 (20 msg/s) | "메시지 빈도 올리면?" |
| W4 | 500 | 100대 (20 msg/s) | "500명에게 20msg/s broadcast 가능?" |
| W5 | 1000 | 1000대 (200 msg/s) | 극한: 초당 20만 frame write |

#### 결과 해석 기준

| 지표 | 좋음 | 보통 | 나쁨 (병목) |
|------|------|------|------------|
| 메시지 수신 지연 | < 50ms | 50~200ms | > 200ms |
| 메시지 유실률 | 0% | < 1% | > 1% |
| 서버 메모리 증가 | 안정적 | 완만 증가 | 급격 증가 (메모리 릭) |

#### 측정 방법

JMeter WebSocket Sampler가 수신한 메시지의 timestamp와 payload 내 timestamp를 비교:
```
broadcast 지연 = JMeter 수신 시각 - payload.timestamp
```

---

### 예상 병목 지점 (WebSocket)

| # | 위치 | 현재 코드 | 왜 병목인가 | 어떻게 확인하나 |
|---|------|----------|------------|---------------|
| 1 | **ChannelGroup 순차 write** | `websocketClients.writeAndFlush(new TextWebSocketFrame(json))` | 1000개 채널에 개별 write+flush — EventLoop에서 순차 처리 | W4/W5에서 수신 지연 급증 확인 |
| 2 | **매번 새 Frame 객체** | `new TextWebSocketFrame(json)` | 같은 메시지를 N번 복사하여 N개 Frame 생성 → GC pressure | VisualVM에서 TextWebSocketFrame 객체 생성량 |
| 3 | **Subscriber 단일 스레드** | HiveMQ callback 스레드에서 broadcast까지 동기 실행 | broadcast 느리면 → 다음 MQTT 메시지 수신도 밀림 → 전체 파이프라인 정체 | MQTT publish 시각 vs Subscriber 수신 시각 차이 증가 |
| 4 | **이중 JSON 직렬화** | `objectMapper.writeValueAsString(telemetry)` | MQTT에서 받은 payload가 이미 JSON인데 역직렬화→재직렬화 반복 | 프로파일러 hot method |

---

## Phase 3: 병목 개선 → 재측정

### 개선 원칙

```
1회에 1개만 고친다 → 재측정 → Before/After 기록 → 다음 병목으로
```

### 개선 순서 (예상 효과 큰 순)

#### 개선 1: HTTP Keep-Alive 지원
- **현재**: 매 응답 후 `ChannelFutureListener.CLOSE` → TCP 연결 종료
- **변경**: `Connection: keep-alive` 헤더 확인 후 연결 유지, Content-Length 기반으로 응답 구분
- **예상 효과**: TPS 2~5배 향상 (TCP 핸드셰이크 제거)
- **구현 포인트**: `HttpRoutingHandler.sendJson()`에서 CLOSE 대신 연결 유지 + idle timeout 설정

#### 개선 2: SO_BACKLOG 증가
- **현재**: 128
- **변경**: 1024 (또는 OS 기본값에 맞춤)
- **예상 효과**: 동시 접속 폭주 시 connection refused 해소
- **구현 포인트**: `CarTrackingServer`에서 `ChannelOption.SO_BACKLOG, 1024`

#### 개선 3: Worker thread 수 최적화
- **현재**: 4 (하드코딩 in properties)
- **변경**: `Runtime.getRuntime().availableProcessors() * 2` 또는 측정 기반 최적값
- **예상 효과**: CPU 활용도 향상 → TPS 증가
- **구현 포인트**: `netty_config.properties` 또는 동적 감지

#### 개선 4: Unpooled → Pooled ByteBuf
- **현재**: `Unpooled.copiedBuffer(json, UTF_8)` — 매번 힙 할당
- **변경**: `ctx.alloc().buffer()` 사용 (Netty의 PooledByteBufAllocator 활용)
- **예상 효과**: GC 횟수 감소, p99 latency 안정화
- **구현 포인트**: `HttpRoutingHandler.sendJson()` 내부

#### 개선 5: WebSocket broadcast 최적화
- **현재**: 매 메시지마다 `new TextWebSocketFrame(json)` → N개 채널에 전송
- **변경**:
  - ByteBuf를 한 번 만들고 `retain()`으로 공유 → 마지막 채널이 release
  - 또는 `write()`만 모아서 마지막에 한 번 `flush()` (batch flush)
- **예상 효과**: 메모리 1/N 절감, EventLoop write 시간 단축
- **구현 포인트**: `VehicleTelemetrySubscriber`에서 broadcast 로직 변경

#### 개선 6: Subscriber callback 오프로드
- **현재**: HiveMQ Client 내부 스레드에서 도메인 로직 + broadcast 동기 실행
- **변경**: callback에서 Virtual Thread로 오프로드 → MQTT 수신 스레드 즉시 해방
- **예상 효과**: 메시지 빈도 높을 때 수신 지연 해소
- **구현 포인트**: `VehicleTelemetrySubscriber.subscribe()` callback 내부에 `virtualExecutor.submit()`

#### 개선 7: 이중 직렬화 제거
- **현재**: MQTT payload(JSON bytes) → 역직렬화(TelemetryPayload) → 재직렬화(String) → Frame
- **변경**: MQTT payload를 그대로 WebSocket Frame에 전달 (도메인 로직 처리용으로만 역직렬화)
- **예상 효과**: CPU 시간 절약 (ObjectMapper 호출 1회 제거)
- **구현 포인트**: `byte[] raw = publish.getPayloadAsBytes()` → `new TextWebSocketFrame(new String(raw))`

#### 개선 8: 부하 시 로그 제거
- **현재**: 매 요청/응답마다 INFO 로그 2줄
- **변경**: 부하 프로파일에서 개별 로그 OFF, summary만 유지
- **예상 효과**: I/O 경합 제거, 특히 동기 로깅 시 효과 큼
- **구현 포인트**: `log4j2.properties`에 부하 테스트용 프로파일 추가 또는 동적 레벨 변경

---

## Phase 4: 결과 정리

### 산출물

1. **결과 표**: 각 시나리오 × 각 개선의 Before/After

```
예시:
| 시나리오 | 개선 전 TPS | 개선 후 TPS | 개선 전 p99 | 개선 후 p99 | 원인 |
|---------|------------|------------|------------|------------|------|
| R3 (500동시) | 1,200 | 5,800 | 450ms | 35ms | Keep-Alive 적용 |
| W4 (500연결) | 수신지연 800ms | 50ms | - | - | broadcast retain 최적화 |
```

2. **ADR-V027**: "Netty 부하 테스트 결과 및 최적화 결정" 작성
3. **최종 아키텍처 한계**: "현재 단일 서버에서 최대 X 동시연결, Y TPS 처리 가능"

### JMeter HTML 리포트

각 단계 실행 후 자동 생성되는 리포트를 `loadtest-results/` 디렉토리에 보관:
```
loadtest-results/
├── phase1-R1-baseline/
├── phase1-R2-100threads/
├── phase1-R3-500threads/
├── phase2-W1-baseline/
├── phase3-after-keepalive/
├── phase3-after-backlog/
└── ...
```

---

## 실행 순서 요약

1. JMeter 설치 + 플러그인 세팅
2. 서버 + HiveMQ CE 기동, VisualVM 연결
3. **Phase 1** 실행 → REST 병목 발견 → 기록
4. **Phase 2** 실행 → WebSocket 병목 발견 → 기록
5. **Phase 3** 개선 1~8을 순서대로:
   - 1개 고친다
   - 같은 시나리오 재측정
   - Before/After 기록
   - 다음 개선으로
6. **Phase 4** 결과 정리 + ADR 작성

---

## 참고: 현재 아키텍처 제약

- Publisher/Subscriber 모두 **단일 MQTT 커넥션** 사용 (MqttClientFactory)
- Subscriber callback은 HiveMQ Client 내부 스레드에서 실행 (별도 오프로드 없음)
- InMemory 저장소 — GC pressure가 병목이 될 수 있음
- WebSocket broadcast는 Netty ChannelGroup — EventLoop 스레드에서 실행
- HTTP Keep-Alive 미지원 — 매 요청마다 TCP 연결 생성/종료
