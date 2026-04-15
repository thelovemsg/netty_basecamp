# 이직 준비 학습 계획: 로봇/자율주행/모빌리티 백엔드

## 목표
- **2026년 하반기** 로봇/자율주행/모빌리티 회사 백엔드 포지션 이직
- 이직 후 도메인 경험 쌓고 → 2029~2030년 대학원 진학 연결

---

## 이 분야 백엔드가 하는 일
- 차량/로봇에서 오는 **대량 실시간 데이터 수집 및 처리**
- 원격 모니터링/제어를 위한 **저지연 통신 서버**
- 지도, 센서, 로그 등 **대용량 데이터 파이프라인**
- 시뮬레이션/테스트 인프라 관리

---

## 이미 가진 역량과 연결 포인트

| 이미 있는 역량 | 이 분야에서의 연결 포인트 |
|---|---|
| 대용량 트래픽 처리 | 차량 수천 대의 실시간 데이터 |
| Netty | 저지연 비동기 통신 서버 |
| JMeter 성능 튜닝 | 실시간 시스템 성능 검증 |
| AWS | 클라우드 인프라 운영 |
| Java / Spring Boot | 서버 애플리케이션 |

---

## 추가 학습 항목

### 필수 (면접에서 차이를 만드는 것)
1. **MQTT** - IoT/로봇/자율주행의 표준 프로토콜. 디바이스 ↔ 서버 통신의 핵심
2. **실시간 통신** - WebSocket, gRPC (차량-서버 간 양방향 통신)
3. **시계열 데이터베이스** - InfluxDB, TimescaleDB (센서/GPS 데이터 저장)

### 기반 역량 강화 (Netty 심화를 위한 선행 학습)
4. **LMAX Disruptor** - Ring Buffer, CPU Cache 친화적 자료구조. Log4j2 Async Logger의 핵심
5. **운영체제 기초** - 컨텍스트 스위칭, 커널/유저 공간, Zero-Copy, False Sharing
6. **DB 설계 기본** - 정규화, 키 설계, 인덱스 (독학 + 블로그 정리)

### 있으면 좋은 것
- Docker / Kubernetes 운영 경험
- ROS2 기본 개념 (깊이 X, "이게 뭔지" 수준)
- 지도/위치 데이터 처리 (PostGIS 등)
- Kafka (서버 간 데이터 파이프라인)

### 왜 C/C++이 아닌 Java(Netty)인가?
- 본인이 가려는 포지션은 **클라우드 서버 쪽** (차량 내부 펌웨어 X)
- HiveMQ (Java/Netty)가 수백만 동시 접속 처리 → 성능 충분
- C로 만든 Mosquitto는 단일 스레드, 대규모 서버용 아님
- 멀티스레딩, 클러스터링, 스케일아웃은 Java/Netty가 유리
- C/C++은 대학원 가서 로봇 펌웨어 할 때 배워도 늦지 않음

---

## 사이드 프로젝트: 가상 차량 실시간 위치 모니터링 시스템

### 아키텍처
```
[가상 차량 100대]  →  MQTT Broker  →  Backend Server  →  Dashboard
 (GPS 데이터 발행)    (Mosquitto)     (Spring Boot)      (WebSocket)
                                          ↓
                                     TimescaleDB
                                    (시계열 저장)
```

### 이 프로젝트로 보여줄 수 있는 것
- MQTT 프로토콜 이해 및 활용
- 대량 실시간 데이터 처리 능력
- 시계열 DB 활용 경험
- WebSocket 실시간 시각화
- Netty / 성능 튜닝 역량 (기존 강점 연결)

### 차별화 포인트
- 차량 1,000대 vs 10,000대 **JMeter 부하 테스트 → 병목 분석**
- **Netty 기반 MQTT 브로커 직접 구현** (이건 하는 사람이 거의 없음)
- 성능 튜닝 과정을 **블로그에 숫자와 함께 기록**

---

## Netty 기반 MQTT 브로커 직접 구현

### 왜 Netty + MQTT인가?
- 실제 상용 MQTT 브로커들이 Netty 기반 (HiveMQ, Moquette 등)
- MQTT는 TCP 위의 프로토콜 → Netty는 TCP 서버 프레임워크 → 자연스러운 조합
- Netty에 `MqttDecoder`, `MqttEncoder`가 이미 내장되어 있음

### 브로커가 하는 일 (결국 이것뿐)
```
발행자(Publisher)  →  [브로커]  →  구독자(Subscriber)

1. 클라이언트 연결 받기        (TCP 서버 = Netty가 해줌)
2. MQTT 패킷 파싱하기         (Netty 디코더가 해줌)
3. 토픽에 맞는 구독자 찾기     (이것만 직접 구현)
4. 메시지 전달하기             (Netty 인코더가 해줌)
```

### 최소 동작 브로커 구조
```java
public class MqttBrokerServer {
    ServerBootstrap b = new ServerBootstrap();
    b.childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline()
              .addLast(new MqttDecoder())         // Netty 내장
              .addLast(MqttEncoder.INSTANCE)       // Netty 내장
              .addLast(new MqttConnectHandler())   // 직접 구현
              .addLast(new MqttPublishHandler())   // 직접 구현
              .addLast(new MqttSubscribeHandler()) // 직접 구현
        }
    });
}
```

### 직접 구현할 핸들러 3개
```
MqttConnectHandler:
  → 클라이언트 접속 시 CONNACK 응답
  → 클라이언트 ID를 Map에 저장

MqttSubscribeHandler:
  → "나는 vehicle/gps 토픽 구독할래" 요청 처리
  → Map<토픽, List<구독자>>에 등록

MqttPublishHandler:
  → "vehicle/gps 토픽에 메시지 보낸다" 처리
  → Map에서 해당 토픽 구독자 찾아서 전달
```

### 핵심 자료구조: 딱 하나
```java
// 브로커의 심장
Map<String, Set<Channel>> subscriptions = new ConcurrentHashMap<>();

// 구독 등록
subscriptions.computeIfAbsent("vehicle/gps", k -> new HashSet<>()).add(channel);

// 메시지 전달
Set<Channel> subscribers = subscriptions.get("vehicle/gps");
for (Channel ch : subscribers) {
    ch.writeAndFlush(publishMessage);
}
```

### MQTT 패킷 구조
```
[Fixed Header] [Variable Header] [Payload]
    1byte+         가변             가변
```

### Netty 처리 흐름
```
TCP 연결 관리 → 바이트 수신 → MQTT 패킷 디코딩 → 비즈니스 로직 → 응답 인코딩 → 전송
```
→ Netty의 Pipeline + Handler 구조가 MQTT 패킷 파싱에 딱 맞음

### 브로커 발전 로드맵

| 버전 | 내용 | 난이도 | 비고 |
|---|---|---|---|
| v0.1 | Map 하나로 동작하는 최소 브로커 | 낮음 | 하루면 됨 |
| v0.2 | QoS 0, 1 지원, PUBACK 처리 | 중 | 포트폴리오 가치 충분 |
| v0.3 | 와일드카드 토픽 매칭 (vehicle/+/gps) | 중상 | + / # 패턴 매칭 |
| v0.4 | JMeter 부하 테스트 → 병목 찾기 | 중 | 여기서 성능 글감 나옴 |
| v0.5 | Trie 구조로 토픽 매칭 최적화 | 중상 | 개선 결과 비교 |
| v1.0 | Mosquitto와 성능 비교 | 중 | **킬러 콘텐츠** |

### 차별화 근거
- 대부분의 개발자: Mosquitto **갖다 쓴다** → "MQTT 써봤습니다"
- 본인: Netty로 **직접 만든다** → "MQTT 프로토콜과 네트워크 I/O를 이해합니다"
- 이미 Netty를 공부하고 블로그에 쓰고 있으므로 자연스러운 연장선

### 참고 오픈소스
- **Moquette** (github) - Java/Netty 기반 경량 MQTT 브로커. 코드 참고용으로 최적

---

## 초고속 메시지 처리와 성능 최적화

### 패킷 직접 파싱이 필요한가?
- **처음에는 NO.** Netty 내장 `MqttDecoder` / `MqttEncoder`로 충분
- 패킷 구조를 "이해"하는 건 중요하지만, 직접 파싱 코드를 짜는 건 나중 문제
- 극한 최적화(마이크로초 단위, 커스텀 프로토콜) 할 때만 ByteBuf 레벨로 내려감

### 단계별 접근
| 단계 | 접근 | 시점 |
|---|---|---|
| 처음 | Netty 내장 `MqttDecoder/Encoder` 사용 | **이것부터** |
| 성능 튜닝 | Thread 모델, 메모리, I/O 최적화 | **여기가 핵심** |
| 극한 최적화 | 패킷 직접 파싱 (ByteBuf 레벨) | 필요할 때만 |

### Netty에서 병목이 생기는 대표적인 경우

1. **EventLoop 블로킹**
   - 핸들러 안에서 DB 조회, 파일 I/O 등 블로킹 작업 수행
   - EventLoop가 멈추면 해당 스레드에 붙은 수천 개 연결이 전부 멈춤

2. **메모리 누수**
   - ByteBuf를 release 안 하면 Direct Memory가 차올라서 OOM
   - `ReferenceCountUtil.release()` 누락

3. **Topic 매칭 비효율**
   - 구독자 10만 명에게 메시지 뿌릴 때 O(N) 순회
   - Trie 구조로 최적화 안 하면 여기서 병목

4. **write() 남발**
   - 메시지마다 `write()` + `flush()` 하면 시스템콜 폭증
   - 배치로 모아서 flush 해야 함

### 병목 찾는 도구들

| 도구 | 용도 | 난이도 |
|---|---|---|
| **JMeter + MQTT 플러그인** | 동시 접속 N대 시뮬레이션, 처리량/지연 측정 | 낮음 |
| **Netty 내장 LoggingHandler** | 파이프라인에 추가하면 패킷 흐름 확인 | 낮음 |
| **VisualVM / JConsole** | 스레드 상태, 힙 메모리, GC 모니터링 | 중 |
| **Async Profiler** | CPU 핫스팟, 락 경합, 메모리 할당 추적 (Flame Graph) | 중 |
| **Netty ByteBufAllocatorMetric** | Direct/Heap 메모리 할당 추적 | 중 |
| **Prometheus + Grafana** | 실시간 메트릭 대시보드 | 중상 |

### 실전 병목 찾기 시나리오
```
Step 1: JMeter로 차량 1,000대 → 10,000대 점진적 부하
        → 처리량(TPS)과 응답시간 그래프 확인
        → "어디서부터 성능이 꺾이는가?"

Step 2: 꺾이는 지점에서 Async Profiler 돌리기
        → CPU를 누가 많이 쓰는지 Flame Graph로 확인
        → "EventLoop가 블로킹되고 있나? Topic 매칭이 느린가?"

Step 3: VisualVM으로 메모리 확인
        → ByteBuf 누수는 없는가? GC가 자주 도는가?

Step 4: 원인 파악 후 개선 → 다시 부하 테스트 → 비교
```

### JSON vs ProtoBuf (Payload 직렬화)

ProtoBuf는 브로커가 아니라 **Payload를 어떻게 담느냐**의 문제.
브로커는 Payload 안을 열어보지 않고 중계만 함.

```
[차량]                    [브로커]              [백엔드 서버]
ProtoBuf로 직렬화 →  그냥 전달만 함  → ProtoBuf 역직렬화
```

| | JSON | ProtoBuf |
|---|---|---|
| 크기 | 큼 | **2~10배 작음** |
| 파싱 속도 | 느림 | **빠름** |
| 사람이 읽기 | 쉬움 | 불가 (바이너리) |
| 디버깅 | 쉬움 | 어려움 |

```
차량 10,000대 × 초당 1회:
  JSON:     ~700 KB/s
  ProtoBuf: ~200 KB/s  ← 네트워크 부하 3.5배 차이
```

**전략: JSON으로 먼저 구현 → ProtoBuf로 교체 → 성능 비교 블로그 글감**

---

## 메시징 도구 성능 비교

### 비교 대상 (4개)

| 도구 | 특징 | 비교 가치 |
|---|---|---|
| **MQTT (Mosquitto)** | IoT 표준, 경량 | 기본 베이스라인 |
| **Kafka** | 대용량 스트리밍, 디스크 기반 | MQTT와 역할이 다름을 보여줌 |
| **RabbitMQ** | 범용 메시지 큐, AMQP | 전통적 비교 대상 |
| **Redis Pub/Sub** | 초경량, 인메모리 | 가장 빠르지만 유실 가능 |

> 4개면 성격이 다 달라서 비교 자체가 의미 있다.
> (NATS, ZeroMQ, Pulsar 등은 너무 니치하거나 범위가 넓어짐)

### 비교 시나리오
```
동일 조건: "차량 10,000대가 초당 1회 GPS 데이터 전송"

측정 항목:
1. 처리량 (TPS) - 초당 몇 건 처리?
2. 지연 시간 (Latency) - 보내고 받기까지 몇 ms?
3. 메모리 사용량 - 동시 접속 10,000개 유지 시
4. 메시지 유실률 - 부하 상황에서 유실이 있는가?
5. 스케일아웃 용이성 - 브로커 늘리면 선형 증가하는가?
```

### 블로그 시리즈 (성능 비교)
```
#1 "차량 1만 대 시뮬레이션 - 테스트 환경 구성"
#2 "MQTT vs Kafka - 같은 시나리오, 다른 결과"
#3 "RabbitMQ vs Redis Pub/Sub - 메시지 큐의 두 얼굴"
#4 "최종 비교표 + 어떤 상황에서 뭘 써야 하는가"
#5 "Netty 자체 구현 브로커 vs Mosquitto - 내 브로커는 얼마나 빠른가"
```
> **#5가 킬러 콘텐츠.** 직접 만든 브로커와 기존 솔루션을 비교하는 글은 거의 없다.

### 블로그 시리즈 (Netty 브로커 성능 튜닝)
```
#1 "Netty MQTT 브로커, 1,000대는 거뜬했다"
#2 "10,000대 붙이니 TPS가 반토막 - 원인 분석"
#3 "Async Profiler로 찾은 범인: Topic 매칭 O(N)"
#4 "Trie 구조로 바꾸고 3배 개선된 결과"
```
> **숫자로 증명하는 성능 튜닝 글은 면접에서 가장 강력한 무기가 된다.**

---

## 월별 로드맵 (2026)

### 4월: 기초 다지기 + 워밍업
- **LMAX Disruptor + 운영체제 기초** (1~2주)
  - Ring Buffer, CPU Cache Line, False Sharing 개념
  - Log4j2 Async Logger가 빠른 이유
  - 컨텍스트 스위칭, 커널/유저 공간, Zero-Copy
  - → Netty 성능의 기반을 이해하는 워밍업
  - → **블로그 정리** (Netty 시리즈 심화 편)
- **MQTT 감 잡기 (Phase 0)** (1주)
  - Mosquitto 설치 → pub/sub 해보기
  - MQTT 기본 개념: QoS, Topic, Retain, Will
  - CONNECT/PUBLISH/SUBSCRIBE 패킷 구조 읽기
- **SQLD 교재 훑기** (틈틈이)
- 토익 시험 (4/12, 별도 준비 없이 응시)

### 5월: 브로커 시작
- **DB 설계 기본 독학** (정규화, 키 설계 — 블로그 정리로 대체)
- **Netty 브로커 만들기 시작 (Phase 1)**
  - Netty TCP 서버 띄우고 MQTT 클라이언트 연결
  - CONNECT → CONNACK 응답 구현

### 6~7월: 브로커 완성 + 모니터링 시스템
- **Netty 브로커 (Phase 1 계속)**
  - SUBSCRIBE + PUBLISH 구현 → "메시지가 전달된다!"
  - QoS 0/1 지원
  - Spring Boot + MQTT 연동 (모니터링 시스템)
  - 가상 차량 데이터 생성기 만들기
  - 시계열 DB(TimescaleDB) + 대시보드 붙이기
- **블로그**: 구현 과정 기록

### 8~9월: 성능 튜닝 + 블로그 킬러 콘텐츠
- **성능 테스트 + 튜닝 (Phase 2)**
  - JMeter 부하 테스트: 1,000대 → 10,000대 점진적 부하
  - Async Profiler / VisualVM 병목 분석
  - 토픽 매칭 최적화 (Trie 구조)
  - JSON → ProtoBuf 전환 후 성능 비교
- **블로그 시리즈** (숫자 기반 성능 튜닝 글 — 면접 최강 무기)
- **코딩테스트 준비 시작** (프로그래머스 Lv2~3)

### 10월~: 이직 활동
- 이력서/포트폴리오 완성
- 경력기술서 그루핀 업데이트
- 코딩테스트 집중 준비
- 타겟 회사 지원 시작

### 여유 있을 때 (선택): 메시징 도구 비교 + 완성도 (Phase 3)
- Kafka, RabbitMQ, Redis Pub/Sub 각각 같은 시나리오 테스트
- 자체 브로커 vs Mosquitto 성능 비교
- gRPC 또는 WebSocket 실시간 대시보드 고도화
- Docker/K8s로 배포 환경 구성
- GitHub README + 블로그 시리즈 정리

### AI(Claude) 활용 방식
| 역할 | 본인 | AI |
|---|---|---|
| 설계 방향 결정 | O | 제안 |
| 핵심 로직 직접 작성 | O (먼저 시도) | 리뷰/검증 |
| 모르는 개념 질문 | 질문 | 답변 |
| 보일러플레이트 코드 | 검토 | 작성 |
| 테스트/설정 파일 | 검토 | 작성 |
| 버그 디버깅 | 같이 | 같이 |
| 블로그 글 | 직접 작성 | 첨삭/검토 |

> **"AI가 짠 코드"가 아니라 "AI를 도구로 써서 내가 만든 프로젝트"가 되어야 한다.**
> 막힐 때마다 물어보면 됨. AI를 옆에 앉은 시니어 개발자처럼 쓰기.

---

## 자격증 / 병행 준비

| 항목 | 상태 | 시기 |
|---|---|---|
| ✅ AWS SAA-C03 | 취득 완료 | 2026-03-21 |
| ✅ 정보처리기사 | 취득 완료 | 2021-11 |
| 토익 | 응시 예정 (별도 준비 X) | 2026-04-12 |
| ~~SQLD~~ | 미응시 (유효기간 2년, 가성비 낮음) | — |
| LMAX + OS 기초 | 학습 예정 | 2026-04 |
| 코딩테스트 | 9월~ 집중 준비 | 프로그래머스 Lv2~3 |
| DDIA (데이터 중심 애플리케이션 설계) | 병행 독서 | Part1→Part3→Part2 순서로 |
| Udemy AWS 강의 | 틈틈이 수강 | VPC, 클라우드 심화 (SAA 강의 내 추가 학습) |
| 블로그 '공대키메라' | 상시 | 학습 과정 꾸준히 기록 |

---

## 타겟 회사 (국내)
- 네이버랩스, 42dot, 현대로보틱스, HL클레무브, 라이드플럭스 등
- 모빌리티/로봇 회사의 **백엔드/플랫폼 엔지니어** 포지션

## 타겟 회사 (일본)
- 일본 로봇 산업 세계 최고 수준 - 선택지로 열어둠
