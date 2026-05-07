# Vehicle Tracking — Architectural Decision Records

차량 추적 시스템의 주요 의사결정 사항을 기록한 문서.

업데이트 히스토리
- 2026-04-16 생성
- 2026-04-20 ADR-V010 ~ V016 추가
- 2026-04-21 ADR-V017 ~ V019 추가
- 2026-05-01 ADR-V020 ~ V022 추가
- 2026-05-04 ADR-V004 HiveMQ CE로 정정, ADR-V023 ~ V026 추가
- 2026-05-07 ADR-V025 차량 수 10→1000대 변경, ADR-V027 시뮬레이터 count 파라미터 추가, ADR-V028 Log4j2 기반 성능 계측, ADR-V029 HTTP Keep-Alive 병목 확인

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
13. [ADR-V013: 대시보드를 정적 HTML로 제공 — CORS 허용 방식](#adr-v013)
14. [ADR-V014: Virtual Thread에서 응답 시 EventLoop으로 스케줄링](#adr-v014)
15. [ADR-V015: Journey 계산 메서드 이름에서 get 접두어 제거 — Jackson 직렬화 충돌 방지](#adr-v015)
16. [ADR-V016: HttpRoutingHandler 요청/응답 로깅 추가](#adr-v016)
17. [ADR-V017: Trip HTTP API 제거 — 운행 수명주기를 MQTT Subscriber가 자동 처리](#adr-v017)
18. [ADR-V018: 대시보드 Trip 버튼 제거 — GPS 수신 추적만 표시](#adr-v018)
19. [ADR-V019: 운행 이력 조회 API 추가 — Subscriber 검증용 읽기 전용 엔드포인트](#adr-v019)
20. [ADR-V020: WebSocketOrHttpRouter 제거 — 핸들러 순차 배치로 단순화](#adr-v020)
21. [ADR-V021: 회사별 WebSocket 필터링은 보류 — MQTT 성능 측정이 우선](#adr-v021)
22. [ADR-V022: MQTT Subscriber가 첫 GPS 수신 시 Journey를 자동 생성](#adr-v022)
23. [ADR-V023: 대시보드 WebSocket 실시간 추적 + 마커 애니메이션](#adr-v023)
24. [ADR-V024: GpsInterpolator step당 최대 500m 제한](#adr-v024)
25. [ADR-V025: 서버 시작 시 차량 1000대 자동 등록(Seed Data)](#adr-v025)
26. [ADR-V026: 시뮬레이터 종료 시 전체 차량 운행 자동 완료](#adr-v026)
27. [ADR-V027: 시뮬레이터 start에 count 파라미터 추가 — 차량 수 스케일업 부하 테스트 지원](#adr-v027)
28. [ADR-V028: PipelineMetrics 집계 클래스 + Log4j2 — 파이프라인 병목 구간 식별](#adr-v028)
29. [ADR-V029: HTTP Keep-Alive 미지원 병목 확인 — 부하 테스트 S1에서 50% SocketException 발생](#adr-v029)

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

## ADR-V004: MQTT Broker — HiveMQ CE 선택 {#adr-v004}
**날짜**: 2026-04-16 (2026-05-04 정정: Mosquitto → HiveMQ CE)

### 검토 대상
| 항목 | Mosquitto | HiveMQ CE | EMQX | VerneMQ |
|------|-----------|-----------|------|---------|
| 언어 | C | Java | Erlang | Erlang |
| 메모리 | 매우 가벼움 | 보통 (JVM) | 보통 | 보통 |
| 대시보드 | 없음 | 있음 | 있음 | 없음 |
| 학습 자료 | 매우 많음 | 많음 | 많음 | 보통 |

### 결정
HiveMQ CE 선택.

### 판단 근거
- 클라이언트(HiveMQ Client)와 같은 생태계 — 호환성 보장
- 별도 설정 파일 없이 기본값으로 동작 (익명 접속 허용, port 1883)
- Docker 한 줄로 즉시 시작 가능
- 실제 서비스 전환 시 브로커만 EMQX 등으로 교체하면 됨 (클라이언트 코드 변경 없음)

### 운영 방법
```yaml
# docker-compose.yml
services:
  hivemq:
    image: hivemq/hivemq-ce
    ports:
      - "1883:1883"
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

---

## ADR-V013: 대시보드를 정적 HTML로 제공 — CORS 허용 방식 {#adr-v013}
**날짜**: 2026-04-20

### 문제
- 차량 관제 대시보드 UI가 필요한데, Thymeleaf(서버사이드 렌더링)를 도입할지, 정적 HTML을 사용할지 결정 필요
- Thymeleaf는 Spring 없이 standalone으로 사용 가능하지만 의존성과 설정 복잡도가 추가됨

### 검토한 대안
| 방식 | 장점 | 단점 |
|------|------|------|
| Thymeleaf standalone | 서버에서 HTML 렌더링, CORS 불필요 | 의존성 추가, 템플릿 엔진 설정 필요 |
| Netty 정적 파일 서빙 | CORS 불필요, 한 서버로 통합 | StaticFileHandler 구현 필요 |
| 정적 HTML + file:// (채택) | 의존성 없음, 가장 단순 | CORS 설정 필요 |

### 결정
브라우저에서 HTML 파일을 직접 열어(`file://`) API를 호출하는 방식을 사용한다. `HttpRoutingHandler`에 CORS 헤더를 추가하여 cross-origin 요청을 허용한다.

### 구현
```java
// OPTIONS preflight 즉시 응답
if ("OPTIONS".equals(method)) {
    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
    setCorsHeaders(response);
    response.headers().set(CONTENT_LENGTH, 0);
    ctx.writeAndFlush(response).addListener(CLOSE);
    return;
}

// 모든 JSON 응답에 CORS 헤더 추가
private void setCorsHeaders(FullHttpResponse response) {
    response.headers().set("Access-Control-Allow-Origin", "*");
    response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
}
```

### 판단 근거
- **데모/학습 목적** — 프로덕션 수준의 보안(특정 origin 제한)은 현재 불필요
- **최소 복잡도** — Netty에 정적 파일 핸들러를 추가하거나 Thymeleaf 의존성을 도입하는 것보다 CORS 헤더 3줄이 훨씬 단순
- **즉시 시작 가능** — HTML 파일을 더블클릭하면 바로 대시보드 동작
- 실서비스 전환 시 `*` 대신 특정 origin으로 제한하거나 Netty 정적 파일 서빙으로 전환하면 됨

### 관련 파일
- `cartracking/netty/rest/route/HttpRoutingHandler.java`

---

## ADR-V014: Virtual Thread에서 응답 시 EventLoop으로 스케줄링 {#adr-v014}
**날짜**: 2026-04-20

### 문제
`virtualExecutor.submit()` 내부에서 `ctx.writeAndFlush()`를 직접 호출하면, 브라우저에 응답이 전달되지 않는 현상 발생. POST 요청이 빨간색으로 표시되고 response body가 없음.

### 원인 분석
- Netty의 `Channel` I/O 연산은 EventLoop 스레드에서 실행되어야 thread-safety가 보장됨
- Virtual Thread에서 직접 `writeAndFlush()`를 호출하면 EventLoop의 내부 상태와 충돌하여 응답이 유실될 수 있음
- `catch (Exception e) { ctx.close(); }` 에서 로그 없이 연결만 닫아 디버깅도 불가능했음

### 결정
`sendJson()`에서 `ctx.channel().eventLoop().execute()`로 감싸서 응답을 EventLoop에서 실행한다.

```java
ctx.channel().eventLoop().execute(() ->
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
);
```

### 판단 근거
- Netty 공식 권장: I/O 연산은 EventLoop에서 수행
- Virtual Thread는 도메인 로직(블로킹 작업) 전용, 응답 쓰기는 EventLoop으로 돌려보냄
- 직렬화 실패 시에도 로그를 남겨 디버깅 가능하도록 개선

### 관련 파일
- `cartracking/netty/rest/route/HttpRoutingHandler.java`

---

## ADR-V015: Journey 계산 메서드 이름에서 get 접두어 제거 — Jackson 직렬화 충돌 방지 {#adr-v015}
**날짜**: 2026-04-20

### 문제
Jackson이 `Journey` 객체를 직렬화할 때 `getDuration()` 메서드를 `"duration"` 프로퍼티로 인식하여 호출. 운행이 완료되지 않은 상태에서 호출되면 `IllegalStateException`이 발생하여 직렬화 전체가 실패.

```
com.fasterxml.jackson.databind.JsonMappingException:
  완료된 운행만 소요시간을 계산할 수 있습니다.
  (through reference chain: ...Journey["duration"])
```

### 검토한 대안
| 방식 | 장점 | 단점 |
|------|------|------|
| `@JsonIgnore` 어노테이션 | 명시적 | 도메인에 프레임워크 import 금지 (절대 규칙 위반) |
| Jackson Mixin (netty 레이어) | 도메인 오염 없음 | 설정 코드 증가 |
| 응답 DTO 분리 | 가장 정석 | 파일 수 증가, 현재 규모에 과잉 |
| 메서드명에서 `get` 제거 (채택) | 변경 최소, 도메인 오염 없음 | 네이밍 컨벤션 변경 |

### 결정
`getDuration()` → `calculateDuration()`, `getElapsed()` → `calculateElapsed()`로 이름 변경.

### 판단 근거
- `get` 접두어가 없으면 Jackson이 JavaBean 프로퍼티로 인식하지 않음
- 이 메서드들은 실제로 "프로퍼티 접근"이 아닌 "계산 행위"이므로 `calculate` 접두어가 의미적으로도 정확
- 도메인 레이어에 프레임워크 의존성을 추가하지 않고 해결
- 향후 규모가 커지면 응답 DTO 분리를 재검토

### 관련 파일
- `cartracking/tracking/domain/Journey.java`
- `tracking/domain/JourneyTest.java`

---

## ADR-V016: HttpRoutingHandler 요청/응답 로깅 추가 {#adr-v016}
**날짜**: 2026-04-20

### 문제
- API 호출 시 요청/응답 내용을 확인할 수 없어 디버깅이 어려움
- ADR-V014, V015 이슈 발견 시 서버 로그가 없어 원인 파악에 시간 소요

### 결정
`HttpRoutingHandler`에 요청 수신 시점과 응답 송신 시점에 Log4j2 로깅을 추가한다.

### 로그 포맷
```
→ POST /api/cartracking/trips {"vehicleId":1,"originLat":37.56,...}
← POST /api/cartracking/trips 200 {"id":1,"target":{...}}
← POST /api/cartracking/trips 직렬화 실패 (Exception 로그)
```

- `→`: 요청 수신 (method, path, body)
- `←`: 응답 송신 (method, path, status code, response body)

### 판단 근거
- 데모/학습 목적으로 전체 요청·응답 body를 로깅해도 성능 문제 없음
- 프로덕션에서는 body 로깅을 제거하거나 DEBUG 레벨로 낮춰야 함
- Log4j2 비동기 로깅(Disruptor)을 사용하므로 EventLoop 스레드 블로킹 최소화

### 관련 파일
- `cartracking/netty/rest/route/HttpRoutingHandler.java`

---

## ADR-V017: Trip HTTP API 제거 — 운행 수명주기를 MQTT Subscriber가 자동 처리 {#adr-v017}
**날짜**: 2026-04-21

### 문제
`TripRouteConfig`에 운행 배차/출발/위치 기록/완료 REST API가 존재했다.

```
POST /api/cartracking/trips                       ← 운행 배차
POST /api/cartracking/trips/{vehicleId}/depart    ← 출발
POST /api/cartracking/trips/{vehicleId}/snapshots ← 위치 기록
POST /api/cartracking/trips/{vehicleId}/complete  ← 운행 완료
```

실제 세계에서 운행은 차량 단말기(IoT)가 GPS를 전송하기 시작하는 순간 시작되고, GPS가 끊기면 종료된다. 서버가 REST API로 운행을 "만들어내는" 구조는 현실과 반대다.

### 결정
Trip 관련 HTTP API 전체를 삭제한다. 운행 수명주기는 MQTT Subscriber가 자동으로 처리한다.

| 기존 (HTTP API) | 변경 후 (MQTT Subscriber 자동 처리) |
|---|---|
| `POST /trips` 호출 → Journey 생성 | 첫 GPS 수신 → Journey 자동 생성 |
| `POST /trips/{id}/snapshots` 호출 → 위치 기록 | GPS 수신마다 → LocationSnapshot 자동 저장 |
| `POST /trips/{id}/complete` 호출 → 운행 종료 | GPS 끊김 감지 → Journey 자동 종료 |

### 삭제된 파일
- `cartracking/netty/rest/config/TripRouteConfig.java`
- `cartracking/netty/rest/controller/TripController.java`
- `cartracking/netty/rest/dto/ScheduleTripRequest.java`
- `cartracking/netty/rest/dto/LocationRequest.java`

### 유지된 파일
- `cartracking/vehicle/application/TripApplicationService.java` — Subscriber가 호출할 도메인 로직은 유지. `scheduleTrip`/`departTrip` 분리 → `startTrip` 단일 메서드로 단순화.

### 판단 근거
- REST API로 운행을 제어하는 구조는 "서버가 차량을 조종"하는 역전된 모델
- MQTT pub/sub 모델에서 서버는 수신자(Subscriber)여야 하며, 차량이 보내는 이벤트에 반응해야 함
- Subscriber 구현 전 임시로 만든 API였으므로 Subscriber 도입 시점에 제거하는 것이 적절

---

## ADR-V018: 대시보드 Trip 버튼 제거 — GPS 수신 추적만 표시 {#adr-v018}
**날짜**: 2026-04-21

### 문제
`dashboard.html`에 운행 시작/위치 기록/운행 완료/경로 보기 버튼이 있었다. ADR-V017에서 Trip HTTP API를 제거했으므로 이 버튼들은 동작하지 않는다.

### 결정
Trip 관련 버튼 전체를 삭제하고, 대시보드는 GPS 수신 추적 전용으로 단순화한다.

### 변경 전 버튼
- 차량 등록, **운행 시작**, **위치 기록**, **운행 완료**, 새로고침, **경로 보기**

### 변경 후 버튼
- 차량 등록, 시뮬레이터 시작, 새로고침

### 판단 근거
- 대시보드는 "관제(모니터링)" 역할 — 사용자가 운행을 조작하는 인터페이스가 아님
- MQTT Subscriber가 완성되면 GPS 수신 → 지도 마커 자동 갱신으로 추적이 완성됨
- 불필요한 버튼 제거로 대시보드 역할이 명확해짐

---

## ADR-V019: 운행 이력 조회 API 추가 — Subscriber 검증용 읽기 전용 엔드포인트 {#adr-v019}
**날짜**: 2026-04-21

### 문제
Trip HTTP API를 제거한 뒤, MQTT Subscriber가 GPS를 수신하고 Journey/LocationSnapshot을 자동으로 기록했을 때 실제로 데이터가 제대로 쌓이는지 확인할 수단이 없었다.

### 결정
운행 이력을 조회하는 읽기 전용 API를 추가하고 대시보드에 운행 이력 패널을 연결한다.

### 추가된 API
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/cartracking/vehicles/{vehicleId}/journeys` | 차량의 운행 이력 목록 (최신순) |
| GET | `/api/cartracking/journeys/{journeyId}/route` | 특정 운행의 GPS 경로 포인트 목록 |

### 추가된 파일
- `cartracking/netty/rest/controller/JourneyController.java` — 조회 전용 컨트롤러
- `cartracking/netty/rest/config/JourneyRouteConfig.java` — 라우트 등록

### 변경된 파일
- `JourneyRepository` — `findAllByTarget()` 추가
- `InMemoryJourneyRepository` — `findAllByTarget()` 구현 (최신순 정렬)
- `TripApplicationService` — `getVehicleJourneys()` 추가
- `CarTrackingAppConfig` — Repository를 명시적으로 공유 인스턴스로 생성하도록 개선
- `dashboard.html` — 사이드바 하단에 운행 이력 패널 추가, 운행 클릭 시 지도에 경로 표시

### 대시보드 동작
1. 차량 클릭 → 해당 차량의 운행 이력 목록 표시 (상태, 출발/도착 시간)
2. 운행 클릭 → 지도에 보라색 경로선(Polyline) 표시

### 판단 근거
- Subscriber 완성 후 GPS 데이터가 실제로 쌓이는지 화면에서 바로 검증 가능
- "직접 기록"하는 API가 아니라 "자동으로 쌓인 것을 조회"하는 구조 — 역할이 명확
- `CarTrackingAppConfig`에서 Repository 공유 인스턴스를 명시적으로 생성함으로써, 추후 Subscriber도 동일 Repository에 주입받아 데이터 일관성 보장

---

## ADR-V020: WebSocketOrHttpRouter 제거 — 핸들러 순차 배치로 단순화 {#adr-v020}
**날짜**: 2026-05-01

### 문제
같은 포트에서 REST + WebSocket을 처리하기 위해 `WebSocketOrHttpRouter` 내부 클래스를 만들어 첫 HTTP 요청의 URI를 보고 파이프라인을 동적으로 교체하는 방식을 사용했다. `retain()`, `fireChannelRead()`, `p.remove(this)` 등 수동 메시지 재전달 로직이 필요하고 불필요하게 복잡했다.

### 결정
`WebSocketOrHttpRouter` 내부 클래스를 제거하고, 핸들러를 순차적으로 배치한다.

```java
p.addLast(new HttpServerCodec());
p.addLast(new HttpObjectAggregator(65536));
p.addLast(new WebSocketServerProtocolHandler("/ws/vehicles"));
p.addLast(new WebSocketFrameHandler(websocketClients));
p.addLast(new HttpRoutingHandler(routeRegistry, virtualExecutor));
```

### 왜 동작하는가
`WebSocketServerProtocolHandler`는 `/ws/vehicles` 경로의 HTTP Upgrade 요청만 핸드셰이크로 처리하고, 매칭되지 않는 일반 HTTP 요청은 다음 핸들러로 그대로 통과시킨다.

### 제거된 것
- `WebSocketOrHttpRouter` 내부 클래스 전체
- `request.retain()` + `ctx.fireChannelRead(request)` 수동 재전달 로직
- `p.remove(this)` 동적 파이프라인 조작

### 판단 근거
- Netty의 `WebSocketServerProtocolHandler`가 이미 경로 매칭 + 통과 기능을 내장하고 있으므로 별도 라우터가 불필요
- `retain()`/`fireChannelRead()` 패턴은 참조 카운팅 실수 시 메모리 누수 위험이 있으므로 회피하는 것이 안전

### 관련 파일
- `cartracking/netty/channel/CarTrackingChannelInitializer.java`

---

## ADR-V021: 회사별 WebSocket 필터링은 보류 — MQTT 성능 측정이 우선 {#adr-v021}
**날짜**: 2026-05-01

### 검토한 내용
Vehicle에 `companyId`를 추가하고, WebSocket 연결 시 회사별/관리자별 ChannelGroup을 분리하여 telemetry broadcast 대상을 필터링하는 방안을 검토했다.

### 설계 (구현 보류)
```
ws://localhost:8081/ws/vehicles?companyId=acme   → companyChannels["acme"]에 추가
ws://localhost:8081/ws/vehicles?role=admin        → adminChannels에 추가
```

Subscriber에서 GPS 수신 시:
1. 해당 차량의 companyId로 `companyChannels.get(companyId).writeAndFlush(json)` — 회사 브라우저에만 전송
2. `adminChannels.writeAndFlush(json)` — 관리자에게도 전송

메시지 내용은 동일하고, **writeAndFlush 대상만 제어**하는 방식이다.

### 보류 사유
- 현재 프로젝트의 주 목표는 **MQTT 적용 + 성능 측정**이며, 멀티테넌시는 부차적
- Vehicle 도메인에 companyId가 없는 상태에서 도입하면 도메인 모델 전체(Vehicle, VehicleCreate, DTO, Builder, Repository, 테스트)에 변경이 파급됨
- MQTT 성능 측정이 완료된 뒤 필요 시 도입

### 관련 파일
- 변경 없음 (설계만 기록)

---

## ADR-V022: MQTT Subscriber가 첫 GPS 수신 시 Journey를 자동 생성 {#adr-v022}
**날짜**: 2026-05-01

### 문제
Subscriber는 `recordSnapshot()`만 호출하고 있었다. `recordSnapshot()`은 진행 중인 Journey가 있어야 동작하는데, Journey를 생성하는 `startTrip()`을 호출하는 곳이 없었다. 결과적으로 시뮬레이터가 GPS를 publish해도 Journey가 없어서 snapshot이 저장되지 않았다.

### 결정
Subscriber의 callback에서 `recordSnapshot()`이 null을 반환하면(= 진행 중인 Journey가 없으면) `startTrip()`을 자동 호출하여 Journey를 생성한다.

```java
LocationSnapshot snapshot = tripApplicationService.recordSnapshot(vehicleId, location);
if (snapshot == null) {
    tripApplicationService.startTrip(vehicleId, location);
}
```

### 동작 흐름
```
첫 번째 GPS 수신 → recordSnapshot() → Journey 없음(null) → startTrip() → Journey 생성
두 번째 GPS 수신 → recordSnapshot() → Journey 있음 → LocationSnapshot 저장
```

### 판단 근거
- 실제 세계에서 운행은 차량이 GPS를 전송하기 시작하는 순간 시작됨
- 첫 GPS에서는 Journey 생성만 하고 snapshot은 저장하지 않음 — 출발 지점은 Journey의 origin에 이미 저장되므로 데이터 유실 없음

### 관련 파일
- `cartracking/mqtt/VehicleTelemetrySubscriber.java`

---

## ADR-V023: 대시보드 WebSocket 실시간 추적 + 마커 애니메이션 {#adr-v023}
**날짜**: 2026-05-04

### 문제
대시보드가 REST API 수동 새로고침으로만 차량 위치를 표시했다. 마커가 순간이동하여 실시간 추적 느낌이 없었다.

### 결정
WebSocket(`ws://localhost:8081/ws/vehicles`)으로 telemetry를 실시간 수신하고, 마커를 부드럽게 이동시킨다.

### 구현
- **WebSocket 연결**: 페이지 로드 시 자동 연결, 끊기면 3초 후 재연결
- **마커 애니메이션**: `requestAnimationFrame`으로 4초간 선형 보간 이동 (시뮬레이터 5초 간격에 맞춤)
- **점(dot) 궤적**: 5초마다 `circleMarker`로 수신 위치를 점으로 표시, 차량별 색상 구분
- **수신 카운트**: 점 툴팁에 몇 번째 수신인지 표시
- **지역 이동 버튼**: 서울/대전/대구/부산/경북/경남/경기/제주 — `flyTo`로 부드럽게 이동

### 판단 근거
- 서버가 이미 WebSocket broadcast를 구현하고 있었으므로(ADR-V020) 대시보드에서 연결만 하면 됨
- 순간이동 대신 보간 애니메이션으로 차량 이동 경로를 직관적으로 파악 가능
- 점 궤적으로 몇 번째 데이터인지, 어디까지 이동했는지 시각적 확인 가능

### 관련 파일
- `dashboard.html`

---

## ADR-V024: GpsInterpolator step당 최대 500m 제한 {#adr-v024}
**날짜**: 2026-05-04

### 문제
기존 `GpsInterpolator`가 MAX_STEPS=30으로 제한되어, 30km 이상 경로에서 step당 1km 이상 점프가 발생했다. 지도에서 비현실적인 순간이동으로 보였다.

### 결정
MAX_STEPS 상한을 제거하고, step당 최대 0.5km(500m)가 되도록 step 수를 자동 계산한다.

### 변경
```java
// Before
private static final int MAX_STEPS = 30;
int steps = Math.max(MIN_STEPS, Math.min(MAX_STEPS, (int) Math.round(distanceKm)));

// After
private static final double MAX_STEP_KM = 0.5;
int steps = Math.max(MIN_STEPS, (int) Math.ceil(distanceKm / MAX_STEP_KM));
```

### 예시
| 거리 | Before (steps) | After (steps) | step당 이동 |
|------|---------------|---------------|------------|
| 5km  | 5             | 10            | 500m       |
| 20km | 20            | 40            | 500m       |
| 40km | 30 (캡)       | 80            | 500m       |

### 판단 근거
- 5초 간격으로 500m 이동 = 시속 360km → 시뮬레이션 데모 속도로 적절
- 어떤 거리의 경로든 일정한 간격으로 점이 찍혀 지도에서 자연스러움

### 관련 파일
- `cartracking/simulator/GpsInterpolator.java`

---

## ADR-V025: 서버 시작 시 차량 1000대 자동 등록(Seed Data) {#adr-v025}
**날짜**: 2026-05-04 (2026-05-07 변경: 10대 → 1000대)

### 문제
시뮬레이터 테스트를 위해 매번 REST API로 차량을 수동 등록해야 했다.

### 결정
`CarTrackingAppConfig.init()`에서 차량 1000대를 서울 영역 내 랜덤 위치에 자동 등록한다.

### 변경 이력
| 시점 | 차량 수 | 번호판 형식 | 이유 |
|------|--------|-----------|------|
| 2026-05-04 | 10대 | `서울11가1001` ~ `제주10차1010` (하드코딩 배열) | 시뮬레이터 데모용 |
| 2026-05-07 | 1000대 | `TEST-0001` ~ `TEST-1000` (자동 생성) | 부하 테스트 스케일업 (ADR-V027) |

### 구현
- `SEED_VEHICLE_COUNT = 1000` 상수로 차량 수 제어
- 번호판: `String.format("TEST-%04d", i)` — 1부터 순번
- 차종: SEDAN/SUV/VAN 순환
- 출발 위치: `SeoulRouteGenerator.randomLocation()`으로 서울 바운딩 박스 내 랜덤 배치

### 판단 근거
- InMemory 저장소라 서버 재시작 시 데이터가 사라지므로 seed data가 필요
- 부하 테스트 시 차량 10 → 100 → 1000대 스케일업이 핵심 변수 — 1000대를 미리 등록해두고 시뮬레이터 `count` 파라미터로 조절
- 하드코딩 배열 대신 자동 생성으로 변경하여 차량 수 확장이 상수 변경 한 줄로 가능

### 관련 파일
- `cartracking/netty/rest/CarTrackingAppConfig.java`

---

## ADR-V026: 시뮬레이터 종료 시 전체 차량 운행 자동 완료 {#adr-v026}
**날짜**: 2026-05-04

### 문제
시뮬레이터를 멈추면 `VehicleSimulator.stop()`으로 publish만 중단되고, Journey는 IN_PROGRESS 상태로 남아있었다. Vehicle도 ON_TRIP 상태 그대로 유지되어 재시작 시 `startTrip()` 예외 발생.

### 결정
`SimulatorBootstrap.stop()`에서 각 차량의 `completeTrip()`을 호출하여 Journey를 COMPLETED로, Vehicle을 AVAILABLE로 전환한다.

### 변경
```java
public void stop() {
    simulators.forEach(VehicleSimulator::stop);
    virtualExecutor.shutdown();

    for (VehicleSimulator sim : simulators) {
        try {
            tripService.completeTrip(sim.getVehicleId());
        } catch (IllegalStateException e) {
            // Journey가 없는 경우 (첫 GPS 수신 전 종료) 건너뜀
        }
    }
    simulators.clear();
    started = false;
}
```

### 판단 근거
- 시뮬레이터 멈춤 = 운행 종료 — 대시보드에서 COMPLETED 이력을 즉시 조회 가능
- Vehicle 상태가 AVAILABLE로 복귀되어 재시작 시 정상 동작
- Journey가 없는 차량(첫 GPS 수신 전 종료)은 예외를 무시하여 안전하게 처리

### 관련 파일
- `cartracking/simulator/SimulatorBootstrap.java`
- `cartracking/simulator/VehicleSimulator.java` — `getVehicleId()` getter 추가

---

## ADR-V027: 시뮬레이터 start에 count 파라미터 추가 — 차량 수 스케일업 부하 테스트 지원 {#adr-v027}
**날짜**: 2026-05-07

### 문제
부하 테스트의 핵심 변수는 **차량 수(= MQTT 메시지 빈도)**이다. 기존 시뮬레이터는 `vehicleRepository.findAll()`로 등록된 전체 차량을 시뮬레이션했기 때문에, 10대 → 100대 → 1000대 단계별 스케일업이 불가능했다.

### 결정
`SimulatorBootstrap.start(int vehicleCount)` 오버로드를 추가하고, REST API에서 `?count=N` 쿼리 파라미터로 시뮬레이션 차량 수를 제어할 수 있게 한다.

### API 변경
```
# 10대만 시뮬레이션
POST /api/cartracking/simulator/start?count=10

# 100대
POST /api/cartracking/simulator/start?count=100

# 전체 (count 생략 또는 0)
POST /api/cartracking/simulator/start
```

### 응답 변경
```json
{"message": "시뮬레이터가 시작되었습니다.", "vehicleCount": 10}
{"message": "시뮬레이터가 시작되었습니다.", "vehicleCount": "all"}
```

### 구현
- `SimulatorBootstrap.start()` — 기존 동작 유지 (전체 차량)
- `SimulatorBootstrap.start(int vehicleCount)` — `vehicleCount > 0`이면 `findAll()` 결과에서 앞에서 N대만 `subList`로 잘라서 시뮬레이션
- `SimulatorController.start()` — `ctx.queryParam("count")`로 파라미터 읽기

### 부하 테스트 시나리오와의 연관
```
서버 시작 → 1000대 seed 등록 (ADR-V025)
  → start?count=10   → baseline 측정
  → stop → start?count=100  → 스케일업 측정
  → stop → start?count=500  → 병목점 탐색
  → stop → start?count=1000 → 극한 테스트
```

### 판단 근거
- 차량을 매번 등록/삭제하지 않고 시뮬레이터 파라미터만으로 스케일업 가능
- 기존 `start()` 호출부(테스트, 대시보드)는 변경 없이 호환
- `subList`로 앞에서 N대를 자르므로 같은 차량 세트로 반복 테스트 가능 (재현성)

### 관련 파일
- `cartracking/simulator/SimulatorBootstrap.java`
- `cartracking/netty/rest/controller/SimulatorController.java`

---

## ADR-V028: PipelineMetrics 집계 클래스 + Log4j2 — 파이프라인 병목 구간 식별 {#adr-v028}
**날짜**: 2026-05-07

### 문제
JMeter + VisualVM으로 **"느려졌다"는 사실**은 알 수 있지만, 서버 내부에서 **어느 구간이 병목인지**는 알 수 없다.

예: "S4(500대)에서 REST TPS가 급락" → 원인이 직렬화인지, 도메인 로직인지, EventLoop 경합인지 구분 불가

### 검토 과정 (3단계 진화)

#### 1차 검토: `System.nanoTime()` + 매 요청 로그

```java
long t0 = System.nanoTime();
// ... 역직렬화 ...
long t1 = System.nanoTime();
// ... 도메인 로직 ...
long t2 = System.nanoTime();
logger.info("[PERF] deserialize={}μs domain={}μs", (t1-t0)/1000, (t2-t1)/1000);
```

**기각 사유:**
- 비즈니스 코드에 `t0, t1, t2, t3, t4` 변수가 흩어져서 코드 오염이 심함
- 1000대 × 초당 200건 = **초당 200줄 로그** → 로그 I/O 자체가 병목이 됨
- 개별 요청 시간만 찍히고 **평균/p99 같은 통계가 없음** → 결국 후처리 필요

#### 2차 검토: Log4j2 타임스탬프만으로 구간 측정

```
10:00:00.000100 MQTT_RECEIVE
10:00:00.000145 MQTT_DESERIALIZE_DONE vid=5    ← 45μs
10:00:00.000480 MQTT_DOMAIN_DONE vid=5         ← 335μs
```

`nanoTime()` 없이 Log4j2의 마이크로초 타임스탬프 차이로 구간 소요시간을 계산하는 방식.

**기각 사유:**
- 1차와 마찬가지로 **초당 수백~수천 줄 로그가 쌓임**
- 통계가 자동으로 나오지 않음 → **후처리 스크립트**(Python 등)로 로그 파일을 파싱해서 avg/p99를 계산해야 함
- 부하 테스트 중 실시간으로 병목을 확인할 수 없음 (테스트 끝나고 사후 분석만 가능)
- 차량이 많아지면 여러 차량의 로그가 뒤섞여서 파싱 로직이 복잡해짐

#### 3차 결정 (채택): PipelineMetrics 집계 클래스 + Log4j2 요약 출력

```
[MQTT] 10s count=2000 | DESERIALIZE: avg=45μs p99=120μs max=250μs | DOMAIN: avg=310μs p99=800μs max=1500μs | ...
[REST] 10s count=500  | VT_WAIT: avg=80μs p99=300μs max=800μs | HANDLER: avg=850μs p99=2500μs max=5000μs | ...
```

메모리에서 집계하고 10초마다 요약 1줄만 Log4j2로 출력.

### 비교표

| 기준 | 1차 (nanoTime+매건로그) | 2차 (Log4j2 타임스탬프) | 3차 (집계 클래스, 채택) |
|------|----------------------|----------------------|---------------------|
| 로그 양 | 초당 수백 줄 | 초당 수천 줄 | **10초에 2줄** |
| 통계 확인 | 없음 | 후처리 스크립트 필요 | **로그에 바로 보임** |
| 실시간 확인 | 가능 (개별 건) | 불가 (사후 분석) | **가능 (10초마다 갱신)** |
| 코드 침습 | t0,t1,t2 변수 난무 | perf.debug() 5줄 | **trace.mark() 3~4줄** |
| 추가 코드 | 없음 | 후처리 스크립트 | 집계 클래스 2개 |
| 로그 I/O 영향 | 심각 | 심각 | **거의 없음** |
| 차량 뒤섞임 | 문제 없음 (1건=1줄) | 파싱 복잡 | **문제 없음 (집계됨)** |

### 이 방식이 괜찮은 이유

#### 1. nanoTime 오버헤드가 무시할 수 있는 수준이다

`System.nanoTime()` 호출 1회 = ~25ns (Windows 기준).
한 MQTT 메시지당 `mark()` 3회 + `end()` 1회 = nanoTime 5회 호출 = ~125ns.
도메인 로직이 수백 μs(= 수십만 ns)인 상황에서 125ns는 **0.05% 미만** — 측정이 측정 대상에 영향을 주지 않는다.

#### 2. 메모리 사용량이 제한적이다

10초간 1000대 × 2 msg/s = 2000건의 MQTT trace.
각 trace에서 phase 5개 × Long(8바이트) = 40바이트/trace.
총: 2000 × 40 = **80KB** — 10초마다 drain되므로 최대 80KB만 유지.

#### 3. ConcurrentLinkedQueue가 lock-free이다

`PipelineMetrics.record()`는 MQTT callback 스레드와 Virtual Thread에서 동시에 호출된다.
`ConcurrentLinkedQueue.add()`는 CAS(Compare-And-Swap) 기반 lock-free 연산이므로
EventLoop/callback 스레드를 블로킹하지 않는다.

#### 4. 집계(sort + 통계)는 별도 데몬 스레드에서 수행한다

`ScheduledExecutorService`의 데몬 스레드가 10초마다 `drain() → sort() → calcStats()` 수행.
이 계산이 EventLoop이나 MQTT callback 스레드에서 일어나지 않으므로 파이프라인 성능에 영향 없음.

#### 5. perf Logger가 OFF이면 집계 자체를 건너뛴다

```java
private static void report() {
    if (!perf.isDebugEnabled()) return;  // ← OFF이면 즉시 반환
    // drain, sort, 계산 모두 스킵
}
```

`PipelineTrace.mark()`의 nanoTime 호출은 여전히 실행되지만 (레벨과 무관),
이는 앞서 설명한 대로 ~25ns/회로 무시할 수 있는 수준이다.
집계의 무거운 부분(sort, 통계 계산, 로그 출력)은 모두 건너뛴다.

### 결정
`PipelineTrace`로 구간별 소요시간을 `nanoTime()`으로 측정하고,
`PipelineMetrics`가 메모리에서 집계하여 10초마다 avg/p99/max 요약을 Log4j2 perf logger로 출력한다.

### 계측 대상 파이프라인

#### MQTT 파이프라인 (VehicleTelemetrySubscriber)
```
start("MQTT")
  → mark("DESERIALIZE")   역직렬화 (byte[] → TelemetryPayload)
  → mark("DOMAIN")        도메인 로직 (recordSnapshot / startTrip)
  → mark("SERIALIZE")     재직렬화 (TelemetryPayload → JSON String)
  → end("BROADCAST")      WebSocket broadcast (ChannelGroup.writeAndFlush)
  → [자동: TOTAL]         전체 소요시간
```

#### REST 파이프라인 (HttpRoutingHandler)
```
start("REST")
  → mark("VT_WAIT")       EventLoop → Virtual Thread 전환 대기
  → mark("HANDLER")       도메인 로직 (Controller → ApplicationService)
  → end("SERIALIZE")      JSON 직렬화 (ObjectMapper.writeValueAsString)
  → [자동: TOTAL]         전체 소요시간
```

### 구현

#### PipelineTrace — 요청 1건의 구간별 소요시간 기록
```java
PipelineTrace trace = PipelineMetrics.start("MQTT");
// ... 역직렬화 ...
trace.mark("DESERIALIZE");   // 이전 mark부터 여기까지의 nanoTime 차이를 기록
// ... 도메인 로직 ...
trace.mark("DOMAIN");
// ... 재직렬화 ...
trace.mark("SERIALIZE");
// ... broadcast ...
trace.end("BROADCAST");      // 마지막 구간 기록 + TOTAL 계산 + PipelineMetrics에 제출
```

#### PipelineMetrics — 메모리 집계 + 주기적 요약 출력
- `ConcurrentLinkedQueue<Long>`: lock-free로 nanoTime 차이값을 수집
- `ScheduledExecutorService`: 데몬 스레드가 10초마다 drain → sort → avg/p99/max 계산
- `perf` Logger: `logger.perf.level = off`이면 report() 자체를 건너뜀

#### log4j2.properties — perf Logger 설정
```properties
# perf 전용 파일 Appender
appender.perf.name = perfLogger
appender.perf.fileName = ${basePath}/${projectName}_perf.log
appender.perf.layout.pattern = %d{HH:mm:ss.SSSSSS} [%t] %msg%n

# perf Logger — off by default, 부하 테스트 시 debug로 변경
logger.perf.name = perf
logger.perf.level = off
logger.perf.additivity = false
logger.perf.appenderRef.perf.ref = perfLogger
```

### 출력 예시
```
14:23:10.000000 [perf-reporter] [MQTT] 10s count=2000 | DESERIALIZE: avg=45μs p99=120μs max=250μs | DOMAIN: avg=310μs p99=800μs max=1500μs | SERIALIZE: avg=28μs p99=85μs max=150μs | BROADCAST: avg=65μs p99=350μs max=900μs | TOTAL: avg=448μs p99=1200μs max=2500μs
14:23:10.000000 [perf-reporter] [REST] 10s count=500 | VT_WAIT: avg=80μs p99=300μs max=800μs | HANDLER: avg=850μs p99=2500μs max=5000μs | SERIALIZE: avg=120μs p99=400μs max=1200μs | TOTAL: avg=1050μs p99=3200μs max=6500μs
```

### 병목 판별 기준

| 구간 | 정상 | 의심 | 병목 확정 |
|------|------|------|----------|
| DESERIALIZE | < 100μs | 100~500μs | > 500μs |
| DOMAIN | < 500μs | 500μs~2ms | > 2ms |
| SERIALIZE | < 100μs | 100~500μs | > 500μs |
| BROADCAST | < 200μs | 200μs~1ms | > 1ms |
| VT_WAIT | < 100μs | 100μs~1ms | > 1ms (VT 포화) |

### 관련 파일
- `cartracking/netty/perf/PipelineMetrics.java` — 집계 + 주기적 리포트
- `cartracking/netty/perf/PipelineTrace.java` — 요청 1건의 구간 기록
- `src/main/resources/log4j2.properties` — perf Appender/Logger
- `cartracking/mqtt/VehicleTelemetrySubscriber.java` — MQTT 파이프라인 계측
- `cartracking/netty/rest/route/HttpRoutingHandler.java` — REST 파이프라인 계측

---

## ADR-V029: HTTP Keep-Alive 미지원 병목 확인 — 부하 테스트 S1에서 50% SocketException 발생 {#adr-v029}
**날짜**: 2026-05-07

### 문제
Phase 1 부하 테스트 S1(차량 10대, REST 10 threads × 100 loops)을 JMeter로 실행한 결과, **약 50%의 요청이 SocketException으로 실패**했다.

```
# JMeter .jtl 결과 패턴 (200 OK → 실패 → 200 OK → 실패 반복)
200,OK
0,SocketException: 현재 연결은 사용자의 호스트 시스템의 소프트웨어에 의해 중단되었습니다
200,OK
0,SocketException: 현재 연결은 사용자의 호스트 시스템의 소프트웨어에 의해 중단되었습니다
```

### 원인 분석

서버와 클라이언트의 **Keep-Alive 설정 불일치**가 원인이었다.

| 측 | 설정 | 동작 |
|---|------|------|
| **Netty 서버** | `ChannelFutureListener.CLOSE` | 응답 전송 후 **즉시 TCP 연결 종료** |
| **JMeter** | `use_keepalive=true` (기본값) | 같은 TCP 연결을 **재사용**하려고 시도 |

```
JMeter 요청1 → [TCP 연결] → Netty 응답1 → Netty: 연결 닫기(FIN)
JMeter 요청2 → [같은 연결 재사용 시도] → 이미 닫힌 연결 → SocketException
JMeter 요청3 → [새 TCP 연결] → Netty 응답3 → Netty: 연결 닫기(FIN)
JMeter 요청4 → [같은 연결 재사용 시도] → SocketException
...
```

이것이 정확히 200 → 실패 → 200 → 실패 패턴이 반복된 이유다.

### HTTP Keep-Alive란

- **Keep-Alive OFF**: 매 요청마다 TCP 3-way handshake(SYN→SYN-ACK→ACK) 후 연결, 응답 후 즉시 종료
- **Keep-Alive ON**: 하나의 TCP 연결에서 여러 요청/응답을 처리 — 연결 생성/종료 비용 절감

### 긴급 조치 (JMeter 측)

JMeter `.jmx` 파일에서 `use_keepalive`를 `false`로 변경하여, JMeter도 매 요청마다 새 연결을 사용하도록 맞춤.

```xml
<!-- Before -->
<boolProp name="HTTPSampler.use_keepalive">true</boolProp>

<!-- After -->
<boolProp name="HTTPSampler.use_keepalive">false</boolProp>
```

이 조치로 SocketException은 해소되지만, **매 요청마다 TCP 연결을 새로 맺는 오버헤드**가 남아있다.

### 근본 해결 (Phase 3 개선 1 — 미적용)

서버 측에서 HTTP Keep-Alive를 지원하도록 `HttpRoutingHandler.sendJson()`을 수정해야 한다.

```java
// 현재 (매번 닫기)
ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

// 개선안 (클라이언트가 keep-alive 요청 시 연결 유지)
boolean keepAlive = HttpUtil.isKeepAlive(request);
if (keepAlive) {
    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
    ctx.writeAndFlush(response);
} else {
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
}
```

- **예상 효과**: TPS 2~5배 향상 (TCP 핸드셰이크 제거)
- **구현 시 주의**: idle timeout 설정 필요 (연결이 무한히 열려있지 않도록)
- **상태**: Phase 3 개선 목록에 등록됨, Phase 1 완료 후 진행 예정

### 판단 근거
- 이 병목은 todo-20260505의 "예상 병목 지점 (REST) #1"에 이미 예측되어 있었으며, S1 테스트에서 **첫 번째로 확인된 실제 병목**이다
- 긴급 조치(JMeter Keep-Alive OFF)로 테스트 진행을 우선하고, 서버 개선은 Phase 3에서 Before/After 비교를 위해 분리

### 관련 파일
- `cartracking/netty/rest/route/HttpRoutingHandler.java:125-127` — `ChannelFutureListener.CLOSE`
- `D:/apache-jmeter-5.6.3/result_cartracking/cartracking_test.jmx` — `use_keepalive` 설정
