# Phase 2: Vehicle Simulator + MQTT Publish 구현 시나리오

작성일: 2026-04-20

---

## MQTT 개념 정리

### Broker (Mosquitto)
- **우체국** 역할. 직접 구현하지 않고 Docker로 띄우기만 하면 됨
- `docker compose up -d` → port 1883에서 대기
- 메시지를 받아서 구독자(subscriber)에게 전달하는 중간 매개체

### Client (HiveMQ Client)
- 우리 코드에서 만드는 건 **이쪽만**
- `MqttClientFactory`가 broker에 접속할 클라이언트 객체를 생성
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

```conf
listener 1883
allow_anonymous true
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

## 클래스별 상세 설계

### RouteGenerator

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
    private final RouteGenerator routeGenerator;
    private final GpsInterpolator interpolator;
    private final TelemetryPublisher publisher;
    private final long intervalMillis;  // 기본 5000ms
    private volatile boolean running = true;

    @Override
    public void run() {
        while (running) {
            // 1. 랜덤 경로 생성
            Location[] route = routeGenerator.randomRoute();
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
