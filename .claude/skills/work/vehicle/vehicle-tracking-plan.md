1# 차량 추적 시스템 — 설계 및 작업 계획

작성일: 2026-04-16

---

## 목표

차량 대여 플랫폼의 **관리자 시점** 실시간 차량 추적 시스템.
가상 차량들이 랜덤 경로로 자율 이동하고, 관리자는 지도에서 실시간으로 위치를 확인한다.

**부가 목표**: 1분 단위 위치 스냅샷을 저장하여 운행 이력 분석(소요시간, 경로 재현 등)에 활용 가능한 데이터 구조 확보.

---

## 시스템 전체 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                      MQTT Broker (Mosquitto)                    │
└──────────┬──────────────────────────────┬───────────────────────┘
           │ publish telemetry            │ subscribe command
           │                              │
┌──────────▼──────────┐        ┌──────────▼───────────────────────┐
│  Vehicle Simulator  │        │         Netty Server             │
│  (가상 차량 N대)     │        │                                  │
│                     │        │  MQTT Subscribe                  │
│  랜덤 경로 생성       │        │    → Domain 상태 갱신            │
│  GPS 좌표 보간       │        │    → WebSocket Broadcast         │
│  1분마다 위치 publish │        │                                  │
└─────────────────────┘        │  REST API (차량 등록/목록/이력)    │
                               │  WebSocket (실시간 위치 push)     │
                               └──────────────┬───────────────────┘
                                              │ WebSocket
                               ┌──────────────▼───────────────────┐
                               │      Admin Dashboard (Browser)   │
                               │                                  │
                               │  Leaflet.js 지도                 │
                               │  차량 마커 실시간 이동            │
                               │  차량 상태 패널                  │
                               └──────────────────────────────────┘
```

---

## MQTT Topic 구조

```
vehicle/{vehicleId}/telemetry     ← 차량 → Broker  (위치, 속도, 상태)
vehicle/{vehicleId}/status        ← 차량 → Broker  (online/offline)
vehicle/{vehicleId}/command       ← 서버 → Broker  (recall, stop 등)

서버 구독 패턴:
  vehicle/+/telemetry   → 전체 차량 위치 한 번에 수신
```

### Telemetry Payload

```json
{
  "vehicleId": "V001",
  "lat": 37.5012,
  "lng": 127.0396,
  "speed": 42.5,
  "status": "ON_TRIP",
  "tripId": "T-20260416-001",
  "timestamp": "2026-04-16T10:23:11Z"
}
```

---

## 도메인 모델

### Aggregate Roots

#### Vehicle (차량 자체)
```
Vehicle
  - vehicleId       String
  - plateNumber     String       (차량 번호판)
  - type            VehicleType  (SEDAN/SUV/VAN)
  - status          VehicleStatus (AVAILABLE/ON_TRIP/OFFLINE)
  - homeLocation    Location     (기본 주차 위치)

  + startTrip()     → status: ON_TRIP
  + returnHome()    → status: AVAILABLE
  + goOffline()     → status: OFFLINE

불변식:
  - ON_TRIP 중에 startTrip() 호출 불가
```

#### Trip (운행 단위)
```
Trip
  - tripId          String
  - vehicleId       String
  - origin          Location     (출발지)
  - destination     Location     (목적지)
  - startedAt       Instant
  - arrivedAt       Instant?     (null이면 진행 중)
  - status          TripStatus   (IN_PROGRESS/COMPLETED)

  + arrive(arrivedAt)            → status: COMPLETED
  + getDuration()                → arrivedAt - startedAt

불변식:
  - COMPLETED 상태에서 arrive() 호출 불가
  - origin == destination 불가
```

#### LocationSnapshot (1분마다 찍는 위치 기록)
```
LocationSnapshot
  - snapshotId      String
  - tripId          String
  - vehicleId       String
  - location        Location     (lat, lng)
  - speed           double
  - capturedAt      Instant

불변식:
  - capturedAt은 미래 시각 불가
```

### Value Objects

```
Location        (lat: double, lng: double)
VehicleType     Enum (SEDAN, SUV, VAN)
VehicleStatus   Enum (AVAILABLE, ON_TRIP, OFFLINE)
TripStatus      Enum (IN_PROGRESS, COMPLETED)
```

---

## 패키지 구조

```
domains/vehicle/
  domain/
    Vehicle.java
    Trip.java
    LocationSnapshot.java
    Location.java                     ← VO
    VehicleType.java                  ← Enum
    VehicleStatus.java                ← Enum
    TripStatus.java                   ← Enum
    VehicleRepository.java            ← 인터페이스
    TripRepository.java               ← 인터페이스
      + findActiveByVehicleId(id)     ← 진행 중 Trip 조회
    LocationSnapshotRepository.java   ← 인터페이스
      + findAllByTripId(tripId)       ← 경로 재현용
  application/
    VehicleApplicationService.java
      + register(VehicleCreate)
      + listVehicles()
    TripApplicationService.java
      + startTrip(vehicleId, origin, destination)
      + recordSnapshot(vehicleId, location, speed)   ← 1분마다 호출
      + completeTrip(vehicleId, arrivedAt)
  infrastructure/
    InMemoryVehicleRepository.java
    InMemoryTripRepository.java
    InMemoryLocationSnapshotRepository.java

mqtt/
  MqttBootstrap.java
  subscriber/
    VehicleTelemetrySubscriber.java   ← MQTT 수신 → TripApplicationService
  publisher/
    VehicleCommandPublisher.java      ← 서버 → 차량 명령 발행
  config/
    VehicleMqttConfig.java

netty/
  websocket/
    WebSocketHandler.java             ← 브라우저 연결 관리
    VehicleLocationBroadcaster.java   ← telemetry → WebSocket push

simulator/
  SimulatorBootstrap.java             ← N대 차량 동시 시작
  VehicleSimulator.java               ← 차량 1대 행동 (Runnable)
  RouteGenerator.java                 ← 랜덤 출발지/목적지 생성
  GpsInterpolator.java                ← 경로 → 1분 단위 좌표 보간
```

---

## 데이터 흐름 (연결 고리)

```
[등록]
  POST /api/vehicles
    → VehicleApplicationService.register()
    → InMemoryVehicleRepository.save()

[시뮬레이션 시작]
  SimulatorBootstrap
    → VehicleRepository.findAll()
    → 차량마다 VehicleSimulator 스레드 시작

[운행 중]
  VehicleSimulator (1분마다)
    → GpsInterpolator.next()         ← 다음 좌표 계산
    → MQTT publish(telemetry)

  VehicleTelemetrySubscriber
    → TripApplicationService.recordSnapshot()
         → Trip 상태 갱신
         → LocationSnapshotRepository.save()    ← DB 저장 포인트
    → VehicleLocationBroadcaster.broadcast()
         → 연결된 WebSocket 전체 push

[도착]
  VehicleSimulator (목적지 도달 감지)
    → MQTT publish(status: COMPLETED)
    → TripApplicationService.completeTrip()
         → Trip.arrive() → COMPLETED
         → Vehicle.returnHome() → AVAILABLE
```

---

## 나중에 가능한 분석 쿼리

| 분석 | 필요 데이터 |
|------|------------|
| 특정 차량 월별 운행 이력 | Trip (vehicleId, startedAt) |
| 운행 경로 재현 (replay) | LocationSnapshot (tripId, capturedAt 순) |
| 평균 운행 소요시간 | Trip (arrivedAt - startedAt) |
| 이상 정차 감지 | LocationSnapshot (연속 speed ≈ 0) |
| 구역별 차량 밀도 | LocationSnapshot (lat, lng 그룹핑) |

---

## 작업 순서

| 단계 | 작업 | 검증 방법 |
|------|------|----------|
| 1 | `domains/vehicle/` 도메인 모델 + TDD | JUnit |
| 2 | `simulator/` 가상 차량 MQTT publish | MQTT Explorer |
| 3 | `mqtt/` 서버 subscribe → ApplicationService | 콘솔 로그 |
| 4 | `netty/websocket/` + broadcast | wscat |
| 5 | 브라우저 Leaflet 지도 + WebSocket | 실제 화면 |
| 6 | DB 연동 (Phase 2) | LocationSnapshot 적재 확인 |

**현재 위치: 1단계 진입 전**

---

## 설계 결정 사항

### Vehicle을 먼저 REST API로 등록 후 시뮬레이션 시작
- Spring처럼 자동 주입이 아니므로, 등록된 차량 데이터를 Simulator가 읽어서 시작
- `POST /api/vehicles` → InMemory 저장 → SimulatorBootstrap이 읽어서 스레드 시작

### Trip을 Vehicle과 분리한 이유
- Vehicle: "이 차가 지금 뭐하는 중?"
- Trip: "이 운행은 언제 시작해서 얼마나 걸렸어?"
- 미래 분석 쿼리는 Trip + LocationSnapshot 조합으로 충분

### LocationSnapshot을 Trip 내부 컬렉션이 아닌 별도 AR로 분리한 이유
- 장거리 운행 시 수백 개 Snapshot이 Trip 안에 쌓이면 메모리/조회 부하
- DB 적재 시 bulk insert 최적화 가능
- 경로 재현 쿼리가 `findAllByTripId(tripId)` 한 줄로 분리됨

### MQTT Broker 선택

#### 브로커란
클라이언트(Simulator, 서버)가 직접 통신하지 않고 중간에서 메시지를 받아 중계하는 **별도 서버 프로세스**. Docker로 띄워서 운영하며 Java 코드와 무관하게 독립 실행된다.

#### 검토 대상

**1. Eclipse Mosquitto**
- MQTT 3.1/3.1.1/5.0 지원
- C로 작성된 초경량 브로커 — 메모리 사용량 수 MB 수준
- 설정 파일 하나로 동작, Docker 한 줄로 시작
- 학습/IoT 환경에서 사실상 표준
- 대시보드 없음 — MQTT Explorer 같은 별도 툴로 모니터링

**2. HiveMQ Community Edition**
- Java 기반, MQTT 3.1.1 + 5.0 지원
- 내장 웹 대시보드 제공 (토픽/클라이언트 현황 시각화)
- 플러그인 시스템으로 확장 가능
- 메모리 사용량이 Mosquitto 대비 높음 (JVM 기반)
- Enterprise 버전은 클러스터링, 고가용성 지원

**3. EMQX**
- Erlang 기반, 대규모 IoT 트래픽 처리에 강점
- 웹 대시보드 기본 제공 (토픽 모니터링, 클라이언트 관리)
- MQTT 3.1.1/5.0 + WebSocket 지원 내장
- 학습용으로는 기능이 과함

**4. VerneMQ**
- Erlang 기반, 분산 클러스터 특화
- 대규모 수평 확장 목적으로 설계
- 학습/단일 노드 프로젝트에는 과함

#### 비교

| 항목 | Mosquitto | HiveMQ CE | EMQX |
|------|-----------|-----------|------|
| 언어 | C | Java | Erlang |
| 메모리 | 매우 가벼움 | 보통 (JVM) | 보통 |
| 대시보드 | 없음 | 있음 | 있음 |
| Docker 설정 난이도 | 낮음 | 보통 | 보통 |
| MQTT 5.0 | O | O | O |
| 학습 자료 | 매우 많음 | 많음 | 많음 |
| 단일 노드 적합성 | 최적 | 적합 | 적합 |
| 확장성 (미래) | 제한적 | Enterprise 필요 | 오픈소스로 가능 |

#### 결정: Eclipse Mosquitto

```yaml
# docker-compose.yml
services:
  mosquitto:
    image: eclipse-mosquitto:2
    ports:
      - "1883:1883"
    volumes:
      - ./mosquitto.conf:/mosquitto/config/mosquitto.conf
```

**이유:**
- 학습/데모 목적에 가장 단순한 선택
- 설정 최소화 — 브로커 운영 자체에 시간 쓰지 않고 아키텍처에 집중
- MQTT 클라이언트(HiveMQ Client) 동작 검증용으로 충분
- 실제 서비스 전환 시 브로커만 EMQX/HiveMQ로 교체하면 됨 (클라이언트 코드 변경 없음)

---

### MQTT Java 클라이언트 라이브러리 선택

#### 검토 대상

Java 생태계에서 쓸 수 있는 MQTT 클라이언트는 크게 3가지다.

**1. Eclipse Paho v3 (`org.eclipse.paho:org.eclipse.paho.client.mqttv3`)**
- MQTT 3.1.1 지원
- 오랫동안 가장 많이 쓰인 레퍼런스 구현체
- API: 콜백 기반 (`MqttCallback` 인터페이스 구현)
- 문제점:
  - 마지막 주요 릴리즈가 2019년대 수준 — 사실상 유지보수 중단
  - 재연결 처리가 수동 (`setAutomaticReconnect(true)`가 있지만 불안정하다고 알려짐)
  - 스레드 안전성 이슈가 오래된 GitHub issue로 남아있음
  - MQTT 5.0 미지원

**2. Eclipse Paho v5 (`org.eclipse.paho:org.eclipse.paho.mqttv5.client`)**
- MQTT 5.0 지원 (v3 API와 별개 패키지)
- v3 대비 개선된 API 구조
- 문제점:
  - v3보다도 커뮤니티 채택률이 낮음
  - 역시 Eclipse 재단의 소극적 유지보수 문제에서 자유롭지 않음
  - 실사용 레퍼런스가 적어 트러블슈팅 자료 부족

**3. HiveMQ MQTT Client (`com.hivemq:hivemq-mqtt-client`)**
- MQTT 3.1.1 + 5.0 모두 지원 (단일 라이브러리)
- HiveMQ 회사가 자사 브로커와 함께 적극적으로 유지보수 중
- API 스타일 3종 제공:
  - `Blocking` — 동기 (테스트/단순 사용)
  - `Async` — `CompletableFuture` 기반 비동기
  - `Reactive` — RxJava 기반 스트림
- 자동 재연결 + 재구독이 내장 (`automaticReconnect()` 설정)
- 연결 상태 이벤트 리스너 지원

#### 비교

| 항목 | Paho v3 | Paho v5 | HiveMQ Client |
|------|---------|---------|---------------|
| MQTT 버전 | 3.1.1 | 5.0 | 3.1.1 + 5.0 |
| 유지보수 상태 | 사실상 중단 | 미흡 | 활발 |
| API 스타일 | 콜백 (blocking) | 콜백 | Blocking / Async / Reactive |
| 자동 재연결 | 불안정 | 부분 | 내장, 안정적 |
| Netty 비동기 철학 부합 | 낮음 | 보통 | 높음 (Async API) |
| 커뮤니티/레퍼런스 | 많음 (오래된 것) | 적음 | 증가 중 |
| 스레드 안전성 | 이슈 있음 | 불분명 | 설계 단계부터 고려 |

#### MQTT 3.1.1 vs 5.0

| 기능 | 3.1.1 | 5.0 |
|------|-------|-----|
| 기본 pub/sub | O | O |
| QoS 0/1/2 | O | O |
| 메시지 만료 (expiry) | X | O |
| 공유 구독 (Shared Subscription) | X | O |
| 상세 에러 코드 (Reason Code) | X | O |
| 사용자 정의 속성 (User Properties) | X | O |

차량 추적 수준에서는 3.1.1으로 충분하다.
5.0의 추가 기능(메시지 만료, 공유 구독)은 다중 서버 확장 시 유용하지만 현재 범위가 아니다.

#### 결정: HiveMQ MQTT Client + MQTT 3.1.1

```gradle
implementation 'com.hivemq:hivemq-mqtt-client:1.3.3'
```

**이유:**
- Paho는 유지보수가 사실상 멈췄고 재연결 안정성 문제가 실제 이슈로 보고됨
- HiveMQ Client의 `Async` API가 Netty의 비동기 철학과 부합
- 자동 재연결 내장 — 차량 시뮬레이터가 재시작되거나 브로커 일시 중단 시 서버 코드 변경 없이 복구
- MQTT 5.0으로 업그레이드 시 클라이언트 라이브러리 교체 없이 대응 가능

**사용 예시 (Async API)**
```java
Mqtt3AsyncClient client = MqttClient.builder()
    .useMqttVersion3()
    .serverHost("localhost")
    .serverPort(1883)
    .automaticReconnect().applyAutomaticReconnect()
    .buildAsync();

client.connect()
    .thenCompose(ack -> client.subscribeWith()
        .topicFilter("vehicle/+/telemetry")
        .qos(MqttQos.AT_LEAST_ONCE)
        .callback(publish -> {
            String payload = new String(publish.getPayloadAsBytes());
            // → VehicleLocationBroadcaster.broadcast()
        })
        .send());
```

---

### 지도 라이브러리: Leaflet.js + OpenStreetMap 선택
**검토한 대안**: 네이버 지도 API

| | Leaflet.js + OpenStreetMap | 네이버 지도 API |
|--|--|--|
| 가입/카드 등록 | 불필요 | NCP 회원가입 + 카드 필수 |
| API Key | 없음 | Client ID 발급 필요 |
| 한국 지도 품질 | 보통 | 정확 |
| 설정 난이도 | 즉시 시작 | NCP 셋업 필요 |
| 비용 | 완전 무료 | 한도 내 무료 (초과 시 과금) |

**결정**: 학습/데모 목적이므로 Leaflet.js + OpenStreetMap 사용.
차량 마커 이동 + 경로 추적 기능은 Leaflet으로 충분히 구현 가능.
실제 서비스 수준이 필요해지는 시점에 네이버 지도로 교체 검토.
