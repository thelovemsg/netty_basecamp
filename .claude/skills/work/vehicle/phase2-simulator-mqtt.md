# Phase 2: Vehicle Simulator + MQTT Publish 구현 시나리오

작성일: 2026-04-20

---

## 오늘 한 일 요약 (2026-04-20)

### 한 마디로
**가짜 차량이 5초마다 GPS 좌표를 만들어서 Mosquitto 브로커에 보내는 시뮬레이터를 만들었다.**

### 구체적으로 만든 것

```
docker-compose.yml       ← Mosquitto 브로커를 Docker로 띄우는 설정
mosquitto/mosquitto.conf ← 브로커 동작 설정 (포트, 인증)
build.gradle             ← HiveMQ 클라이언트 라이브러리 추가

mqtt/
├── TelemetryPayload.java    ← 보낼 데이터 구조 (vehicleId, lat, lng, speed, status, timestamp)
├── MqttClientFactory.java   ← 브로커에 연결할 클라이언트 객체를 만드는 공장
└── TelemetryPublisher.java  ← 실제로 브로커에 JSON 메시지를 보내는 역할

simulator/
├── SeoulRouteGenerator.java ← 서울 안에서 랜덤 출발지/도착지를 뽑아주는 것
├── GpsInterpolator.java     ← 출발지~도착지 사이를 여러 좌표로 쪼개주는 것 (보간)
├── VehicleSimulator.java    ← 차량 1대의 움직임 루프 (5초마다 다음 좌표로 이동 → publish)
└── SimulatorBootstrap.java  ← 등록된 모든 차량에 대해 시뮬레이터를 일괄 시작/종료

rest/
├── SimulatorController.java  ← start/stop/status REST API
└── SimulatorRouteConfig.java ← 라우트 등록
```

### 동작 흐름

```
1. docker compose up -d              → Mosquitto 브로커 시작 (port 1883)
2. CarTrackingApplication 실행        → Netty 서버 시작 (port 8081) + 브로커에 MQTT 연결
3. POST /api/cartracking/vehicles    → 차량 등록
4. POST /api/cartracking/simulator/start → 시뮬레이터 시작
5. 로그에 5초마다 출력:
   MQTT publish [topic=vehicle/1/telemetry] [payload={"vehicleId":1,"latitude":37.54,"longitude":127.03,"speed":42.5,"status":"ON_TRIP","timestamp":...}]
6. POST /api/cartracking/simulator/stop  → 시뮬레이터 종료
```

### MQTT Client란?

MQTT Client = **브로커에 접속해서 메시지를 보내거나 받을 수 있는 프로그램(또는 라이브러리)**

우리 프로젝트에서:
- HiveMQ Client 라이브러리 = Java에서 MQTT 통신을 할 수 있게 해주는 도구
- `MqttClientFactory.create("이름")` → 클라이언트 객체 생성 (아직 연결 안 됨)
- `client.connect()` → 브로커(localhost:1883)에 TCP 연결
- `client.publishWith().topic("...").payload(json).send()` → 메시지 전송
- `client.disconnect()` → 연결 끊기

**HTTP로 비유하면:**
| MQTT | HTTP 비유 |
|------|-----------|
| MqttClient | HttpClient |
| connect() | TCP 연결 맺기 |
| publish() | POST 요청 보내기 |
| subscribe() | 웹훅 등록하기 (서버가 나한테 쏴줌) |
| broker | 서버 |

### 아직 안 한 것 (다음에 할 것)
- Broker → 서버가 subscribe해서 메시지 수신
- 서버 → 브라우저에 실시간 push (SSE or WebSocket)
- 화면에서 차량이 움직이는 걸 눈으로 보기

---

## MQTT 용어 정리

| 용어 | 정체 | 비유 |
|------|------|------|
| **MQTT** | 프로토콜 (규약) | "택배 규칙" — HTTP 같은 것 |
| **Mosquitto** | 브로커 구현체 | "CJ대한통운" — 택배 회사 |
| **HiveMQ Client** | 클라이언트 라이브러리 | "택배 보내는 앱" |

### Broker (Mosquitto)
- **우체국** 역할. 직접 구현하지 않고 Docker로 띄우기만 하면 됨
- `docker compose up -d` → port 1883에서 대기
- 메시지를 받아서 구독자(subscriber)에게 전달하는 중간 매개체

### Client (HiveMQ Client)
- 우리 코드에서 만드는 건 **이쪽만**
- `MqttClientFactory`가 broker에 접속할 클라이언트 객체를 생성 (host/port 설정을 한 곳에 모아두고 `create("이름")`만 호출하면 클라이언트가 나옴)
- `TelemetryPublisher`가 그 클라이언트로 broker에 메시지를 publish(보냄)

### 화면까지 연결하는 전체 흐름 (Phase 2 + 이후 Phase)

```
[시뮬레이터]                    [Mosquitto]                [Netty 서버]              [브라우저]
 VehicleSimulator               Broker                   MQTT Subscriber           HTML 화면
     │                            │                          │                        │
     │── publish ──────────────▶  │                          │                        │
     │   vehicle/1/telemetry      │── 구독자에게 전달 ──────▶ │                        │
     │                            │                          │── SSE or WebSocket ──▶ │
     │                            │                          │   위치 데이터 push      │ 지도에 마커 이동
```

| 구간 | 역할 | 프로토콜 | Phase |
|------|------|----------|-------|
| Simulator → Broker | 가짜 GPS 데이터 보내기 | MQTT publish | **Phase 2 (이번)** |
| Broker → Netty 서버 | 서버가 broker를 구독 | MQTT subscribe | 이후 Phase |
| Netty 서버 → 브라우저 | 실시간 데이터 전달 | SSE 또는 WebSocket | 이후 Phase |

## Phase 2에서 하는 것 / 안 하는 것

**하는 것:**
- 순수 Java 코드로 MQTT 메시지를 보내는 시뮬레이터 구현
- Netty 안 씀 — HiveMQ 라이브러리가 MQTT 통신을 담당
- 서버 안 만듦 — 시뮬레이터는 Virtual Thread에서 도는 Runnable
- 5초마다 가짜 GPS 좌표 생성 → JSON으로 broker에 publish
- 기존 cartracking Netty 서버(8081)에 시뮬레이터 start/stop REST API만 추가

**안 하는 것:**
- Broker → 서버 subscribe (이후 Phase)
- 서버 → 브라우저 실시간 push (이후 Phase)
- MQTT Explorer에서 눈으로 메시지 수신 확인만 하고 끝

**브로커 전략:**
- 지금은 Mosquitto(기성품)를 Docker로 띄워서 사용
- 나중에 Netty로 직접 구현한 브로커로 갈아끼울 예정 (career-transition-plan.md 5~7월 로드맵)

---

## 목표

가상 차량이 서울 영역 내 랜덤 경로로 이동하며 주기적으로 MQTT 브로커에 위치 telemetry를 publish하는 Simulator를 구현한다.
MQTT Explorer에서 `vehicle/+/telemetry` 메시지를 수신할 수 있으면 완료.

---

## 전제 조건

- 1단계 완료: 도메인 모델 (Vehicle, Journey, LocationSnapshot) + REST API + cartracking 독립 서버 (port 8081)
- Docker Desktop 실행 가능
- MQTT Explorer 설치 (검증용)

---

## 인프라 설정

### 1. build.gradle — HiveMQ MQTT Client 추가

```gradle
// MQTT 클라이언트 (ADR-V005)
implementation 'com.hivemq:hivemq-mqtt-client:1.3.3'
```

### 2. docker-compose.yml (프로젝트 루트)

```yaml
services:
  mosquitto:
    image: eclipse-mosquitto:2
    ports:
      - "1883:1883"
    volumes:
      - ./mosquitto/mosquitto.conf:/mosquitto/config/mosquitto.conf
```

### 3. mosquitto/mosquitto.conf (프로젝트 루트)

mosquitto.conf는 **Mosquitto 브로커의 설정 파일**. Docker 컨테이너가 시작될 때 이 파일을 읽고 동작 방식을 결정한다.

```conf
listener 1883
allow_anonymous true
```

| 설정 | 의미 | 왜 이렇게? |
|------|------|-----------|
| `listener 1883` | TCP 1883 포트에서 연결 대기 | MQTT 기본 포트. 클라이언트가 이 포트로 접속 |
| `allow_anonymous true` | 인증 없이 아무나 접속 가능 | 로컬 개발용이니까. 운영에서는 false + 비밀번호 설정 필요 |

**Mosquitto 2.x부터 기본값이 `allow_anonymous false`**이므로, 이 설정 없으면 클라이언트 연결이 거부된다.

#### 나중에 추가할 수 있는 설정 예시
```conf
listener 1883
allow_anonymous false
password_file /mosquitto/config/passwd    # 사용자/비밀번호 인증
log_type all                              # 모든 로그 출력
max_connections 10000                     # 최대 동시 접속 수
persistence true                          # 메시지를 디스크에 저장 (브로커 재시작 후 복구)
persistence_location /mosquitto/data/
```

---

## MQTT Topic 설계

### 현재 (Phase 2)
```
vehicle/{vehicleId}/telemetry    ← GPS, 속도, 상태 (5초마다)
```

### 향후 확장 시
```
vehicle/{vehicleId}/telemetry    ← GPS, 속도, 상태 (5초마다)
vehicle/{vehicleId}/event        ← 시동 ON/OFF, 급정거, 사고 등 (발생 시)
vehicle/{vehicleId}/command      ← 서버→차량 명령 (경로 변경, 정지 등)
```

| 토픽 | 빈도 | 방향 | QoS | 이유 |
|------|------|------|-----|------|
| `telemetry` | 5초마다 | 차량→서버 | 0 | 유실 OK, 다음 거 오면 됨 |
| `event` | 가끔 | 차량→서버 | 1 | 사고 알림 등 유실하면 안 됨 |
| `command` | 가끔 | 서버→차량 | 1 | 정지 명령은 반드시 도달해야 함 |

### 와일드카드 구독
```
vehicle/+/telemetry    ← + : 한 레벨만 아무거나 (모든 차량의 telemetry)
vehicle/1/#            ← # : 이하 전부 (차량 1의 모든 메시지)
vehicle/#              ← 차량 관련 전체 구독
```

---

## 패키지 구조

```
src/main/java/org/example/netty_basecamp/cartracking/
  simulator/
    RouteGenerator.java         ← 서울 영역 랜덤 출발지/목적지 생성
    GpsInterpolator.java        ← 두 점 사이 직선 보간 (N개 중간 좌표)
    VehicleSimulator.java       ← 차량 1대 행동 루프 (Runnable)
    SimulatorBootstrap.java     ← 등록 차량 조회 → 시뮬레이터 일괄 시작
  mqtt/
    MqttClientFactory.java      ← HiveMQ Async Client 생성
    TelemetryPublisher.java     ← vehicle/{id}/telemetry 토픽 publish
    TelemetryPayload.java       ← 직렬화용 record
```

---

## 클래스별 역할 요약 (Q&A 기반)

### MqttClientFactory
- broker에 연결할 클라이언트 객체를 만들어주는 공장
- host/port 설정을 한 곳에 모아두고 `create("이름")`만 호출하면 클라이언트가 나옴
- 반환된 client 객체로 `connect()`, `publishWith()`, `disconnect()` 사용

### GpsInterpolator (보간기)
- **두 점 사이를 쪼개서 중간 좌표들을 만들어주는 것**
- 차량이 A→B로 순간이동하면 실시간 추적이 안 되니까, 중간중간 위치를 만들어야 함
- `interpolate()`: ratio(0.0→1.0)로 출발지~도착지 사이를 균등 분할. steps=5면 출발지+도착지 포함 **6개** 좌표 반환
- `calculateSteps()`: 두 점 사이 거리(km) 기반으로 steps 수를 자동 계산 (최소 3, 최대 30)
- Location은 BigDecimal이지만 보간 계산은 double로 수행 — 가짜 GPS니까 소수점 오차 무의미, `Location.of(double, double)`이 다시 BigDecimal로 변환

### 시뮬레이터가 필요한 이유
- 실제 차량이 없으니까 **가짜로 움직이는 차량**을 만든 것
- 나중에 진짜 차량에서 GPS 데이터가 오면 시뮬레이터는 필요 없어짐

---

## 클래스별 상세 설계

### SeoulRouteGenerator

```java
package org.example.netty_basecamp.cartracking.simulator;

// 서울 영역 바운딩 박스
// 위도: 37.42 ~ 37.70
// 경도: 126.80 ~ 127.18

public class RouteGenerator {
    private static final double LAT_MIN = 37.42;
    private static final double LAT_MAX = 37.70;
    private static final double LNG_MIN = 126.80;
    private static final double LNG_MAX = 127.18;

    private final Random random = new Random();

    /** 서울 영역 내 랜덤 좌표 1개 */
    public Location randomLocation() { ... }

    /** 출발지 + 목적지 쌍 (서로 다른 좌표 보장) */
    public Location[] randomRoute() { ... }  // [0]=origin, [1]=destination
}
```

### GpsInterpolator

```java
package org.example.netty_basecamp.cartracking.simulator;

public class GpsInterpolator {
    /**
     * 두 점 사이를 직선 보간하여 N개 중간 좌표 반환.
     * steps 계산: 두 점 사이 거리(km) 기반 — 시속 60km 가정, 1분당 1km
     * 최소 steps = 3, 최대 = 30 (너무 길거나 짧은 경로 방지)
     */
    public List<Location> interpolate(Location origin, Location destination, int steps) { ... }

    /** 두 좌표 사이 대략적 거리(km) 계산 — Haversine 또는 단순 유클리드 */
    public int calculateSteps(Location origin, Location destination) { ... }
}
```

### VehicleSimulator (Runnable)

```java
package org.example.netty_basecamp.cartracking.simulator;

/**
 * Virtual Thread에서 실행 — blocking sleep 허용.
 * 데모 간격: 5초 (실제 1분 대신)
 */
public class VehicleSimulator implements Runnable {
    private final Long vehicleId;
    private final RouteGenerator seoulRouteGenerator;
    private final GpsInterpolator interpolator;
    private final TelemetryPublisher publisher;
    private final long intervalMillis;  // 기본 5000ms
    private volatile boolean running = true;

    @Override
    public void run() {
        while (running) {
            // 1. 랜덤 경로 생성
            Location[] route = seoulRouteGenerator.randomRoute();
            int steps = interpolator.calculateSteps(route[0], route[1]);
            List<Location> waypoints = interpolator.interpolate(route[0], route[1], steps);

            // 2. 각 waypoint마다 publish
            for (Location loc : waypoints) {
                if (!running) break;
                TelemetryPayload payload = new TelemetryPayload(
                    vehicleId, loc.getLatitude(), loc.getLongitude(),
                    calculateSpeed(steps), "ON_TRIP", System.currentTimeMillis()
                );
                publisher.publish(payload);
                Thread.sleep(intervalMillis);
            }
            // 3. 도착 → 새 경로 반복
        }
    }

    public void stop() { running = false; }
}
```

### SimulatorBootstrap

```java
package org.example.netty_basecamp.cartracking.simulator;

/**
 * 등록된 차량 목록 조회 → 차량마다 VehicleSimulator를 Virtual Thread로 시작.
 */
public class SimulatorBootstrap {
    private final VehicleRepository vehicleRepository;
    private final TelemetryPublisher publisher;
    private final ExecutorService virtualExecutor;
    private final List<VehicleSimulator> simulators = new ArrayList<>();

    public SimulatorBootstrap(VehicleRepository vehicleRepository,
                              TelemetryPublisher publisher) {
        this.vehicleRepository = vehicleRepository;
        this.publisher = publisher;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void start() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        for (Vehicle v : vehicles) {
            VehicleSimulator sim = new VehicleSimulator(
                v.getId(), new RouteGenerator(), new GpsInterpolator(),
                publisher, 5000L
            );
            simulators.add(sim);
            virtualExecutor.submit(sim);
        }
    }

    public void stop() {
        simulators.forEach(VehicleSimulator::stop);
        virtualExecutor.shutdown();
    }
}
```

### MqttClientFactory

```java
package org.example.netty_basecamp.cartracking.mqtt;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.MqttClient;

public class MqttClientFactory {
    private final String host;
    private final int port;

    public MqttClientFactory(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Mqtt3AsyncClient create(String clientId) {
        return MqttClient.builder()
            .useMqttVersion3()
            .serverHost(host)
            .serverPort(port)
            .identifier(clientId)
            .automaticReconnect().applyAutomaticReconnect()
            .buildAsync();
    }
}
```

### TelemetryPublisher

```java
package org.example.netty_basecamp.cartracking.mqtt;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;

public class TelemetryPublisher {
    private final Mqtt3AsyncClient client;
    private final ObjectMapper objectMapper;

    public TelemetryPublisher(Mqtt3AsyncClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    /** 연결 시작 — 서버 부팅 시 1회 호출 */
    public void connect() {
        client.connect().join();  // blocking — 시작 시 한번만
    }

    /** vehicle/{vehicleId}/telemetry 토픽에 JSON publish */
    public void publish(TelemetryPayload payload) {
        String topic = "vehicle/" + payload.vehicleId() + "/telemetry";
        byte[] json = objectMapper.writeValueAsBytes(payload);
        client.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(json)
            .send();
    }

    public void disconnect() {
        client.disconnect().join();
    }
}
```

### TelemetryPayload

```java
package org.example.netty_basecamp.cartracking.mqtt;

import java.math.BigDecimal;

public record TelemetryPayload(
    Long vehicleId,
    BigDecimal lat,
    BigDecimal lng,
    double speed,
    String status,
    long timestamp
) {}
```

---

## 진입점 변경

### CarTrackingAppConfig — MQTT + Simulator DI 조립

```java
public class CarTrackingAppConfig {
    private static final String MQTT_HOST = "localhost";
    private static final int MQTT_PORT = 1883;

    public static RouteRegistry initRoutes() { ... }  // 기존 유지

    public static SimulatorBootstrap initSimulator(VehicleRepository vehicleRepository) {
        MqttClientFactory factory = new MqttClientFactory(MQTT_HOST, MQTT_PORT);
        Mqtt3AsyncClient mqttClient = factory.create("cartracking-simulator");
        TelemetryPublisher publisher = new TelemetryPublisher(mqttClient);
        publisher.connect();
        return new SimulatorBootstrap(vehicleRepository, publisher);
    }
}
```

### CarTrackingApplication — 시뮬레이션 시작 옵션

**옵션 A: 서버 시작 후 자동 시작**
```java
public static void main(String[] args) throws Exception {
    VehicleRepository vehicleRepo = new InMemoryVehicleRepository();
    RouteRegistry registry = CarTrackingAppConfig.initRoutes(vehicleRepo);

    // Simulator 준비 (차량 등록 후 수동 시작)
    SimulatorBootstrap simulator = CarTrackingAppConfig.initSimulator(vehicleRepo);

    new CarTrackingServer(8081, registry).start();
}
```

**옵션 B: REST API로 시뮬레이션 트리거 (권장)**
```
POST /api/cartracking/simulator/start  → SimulatorBootstrap.start()
POST /api/cartracking/simulator/stop   → SimulatorBootstrap.stop()
```

---

## MQTT Topic & Payload 스펙

```
Topic: vehicle/{vehicleId}/telemetry
QoS: 1 (AT_LEAST_ONCE)

Payload (JSON):
{
  "vehicleId": 1,
  "lat": 37.501200,
  "lng": 127.039600,
  "speed": 42.5,
  "status": "ON_TRIP",
  "timestamp": 1713600000000
}
```

---

## 테스트

### GpsInterpolatorTest

```java
@Test
void 두_점_사이_보간_결과는_출발지로_시작하고_목적지로_끝난다() { ... }

@Test
void steps가_5이면_중간_좌표_5개를_반환한다() { ... }

@Test
void 보간_좌표는_출발지에서_목적지로_단조_증가_또는_감소한다() { ... }
```

### RouteGeneratorTest

```java
@Test
void 생성된_좌표는_서울_바운딩_박스_내에_있다() { ... }

@Test
void 출발지와_목적지는_서로_다르다() { ... }
```

---

## 검증 시나리오 (수동)

```bash
# 1. Mosquitto 브로커 시작
docker compose up -d

# 2. MQTT Explorer 설정
#    Host: localhost, Port: 1883
#    Subscribe: vehicle/+/telemetry

# 3. 서버 시작 + 차량 등록
./gradlew run  # 또는 IDE에서 CarTrackingApplication 실행

curl -X POST http://localhost:8081/api/cartracking/vehicles \
  -H "Content-Type: application/json" \
  -d '{"plateNumber":"12가3456","type":"SEDAN"}'

curl -X POST http://localhost:8081/api/cartracking/vehicles \
  -H "Content-Type: application/json" \
  -d '{"plateNumber":"34나7890","type":"SUV"}'

# 4. 시뮬레이션 시작
curl -X POST http://localhost:8081/api/cartracking/simulator/start

# 5. MQTT Explorer에서 5초 간격 telemetry 메시지 수신 확인

# 6. 시뮬레이션 종료
curl -X POST http://localhost:8081/api/cartracking/simulator/stop

# 7. 테스트 실행
./gradlew test
```

---

## 구현 순서 (권장)

| 순서 | 작업 | 의존 관계 |
|------|------|----------|
| 1 | `docker-compose.yml` + `mosquitto.conf` | 없음 |
| 2 | `build.gradle`에 HiveMQ 의존성 추가 | 없음 |
| 3 | `TelemetryPayload` record | 없음 |
| 4 | `MqttClientFactory` | HiveMQ 의존성 |
| 5 | `TelemetryPublisher` | MqttClientFactory, TelemetryPayload |
| 6 | `RouteGenerator` + 테스트 | Location VO |
| 7 | `GpsInterpolator` + 테스트 | Location VO |
| 8 | `VehicleSimulator` | RouteGenerator, GpsInterpolator, TelemetryPublisher |
| 9 | `SimulatorBootstrap` | VehicleSimulator, VehicleRepository |
| 10 | `CarTrackingAppConfig` 수정 — DI 조립 | 전체 |
| 11 | `CarTrackingApplication` 수정 또는 Simulator REST API 추가 | AppConfig |
| 12 | 통합 검증 (docker + MQTT Explorer) | 전체 |

---

## 주의사항

- `VehicleSimulator`는 **Virtual Thread**에서 실행 — `Thread.sleep()` 사용 가능 (EventLoop 아님)
- `TelemetryPublisher.publish()`는 HiveMQ Async API 사용 — fire-and-forget 스타일
- `domains/` 패키지에는 MQTT 관련 코드 금지 — `simulator/`와 `mqtt/`는 인프라 레이어
- MQTT Client의 `connect().join()`은 서버 부팅 시 한 번만 호출 (blocking OK)
- 데모 간격은 5초 권장 — 실제 1분은 검증 시 너무 느림
