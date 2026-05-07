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

- [ ] 서버 정상 동작 확인: `curl http://localhost:8081/api/cartracking/vehicles` (1000대 응답 확인)
- [ ] HiveMQ CE 정상 동작 확인: `docker ps`
- [ ] VisualVM에서 서버 JVM 연결 확인
- [ ] JMeter에서 간단한 1회 요청 성공 확인
- [ ] 시뮬레이터 count 파라미터 동작 확인: `curl -X POST 'http://localhost:8081/api/cartracking/simulator/start?count=10'`

---

## Phase 1: 차량 수 스케일업 통합 부하 테스트 (핵심)

### 목표
차량 수(= MQTT 메시지 빈도)를 10 → 100 → 500 → 1000대로 늘리면서,
**시뮬레이터가 GPS를 쏘는 중에** REST 조회 + WebSocket broadcast가 동시에 동작하는
실제 운영 시나리오의 병목을 찾는다.

### 코드 변경 사항 (ADR-V025, ADR-V027)
- 서버 시작 시 차량 1000대 자동 seed 등록 (`SEED_VEHICLE_COUNT = 1000`, 번호판 `TEST-0001` ~ `TEST-1000`)
- `POST /api/cartracking/simulator/start?count=N` — 시뮬레이터가 앞에서 N대만 시뮬레이션

### 테스트 시나리오

시뮬레이터가 N대 차량의 GPS를 5초마다 MQTT로 전송하는 중에,
JMeter로 REST 조회 부하 + WebSocket 수신 측정을 **동시에** 수행한다.

#### 단계별 실행

| 단계 | 차량 수 | MQTT msg/s | JMeter REST | JMeter WS | 뭘 보는가 |
|------|--------|-----------|-------------|-----------|----------|
| S1 | 10대 | 2/s | 10 threads, GET /vehicles, Loop 100 | 5 WS 연결 | baseline: 모든 지표 정상인가? |
| S2 | 50대 | 10/s | 30 threads, GET /vehicles, Loop 50 | 10 WS 연결 | 50대로 늘려도 괜찮은가? |
| S3 | 100대 | 20/s | 50 threads, GET /vehicles, Loop 50 | 20 WS 연결 | broadcast 지연이 시작되는가? |
| S4 | 500대 | 100/s | 50 threads, GET /vehicles, Loop 30 | 50 WS 연결 | 어디서 병목이 터지나? |
| S5 | 1000대 | 200/s | 50 threads, GET /vehicles, Loop 20 | 100 WS 연결 | 극한: 서버가 버티는가? |

> **핵심 변수는 차량 수(= 메시지 빈도)**이다.
> REST 동시 사용자 수는 고정 또는 소폭 증가만 시킨다.

#### 각 단계 실행 절차

```
1. 서버 시작 (1000대 seed 자동 등록)

2. 시뮬레이터 시작 (N대)
   curl -X POST 'http://localhost:8081/api/cartracking/simulator/start?count=10'

3. 30초 대기 (시뮬레이터가 GPS를 쏘기 시작하고 안정화)

4. JMeter 실행 (시뮬레이터가 돌아가는 중에)
   - REST Thread Group: GET /api/cartracking/vehicles
   - WebSocket Thread Group: ws://localhost:8081/ws/vehicles 연결 + 수신 대기
   jmeter -n -t scaleup-loadtest.jmx -l loadtest-results/S1.jtl -e -o loadtest-results/S1/

5. JMeter 종료 후 결과 기록
   - REST: Avg, p99, TPS, Error%
   - WebSocket: 수신 메시지 수, 수신 지연(payload timestamp vs 수신 시각)
   - VisualVM: 힙 사용량, GC 횟수, 스레드 수

6. 시뮬레이터 중지
   curl -X POST http://localhost:8081/api/cartracking/simulator/stop

7. 서버 재시작 (InMemory 초기화) → 다음 단계로
   - S1 → S2: count=10 → count=50
```

#### JMeter Test Plan 구성

```
Test Plan: "차량 스케일업 통합 부하 테스트"
├── HTTP Request Defaults          ← localhost, 8081, http
├── HTTP Header Manager            ← Content-Type: application/json
│
├── [Thread Group] REST-GET-vehicles       ← 시뮬레이터 도는 중에 REST 부하
│   ├── Number of Threads: 10~50 (단계별 조절)
│   ├── Ramp-Up Period: 5
│   ├── Loop Count: 100~20 (단계별 조절)
│   └── HTTP Request: GET /api/cartracking/vehicles
│
├── [Thread Group] WS-broadcast-수신       ← 시뮬레이터 도는 중에 WebSocket 수신 측정
│   ├── Number of Threads: 5~100 (단계별 조절)
│   ├── WebSocket Open: ws://localhost:8081/ws/vehicles
│   ├── WebSocket Read (Duration: 60초간 수신)
│   └── WebSocket Close
│
├── [Listener] Summary Report
├── [Listener] Response Times Over Time
└── [Listener] Transactions Per Second
```

#### 결과 기록 템플릿

```
| 단계 | 차량 수 | REST Avg | REST p99 | REST TPS | REST Error% | WS 수신 지연 | WS 유실률 | 힙 사용 | GC 횟수 |
|------|--------|---------|---------|---------|------------|------------|---------|--------|--------|
| S1   | 10     |         |         |         |            |            |         |        |        |
| S2   | 50     |         |         |         |            |            |         |        |        |
| S3   | 100    |         |         |         |            |            |         |        |        |
| S4   | 500    |         |         |         |            |            |         |        |        |
| S5   | 1000   |         |         |         |            |            |         |        |        |
```

---

## Phase 1-A: REST 순수 부하 테스트 (보조)

### 목표
시뮬레이터 없이 REST 엔드포인트만의 순수 처리 성능을 측정한다.
Phase 1 결과와 비교하면 "MQTT broadcast가 REST 성능에 얼마나 영향을 주는가"를 알 수 있다.

### 테스트 시나리오

JMeter로 Netty REST 엔드포인트에 동시 요청을 쏜다. (시뮬레이터 OFF 상태)

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

공통 설정 (HTTP Request Defaults에 등록하면 편리):
```
Server Name: localhost
Port: 8081
Protocol: http
Content-Type: application/json (Header Manager에 추가)
```

##### Request 1: GET /api/cartracking/vehicles (전체 차량 조회)
```
Method: GET
Path: /api/cartracking/vehicles
Body: 없음
예상 응답: 200 OK, JSON 배열
```

##### Request 2: GET /api/cartracking/vehicles/${vehicleId} (단일 차량 조회)
```
Method: GET
Path: /api/cartracking/vehicles/${vehicleId}
Body: 없음
예상 응답: 200 OK, JSON 객체 (id, plateNumber, type, status, location)
설정: User Defined Variables에 vehicleId=1 또는 CSV Data Set으로 ID 목록 주입
```

##### Request 3: POST /api/cartracking/vehicles (차량 등록)
```
Method: POST
Path: /api/cartracking/vehicles
Body (raw JSON):
{
  "plateNumber": "TEST${__threadNum}${__counter(,)}",
  "type": "SEDAN",
  "homeLat": 37.4979,
  "homeLng": 127.0276
}
예상 응답: 200 OK, 생성된 차량 JSON (id, plateNumber, type, status, location)
참고: plateNumber 중복 방지를 위해 JMeter 함수로 고유값 생성
      type 유효값: SEDAN, SUV, VAN
      homeLat: -90 ~ 90 / homeLng: -180 ~ 180
```

##### Request 4: GET /api/cartracking/vehicles/${vehicleId}/journeys (여정 조회)
```
Method: GET
Path: /api/cartracking/vehicles/${vehicleId}/journeys
Body: 없음
예상 응답: 200 OK, Journey JSON 배열 (id, origin, destination, status, startedAt, arrivedAt)
설정: 시뮬레이터로 여정 데이터를 미리 생성해둔 후 테스트
```

##### Request 5: GET /api/cartracking/journeys/${journeyId}/route (여정 경로/GPS 스냅샷)
```
Method: GET
Path: /api/cartracking/journeys/${journeyId}/route
Body: 없음
예상 응답: 200 OK, LocationSnapshot JSON 배열 (id, journeyId, location, capturedAt)
설정: 시뮬레이터 실행 후 생성된 journeyId 사용
```

#### Listener 설정

##### 1. Summary Report (필수 — 핵심 지표 한눈에)
```
추가 위치: Test Plan 또는 Thread Group 하위
설정:
  - "Save Table Data" 체크 → CSV로 결과 내보내기
확인 항목: Average, p90, p99, Min, Max, Error%, Throughput(TPS)
```

##### 2. Response Times Over Time (플러그인 필요)
```
추가 위치: Thread Group 하위
설정:
  - Granularity: 1000ms (1초 단위 집계)
  - 그래프에서 시간 흐름에 따른 응답시간 변화 확인
확인 항목: 부하 증가 시 응답시간이 선형으로 느는지, 급격히 치솟는 지점(변곡점)이 어디인지
```

##### 3. Transactions Per Second (플러그인 필요)
```
추가 위치: Thread Group 하위
설정:
  - Granularity: 1000ms
확인 항목: TPS가 Thread 수에 비례해 오르는지, 포화(saturation) 지점이 어디인지
```

##### 4. View Results Tree (디버깅 전용)
```
추가 위치: Thread Group 하위
설정:
  - 부하 테스트 시 반드시 비활성화 (메모리 폭증 위험)
  - 시나리오 검증용으로만 사용 (1~2회 수동 실행 시)
확인 항목: 요청/응답 원문, HTTP 상태코드, JSON 파싱 에러 여부
```

##### 5. Assertion (선택 — 정확성 검증)
```
추가 위치: 각 HTTP Request 하위
- Response Assertion: Status Code = 200
- JSON Assertion: $.length() > 0 (GET 목록) 또는 $.id exists (GET 단건)
주의: 부하 테스트 시 Assertion 실패가 Error%에 포함됨
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
| R7 | 100 threads, Ramp-up 10초, Loop 50 | GET /vehicles/{id} | "단건 조회 vs 목록 조회 성능 차이?" |
| R8 | 100 threads, Ramp-up 10초, Loop 50 | GET /journeys/{id}/route | "GPS 스냅샷 대량 응답 시 직렬화 병목?" |

#### 단계별 실행 방법

##### JMeter GUI에서 공통 설정 추가하는 법

```
1. HTTP Request Defaults (모든 HTTP Request에 공통 적용)
   위치: Test Plan 우클릭 → Add → Config Element → HTTP Request Defaults
   설정:
     - Server Name or IP: localhost
     - Port Number: 8081
     - Protocol: http
   효과: 하위 Thread Group의 HTTP Request에서 이 값들을 비워두면 자동으로 이 값이 적용됨

2. HTTP Header Manager (공통 헤더)
   위치: Test Plan 우클릭 → Add → Config Element → HTTP Header Manager
   설정:
     - Name: Content-Type / Value: application/json
   효과: 모든 요청에 이 헤더가 자동 추가됨

3. Listener 추가
   위치: Test Plan 우클릭 → Add → Listener → Summary Report (또는 다른 Listener)
   - Test Plan 바로 아래에 두면 모든 Thread Group의 결과를 합산
   - 특정 Thread Group 아래에 두면 해당 그룹 결과만 수집

4. Thread Group 추가
   위치: Test Plan 우클릭 → Add → Threads (Users) → Thread Group
   설정:
     - Name: "R1-baseline-10threads" (알아보기 쉬운 이름)
     - Number of Threads: 10
     - Ramp-Up Period: 5
     - Loop Count: 100

5. Thread Group 안에 HTTP Request 추가
   위치: Thread Group 우클릭 → Add → Sampler → HTTP Request
   설정:
     - Method: GET
     - Path: /api/cartracking/vehicles
     - (Server, Port, Protocol은 비워두면 HTTP Request Defaults에서 가져옴)
```

참고: Config Element는 **계층 구조**로 동작한다.
- Test Plan 레벨에 두면 → 모든 Thread Group에 적용
- Thread Group 레벨에 두면 → 해당 Thread Group에만 적용
- 하위가 상위를 덮어씀 (override)

##### 방법: 단일 .jmx 파일에 Thread Group을 단계별로 분리

하나의 Test Plan 안에 R1~R8을 각각 별도 Thread Group으로 만들고,
실행할 단계만 Enable / 나머지는 Disable 해서 한 번에 하나씩 돌린다.

```
Test Plan: "REST API 부하 테스트"
├── HTTP Request Defaults          ← 공통 설정 (localhost, 8081, http)
├── HTTP Header Manager            ← Content-Type: application/json
│
├── [Thread Group] R1-baseline-10threads        ← Enable
│   ├── Number of Threads: 10
│   ├── Ramp-Up Period: 5
│   ├── Loop Count: 100
│   └── HTTP Request: GET /api/cartracking/vehicles
│
├── [Thread Group] R2-100threads                ← Disable (R1 끝난 후 이걸 Enable)
│   ├── Number of Threads: 100
│   ├── Ramp-Up Period: 10
│   ├── Loop Count: 100
│   └── HTTP Request: GET /api/cartracking/vehicles
│
├── [Thread Group] R3-500threads                ← Disable
│   ├── Number of Threads: 500
│   ├── Ramp-Up Period: 20
│   ├── Loop Count: 50
│   └── HTTP Request: GET /api/cartracking/vehicles
│
├── [Thread Group] R4-1000threads               ← Disable
│   ├── Number of Threads: 1000
│   ├── Ramp-Up Period: 30
│   ├── Loop Count: 30
│   └── HTTP Request: GET /api/cartracking/vehicles
│
├── [Thread Group] R5-POST-100threads           ← Disable
│   ├── Number of Threads: 100
│   ├── Ramp-Up Period: 10
│   ├── Loop Count: 100
│   └── HTTP Request: POST /api/cartracking/vehicles
│       Body: {"plateNumber":"TEST${__threadNum}-${__counter(,)}","type":"SEDAN","homeLat":37.4979,"homeLng":127.0276}
│
├── [Thread Group] R6-혼합-500threads           ← Disable
│   ├── Number of Threads: 500
│   ├── Ramp-Up Period: 20
│   ├── Loop Count: 50
│   ├── Throughput Controller (70%) → HTTP Request: GET /api/cartracking/vehicles
│   └── Throughput Controller (30%) → HTTP Request: POST /api/cartracking/vehicles
│       Body: {"plateNumber":"MIX${__threadNum}-${__counter(,)}","type":"SUV","homeLat":37.50,"homeLng":127.03}
│
├── [Thread Group] R7-단건조회-100threads       ← Disable
│   ├── Number of Threads: 100
│   ├── Ramp-Up Period: 10
│   ├── Loop Count: 50
│   └── HTTP Request: GET /api/cartracking/vehicles/1
│
├── [Thread Group] R8-GPS스냅샷-100threads      ← Disable
│   ├── Number of Threads: 100
│   ├── Ramp-Up Period: 10
│   ├── Loop Count: 50
│   └── HTTP Request: GET /api/cartracking/journeys/1/route
│
├── [Listener] Summary Report
├── [Listener] Response Times Over Time
└── [Listener] Transactions Per Second
```

##### 실행 절차 (R1 예시)

```
1. GUI에서 시나리오 검증
   - R1 Thread Group만 Enable, 나머지 Disable
   - View Results Tree 추가 (디버깅용)
   - 실행 버튼 (▶) → 요청/응답 정상 확인 → View Results Tree 삭제

2. CLI로 실제 부하 실행
   jmeter -n -t rest-loadtest.jmx -l loadtest-results/R1-baseline.jtl -e -o loadtest-results/phase1-R1-baseline/

3. 결과 확인
   - loadtest-results/phase1-R1-baseline/index.html 열기
   - Summary Report에서 Avg, p99, TPS, Error% 기록

4. 서버 재시작 (InMemory 데이터 초기화)
   - 특히 POST 테스트(R5, R6) 후에는 메모리에 데이터가 쌓이므로 필수

5. 다음 단계로
   - R1 Disable → R2 Enable → 저장 → CLI 재실행
   - 결과 파일명/디렉토리명을 단계별로 분리
```

##### R6 혼합 시나리오: Throughput Controller 설정법

```
Thread Group (R6)
├── Throughput Controller
│   ├── Based on: Percent Executions
│   ├── Throughput: 70.0
│   └── HTTP Request: GET /api/cartracking/vehicles
└── Throughput Controller
    ├── Based on: Percent Executions
    ├── Throughput: 30.0
    └── HTTP Request: POST /api/cartracking/vehicles
```
- Throughput Controller는 "이 하위 요소를 전체 반복 중 몇 %만 실행할 것인가"를 제어
- 70/30으로 설정하면 대략 GET 70%, POST 30% 비율로 섞임

##### R7, R8 사전 준비

```
R7 (단건 조회): 테스트 전에 차량이 최소 1대 이상 등록되어 있어야 함
  → 서버 시작 후 curl로 미리 등록:
  curl -X POST http://localhost:8081/api/cartracking/vehicles \
    -H 'Content-Type: application/json' \
    -d '{"plateNumber":"SEED01","type":"SEDAN","homeLat":37.4979,"homeLng":127.0276}'

R8 (GPS 스냅샷): Journey + LocationSnapshot 데이터가 필요
  → 시뮬레이터를 30초~1분 돌려서 데이터 생성:
  curl -X POST http://localhost:8081/api/cartracking/simulator/start
  (30초 대기)
  curl -X POST http://localhost:8081/api/cartracking/simulator/stop
  → 생성된 journeyId 확인:
  curl http://localhost:8081/api/cartracking/vehicles/1/journeys
```

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

## Phase 2: WebSocket broadcast 집중 테스트 (보조)

### 목표
REST 부하 없이 **WebSocket 연결 수 × 차량 수** 조합만으로 broadcast 성능을 집중 측정한다.
Phase 1 통합 시나리오에서 "WS가 병목인 것 같다"고 판단되면 여기서 상세 분석한다.

### 테스트 시나리오

N개 WebSocket 클라이언트를 연결한 상태에서 시뮬레이터를 돌려 broadcast 성능을 측정한다.
REST 부하는 주지 않는다 (순수 broadcast 성능).

#### JMeter WebSocket Test Plan 구성

```
Test Plan
├── Thread Group (WebSocket 연결 수)
│   ├── WebSocket Open Connection
│   │     URL: ws://localhost:8081/ws/vehicles
│   ├── WebSocket Read (서버에서 오는 메시지 수신 대기, Duration: 60초)
│   └── WebSocket Close
├── Listener: Summary Report
└── Listener: Response Times Over Time
```

#### 시뮬레이터 시작 (별도 터미널)

```bash
# W1~W2: 10대
curl -X POST 'http://localhost:8081/api/cartracking/simulator/start?count=10'

# W3~W4: 100대
curl -X POST 'http://localhost:8081/api/cartracking/simulator/start?count=100'

# W5: 1000대
curl -X POST 'http://localhost:8081/api/cartracking/simulator/start?count=1000'
```

#### 단계별 실행

| 단계 | WS 연결 수 | 차량 수 | msg/s | 초당 총 frame write | 뭘 보는가 |
|------|-----------|--------|-------|-------------------|----------|
| W1 | 10 | 10대 | 2/s | 20 | baseline: broadcast 잘 되나? |
| W2 | 100 | 10대 | 2/s | 200 | 연결만 많아도 느려지나? |
| W3 | 100 | 100대 | 20/s | 2,000 | 메시지 빈도 올리면? |
| W4 | 500 | 100대 | 20/s | 10,000 | 500명에게 20msg/s broadcast 가능? |
| W5 | 1000 | 1000대 | 200/s | 200,000 | 극한: 초당 20만 frame write |

> 초당 총 frame write = WS 연결 수 × msg/s
> W5는 EventLoop가 초당 20만 건의 writeAndFlush를 처리해야 한다.

#### 실행 절차

```
1. 서버 시작
2. curl로 시뮬레이터 시작 (차량 수 지정)
3. 30초 대기 (안정화)
4. JMeter WebSocket Test Plan 실행 (60초간 수신)
5. 결과 기록 → 시뮬레이터 중지 → 서버 재시작 → 다음 단계
```

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

1. **결과 표**: 차량 스케일업 × 각 개선의 Before/After

```
예시:
| 시나리오 | 차량 수 | 개선 전 REST TPS | 개선 후 | 개선 전 WS 지연 | 개선 후 | 원인 |
|---------|--------|----------------|--------|---------------|--------|------|
| S3      | 100대   | 1,200          | 5,800  | 150ms         | 30ms   | Keep-Alive 적용 |
| S4      | 500대   | 500            | 2,000  | 800ms         | 50ms   | broadcast retain 최적화 |
```

2. **ADR-V028**: "Netty 부하 테스트 결과 및 최적화 결정" 작성 (V027은 count 파라미터 추가에 사용됨)
3. **최종 아키텍처 한계**: "현재 단일 서버에서 최대 X대 차량, Y TPS, Z WS 동시연결 처리 가능"

### JMeter HTML 리포트

각 단계 실행 후 자동 생성되는 리포트를 `loadtest-results/` 디렉토리에 보관:
```
loadtest-results/
├── phase1-S1-10vehicles/
├── phase1-S2-50vehicles/
├── phase1-S3-100vehicles/
├── phase1-S4-500vehicles/
├── phase1-S5-1000vehicles/
├── phase1a-REST-pure/
├── phase2-W1-ws-baseline/
├── phase3-after-keepalive/
├── phase3-after-backlog/
└── ...
```

---

## 실행 순서 요약

1. JMeter 설치 + 플러그인 세팅 (WebSocket Sampler 필수)
2. 서버 + HiveMQ CE 기동, VisualVM 연결
3. **Phase 1 (핵심)** 차량 스케일업 통합 테스트: S1(10대) → S2(50대) → S3(100대) → S4(500대) → S5(1000대)
   - 각 단계: 시뮬레이터 start?count=N → JMeter(REST+WS) → 결과 기록 → stop → 재시작
4. **Phase 1-A (보조)** 시뮬레이터 OFF 상태에서 REST 순수 성능 측정
   - Phase 1 결과와 비교 → "MQTT broadcast가 REST에 미치는 영향" 파악
5. **Phase 2 (보조)** Phase 1에서 WS 병목이 의심되면, WS 연결 수만 집중 스케일업
6. **Phase 3** 병목 개선 1~8을 순서대로:
   - 1개 고친다
   - Phase 1의 S3 또는 S4 시나리오로 재측정 (동일 조건)
   - Before/After 기록
   - 다음 개선으로
7. **Phase 4** 결과 정리 + ADR-V028 작성

---

## 참고: 현재 아키텍처 제약

- Publisher/Subscriber 모두 **단일 MQTT 커넥션** 사용 (MqttClientFactory)
- Subscriber callback은 HiveMQ Client 내부 스레드에서 실행 (별도 오프로드 없음)
- InMemory 저장소 — GC pressure가 병목이 될 수 있음 (1000대 차량 seed data + GPS 스냅샷 누적)
- WebSocket broadcast는 Netty ChannelGroup — EventLoop 스레드에서 실행
- HTTP Keep-Alive 미지원 — 매 요청마다 TCP 연결 생성/종료
- 서버 시작 시 차량 1000대 자동 seed 등록 (ADR-V025)
- 시뮬레이터 `?count=N` 파라미터로 차량 수 제어 가능 (ADR-V027)
