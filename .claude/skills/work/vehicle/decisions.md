# Vehicle Tracking — Architectural Decision Records

차량 추적 시스템의 주요 의사결정 사항을 기록한 문서.

업데이트 히스토리
- 2026-04-16 생성
- 2026-04-20 ADR-V010 ~ V012 추가

---

## 목차
1. [ADR-V001: Vehicle REST 등록 후 시뮬레이션 시작](#adr-v001)
2. [ADR-V002: Trip을 Vehicle과 별도 AR로 분리](#adr-v002)
3. [ADR-V003: LocationSnapshot을 Trip 내부 컬렉션이 아닌 별도 AR로 분리](#adr-v003)
4. [ADR-V004: MQTT Broker — Eclipse Mosquitto 선택](#adr-v004)
5. [ADR-V005: MQTT Java Client — HiveMQ Client + MQTT 3.1.1 선택](#adr-v005)
6. [ADR-V006: 지도 라이브러리 — Leaflet.js + OpenStreetMap 선택](#adr-v006)
7. [ADR-V007: Location VO 좌표를 BigDecimal(scale=6)로 저장](#adr-v007)
8. [ADR-V008: 위치 데이터 저장소 — 시계열 DB 대신 RDB 사용](#adr-v008)
9. [ADR-V009: 위치 추적을 별도 Bounded Context(tracking)로 분리](#adr-v009)
10. [ADR-V010: cartracking 전용 독립 Netty 서버 구성](#adr-v010)
11. [ADR-V011: VirtualExecutor를 서버 생명주기에서 생성·주입·종료](#adr-v011)
12. [ADR-V012: HttpRoutingHandler에 exceptionCaught 추가 — 파이프라인 예외 처리](#adr-v012)

---

## ADR-V001: Vehicle REST 등록 후 시뮬레이션 시작 {#adr-v001}
**날짜**: 2026-04-16

### 결정
- `POST /api/vehicles`로 차량을 먼저 등록한 뒤, SimulatorBootstrap이 등록된 차량을 읽어서 시뮬레이션 스레드를 시작한다.

### 판단 근거
- Spring처럼 자동 주입이 없으므로, 등록된 차량 데이터를 Simulator가 명시적으로 읽어서 시작해야 함
- 흐름: `POST /api/vehicles` → InMemory 저장 → `SimulatorBootstrap`이 `VehicleRepository.findAll()` → 차량마다 `VehicleSimulator` 스레드 시작

---

## ADR-V002: Trip을 Vehicle과 별도 AR로 분리 {#adr-v002}
**날짜**: 2026-04-16

### 결정
- Vehicle과 Trip을 별도 Aggregate Root로 분리한다.

### 판단 근거
- **Vehicle**: "이 차가 지금 뭐하는 중?" — 현재 상태 중심
- **Trip**: "이 운행은 언제 시작해서 얼마나 걸렸어?" — 운행 이력 중심
- 관심사가 다르고, 미래 분석 쿼리(월별 운행 이력, 평균 소요시간 등)는 Trip + LocationSnapshot 조합으로 충분

---

## ADR-V003: LocationSnapshot을 Trip 내부 컬렉션이 아닌 별도 AR로 분리 {#adr-v003}
**날짜**: 2026-04-16

### 결정
- LocationSnapshot을 Trip 내부 `List<LocationSnapshot>`이 아닌 별도 Aggregate Root로 분리한다.

### 판단 근거
- 장거리 운행 시 수백 개 Snapshot이 Trip 안에 쌓이면 메모리/조회 부하
- DB 적재 시 bulk insert 최적화 가능
- 경로 재현 쿼리가 `findAllByTripId(tripId)` 한 줄로 깔끔하게 분리됨

---

## ADR-V004: MQTT Broker — Eclipse Mosquitto 선택 {#adr-v004}
**날짜**: 2026-04-16

### 검토 대상
| 항목 | Mosquitto | HiveMQ CE | EMQX | VerneMQ |
|------|-----------|-----------|------|---------|
| 언어 | C | Java | Erlang | Erlang |
| 메모리 | 매우 가벼움 | 보통 (JVM) | 보통 | 보통 |
| 대시보드 | 없음 | 있음 | 있음 | 없음 |
| 학습 자료 | 매우 많음 | 많음 | 많음 | 보통 |

### 결정
Eclipse Mosquitto 선택.

### 판단 근거
- 학습/데모 목적에 가장 단순한 선택
- 설정 최소화 — 브로커 운영 자체에 시간 쓰지 않고 아키텍처에 집중
- MQTT 클라이언트(HiveMQ Client) 동작 검증용으로 충분
- 실제 서비스 전환 시 브로커만 EMQX/HiveMQ로 교체하면 됨 (클라이언트 코드 변경 없음)

### 운영 방법
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

---

## ADR-V005: MQTT Java Client — HiveMQ Client + MQTT 3.1.1 선택 {#adr-v005}
**날짜**: 2026-04-16

### 검토 대상
| 항목 | Paho v3 | Paho v5 | HiveMQ Client |
|------|---------|---------|---------------|
| MQTT 버전 | 3.1.1 | 5.0 | 3.1.1 + 5.0 |
| 유지보수 상태 | 사실상 중단 | 미흡 | 활발 |
| API 스타일 | 콜백 | 콜백 | Blocking / Async / Reactive |
| 자동 재연결 | 불안정 | 부분 | 내장, 안정적 |
| Netty 비동기 철학 부합 | 낮음 | 보통 | 높음 (Async API) |

### 결정
HiveMQ MQTT Client (`com.hivemq:hivemq-mqtt-client:1.3.3`) + MQTT 3.1.1 사용.

### 판단 근거
- Paho는 유지보수가 사실상 멈췄고 재연결 안정성 문제가 실제 이슈로 보고됨
- HiveMQ Client의 `Async` API가 Netty의 비동기 철학과 부합
- 자동 재연결 내장 — 차량 시뮬레이터 재시작이나 브로커 일시 중단 시 서버 코드 변경 없이 복구
- MQTT 5.0으로 업그레이드 시 클라이언트 라이브러리 교체 없이 대응 가능

### MQTT 3.1.1 선택 이유
- 차량 추적 수준에서는 3.1.1으로 충분
- 5.0의 추가 기능(메시지 만료, 공유 구독)은 다중 서버 확장 시 유용하지만 현재 범위가 아님

---

## ADR-V006: 지도 라이브러리 — Leaflet.js + OpenStreetMap 선택 {#adr-v006}
**날짜**: 2026-04-16

### 검토 대상
| | Leaflet.js + OpenStreetMap | 네이버 지도 API |
|--|--|--|
| 가입/카드 등록 | 불필요 | NCP 회원가입 + 카드 필수 |
| API Key | 없음 | Client ID 발급 필요 |
| 한국 지도 품질 | 보통 | 정확 |
| 설정 난이도 | 즉시 시작 | NCP 셋업 필요 |
| 비용 | 완전 무료 | 한도 내 무료 (초과 시 과금) |

### 결정
Leaflet.js + OpenStreetMap 사용.

### 판단 근거
- 학습/데모 목적이므로 즉시 시작할 수 있는 게 중요
- 차량 마커 이동 + 경로 추적 기능은 Leaflet으로 충분히 구현 가능
- 실제 서비스 수준이 필요해지는 시점에 네이버 지도로 교체 검토

---

## ADR-V007: Location VO 좌표를 BigDecimal(scale=6)로 저장 {#adr-v007}
**날짜**: 2026-04-19

### 문제
- `double`은 부동소수점 오차로 `37.5012`가 `37.50119999...`로 저장될 수 있음
- equals/hashCode 비교에서 예기치 않은 불일치 발생 가능
- DB `DECIMAL(9,6)` 컬럼과의 타입 매핑 시 변환 필요

### 검토한 대안
| 방식 | 크기 | 정밀도 | 산술 연산 | DB 매핑 |
|------|------|--------|----------|---------|
| double | 8 bytes | 부동소수점 오차 | 하드웨어 | 변환 필요 |
| BigDecimal | 중간 | 정확 | 소프트웨어 | DECIMAL 직결 |
| String | 10+ bytes | 원본 보존 | 불가 (변환 필요) | VARCHAR |

### 결정
`BigDecimal(scale=6, HALF_UP)` 사용. Money VO와 동일한 패턴 적용.

### 판단 근거
- **정밀도**: 소수점 6자리 = ~0.11m 정밀도, GPS 오차(3~5m) 대비 충분
- **Money VO와 일관성**: 프로젝트에서 이미 BigDecimal로 정밀 수치를 다루는 패턴이 확립됨
- **DB 매핑**: RDB `DECIMAL(9,6)` 컬럼과 변환 없이 직결
- **동등성 비교**: scale 고정으로 equals/hashCode가 안정적

### GPS 소수점 자릿수별 정밀도 참고
| 소수점 | 정밀도 | 용도 |
|--------|--------|------|
| 4자리 | ~11m | 도시 블록 |
| 5자리 | ~1.1m | 차선 구분 |
| 6자리 | ~0.11m | 건물 내 위치 |

---

## ADR-V008: 위치 데이터 저장소 — 시계열 DB 대신 RDB 사용 {#adr-v008}
**날짜**: 2026-04-19

### 문제
- LocationSnapshot은 본질적으로 시계열 데이터 (1분마다 append-only, 시간 범위 조회)
- TimescaleDB, InfluxDB 등 시계열 DB가 더 적합할 수 있음

### 결정
시계열 DB는 고려하지 않고, 일반 RDB를 사용한다. 데이터 증가 시 날짜 기반 파티셔닝으로 대응.

### 판단 근거
- 프로젝트 목적이 Netty + DDD 검증 — 시계열 DB까지 도입하면 학습 범위가 과도하게 넓어짐
- RDB 파티셔닝으로 충분히 대응 가능 (날짜 기반 range partition)
- 시계열 DB 전환이 필요해지는 시점은 현재 범위가 아님

---

## ADR-V009: 위치 추적을 별도 Bounded Context(tracking)로 분리 {#adr-v009}
**날짜**: 2026-04-19

### 문제
- Trip, LocationSnapshot, Location이 `vehicle/` BC 안에 있었음
- 위치 추적은 차량에만 한정되지 않음 — 킥보드, 드론, 배달원 등 다양한 대상에 적용 가능
- `vehicleId`가 도메인 모델 전체에 하드코딩되어 확장 시 대규모 수정 필요

### 결정
위치 추적 관련 도메인을 `domains/tracking/` BC로 분리한다.

### 구조 변경
| Before (vehicle/) | After (tracking/) |
|---|---|
| `Trip` | `Journey` |
| `LocationSnapshot` | `LocationSnapshot` |
| `Location` (VO) | `Location` (VO) |
| `TripStatusEnum` | `JourneyStatusEnum` |
| `vehicleId` (Long) | `TrackingTarget` (VO: targetId + targetType) |
| `TripRepository` | `JourneyRepository` |

- `Vehicle`은 `vehicle/` BC에 유지
- `TripApplicationService`는 `vehicle/`에서 두 BC를 오케스트레이션
- 의존 방향: `vehicle/ → tracking/` (단방향)

### 판단 근거
- **확장성**: 새로운 추적 대상 추가 시 `TrackingTargetTypeEnum`에 값만 추가하면 됨
- **관심사 분리**: "차량 상태 관리"와 "위치 추적"은 독립적인 도메인
- **LocationSnapshot 단순화**: `vehicleId` 제거 — `journeyId`만으로 Journey를 통해 추적 대상 식별
- Vehicle 인터페이스 추상화(킥보드/오토바이 등)는 실제 요구사항 발생 시 결정 (YAGNI)

---

## ADR-V010: cartracking 전용 독립 Netty 서버 구성 {#adr-v010}
**날짜**: 2026-04-20

### 문제
- 기존 `netty/basic/` 패키지(Member, Fare, Coupon)와 cartracking을 같은 서버에서 라우트만 추가하는 방식 검토
- 두 도메인의 관심사가 달라 같은 AppConfig에 묶이면 결합도가 높아짐

### 검토한 대안
| 방식 | 장점 | 단점 |
|------|------|------|
| 기존 AppConfig에 cartracking 라우트 추가 | 파일 적음 | basic과 cartracking이 한 서버에 얽힘 |
| cartracking 전용 독립 서버 (채택) | 완전 격리, 독립 배포 가능 | 파일 수 증가 |

### 결정
`cartracking/netty/` 패키지 아래 전용 Netty 인프라를 완전히 독립적으로 구성한다.

### 패키지 구조
```
cartracking/
  CarTrackingApplication.java       ← 진입점 (port 8081)
  netty/
    CarTrackingServer.java
    channel/
      CarTrackingChannelInitializer.java
    rest/
      CarTrackingAppConfig.java
      route/    ← RouteRegistry, RouteEntry, RouteMatch, RequestContext, HttpRoutingHandler
      config/   ← VehicleRouteConfig, TripRouteConfig
      controller/ ← VehicleController, TripController
      dto/      ← VehicleRegisterRequest, ScheduleTripRequest, LocationRequest
```

### API 엔드포인트
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/cartracking/vehicles` | 차량 등록 |
| GET  | `/api/cartracking/vehicles` | 전체 조회 |
| GET  | `/api/cartracking/vehicles/{id}` | 단건 조회 |
| POST | `/api/cartracking/trips` | 운행 배차 |
| POST | `/api/cartracking/trips/{vehicleId}/depart` | 출발 |
| POST | `/api/cartracking/trips/{vehicleId}/snapshots` | 위치 기록 |
| POST | `/api/cartracking/trips/{vehicleId}/complete` | 운행 완료 |
| GET  | `/api/cartracking/trips/{journeyId}/route` | 경로 조회 |

### 판단 근거
- cartracking은 independent deployable — basic과 같은 서버에 묶으면 향후 분리 시 비용이 커짐
- basic(ADR-009)에서 정립된 Virtual Thread 오프로드 패턴을 cartracking에도 동일하게 적용
- `CarTrackingAppConfig`에서 `VehicleRepository` 인스턴스를 한 번 생성해 `VehicleRouteConfig`와 `TripRouteConfig`에 공유 주입 — Vehicle 상태와 Trip이 같은 저장소를 봐야 일관성이 유지됨

### 관련 파일
- `cartracking/CarTrackingApplication.java`
- `cartracking/netty/CarTrackingServer.java`
- `cartracking/netty/rest/CarTrackingAppConfig.java`

---

## ADR-V011: VirtualExecutor를 서버 생명주기에서 생성·주입·종료 {#adr-v011}
**날짜**: 2026-04-20

### 문제
basic의 `HttpRoutingHandler`는 `VIRTUAL_EXECUTOR`를 `private static final`로 선언했다.

```java
// 기존 (basic)
private static final ExecutorService VIRTUAL_EXECUTOR =
    Executors.newVirtualThreadPerTaskExecutor();
```

이 방식은 두 가지 문제가 있다:
1. **생명주기 미관리**: 서버가 `shutdownGracefully()`될 때 executor가 종료되지 않음 — in-flight virtual thread가 정리되지 않은 채 JVM 종료
2. **명칭 불명확**: `VIRTUAL_EXECUTOR`가 사실상 blocking 작업(RDBMS 트랜잭션, ConcurrentMap)을 처리함에도 `blockingExecutor`로 이름 붙이면 Virtual Thread임이 감춰짐

### 결정
cartracking에서는 executor를 `CarTrackingServer.start()` 진입 시 생성하고, `CarTrackingChannelInitializer` → `HttpRoutingHandler`로 **생성자 주입**한다. 서버 종료 시 `finally` 블록에서 명시적으로 `shutdown()`한다.

```
CarTrackingServer.start()
  └─ virtualExecutor = newVirtualThreadPerTaskExecutor()
       └─ CarTrackingChannelInitializer(routeRegistry, virtualExecutor)
            └─ HttpRoutingHandler(registry, virtualExecutor)
                 └─ channelRead0() → virtualExecutor.submit(도메인 로직)

finally:
  virtualExecutor.shutdown()       ← 먼저
  workerGroup.shutdownGracefully()
  bossGroup.shutdownGracefully()
```

### 명칭 결정: `virtualExecutor`
- `blockingExecutor`는 "무엇을 처리하는가"에 초점 → Virtual Thread임을 숨김
- `virtualExecutor`는 "어떤 스레드 모델인가"에 초점 → 구현 의도가 즉시 드러남
- RDBMS/ConcurrentMap 블로킹 작업을 Virtual Thread로 처리한다는 사실이 이름에서 명확히 전달됨

### 판단 근거
- **생명주기 보장**: in-flight 요청이 처리 완료된 뒤 서버가 종료됨
- **명시적 DI**: static 전역 상태 제거 → 테스트 시 다른 executor 주입 가능
- **명칭 명확성**: 코드 리뷰 시 "왜 Virtual Thread를 쓰는가"를 이름만 봐도 알 수 있음

### 관련 파일
- `cartracking/netty/CarTrackingServer.java` — executor 생성·종료
- `cartracking/netty/channel/CarTrackingChannelInitializer.java` — 주입 전달
- `cartracking/netty/rest/route/HttpRoutingHandler.java` — 사용 지점

---

## ADR-V012: HttpRoutingHandler에 exceptionCaught 추가 — 파이프라인 예외 처리 {#adr-v012}
**날짜**: 2026-04-20

### 문제
`virtualExecutor.submit()` 내부의 예외는 try-catch로 처리되지만, 파이프라인 상단(codec, aggregator 등)에서 발생하는 예외나 Netty 내부 예외는 `exceptionCaught()`가 없으면 처리되지 않고 채널이 조용히 닫힌다.

### 결정
`HttpRoutingHandler`에 `exceptionCaught()` 오버라이드를 추가한다.

```java
@Override
public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    logger.error("Unhandled exception in pipeline", cause);
    sendJson(ctx, INTERNAL_SERVER_ERROR, Map.of("error", "Internal Server Error"));
}
```

### 예외 처리 계층 정리
| 발생 위치 | 처리 방법 |
|-----------|----------|
| 등록되지 않은 URL | `registry.find()` null → `NOT_FOUND` 즉시 반환 |
| 도메인 로직 `IllegalArgumentException` | `virtualExecutor` try-catch → `BAD_REQUEST` |
| 도메인 로직 `IllegalStateException` | `virtualExecutor` try-catch → `CONFLICT` |
| 도메인 로직 기타 예외 | `virtualExecutor` try-catch → `INTERNAL_SERVER_ERROR` |
| 파이프라인 레벨 예외 | `exceptionCaught()` → 로그 + `INTERNAL_SERVER_ERROR` |

### 판단 근거
- 파이프라인 예외가 처리되지 않으면 클라이언트는 응답 없이 연결이 끊기는 경험을 함
- `exceptionCaught()`에서 채널을 강제로 닫지 않고 JSON 응답 후 `ChannelFutureListener.CLOSE`로 정상 종료

### 관련 파일
- `cartracking/netty/rest/route/HttpRoutingHandler.java`
