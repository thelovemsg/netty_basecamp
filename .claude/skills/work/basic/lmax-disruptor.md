# LMAX Disruptor 분석 레퍼런스

> 출처: [tech-monster.tistory.com](https://tech-monster.tistory.com) — 공식 테크니컬 페이퍼 + 개발자 블로그 글 분석 시리즈
> - [Part 1: Overview ~ Pipelines and Graphs](https://tech-monster.tistory.com/296)
> - [Part 2: Design of the LMAX Disruptor ~ Conclusion](https://tech-monster.tistory.com/297)
> - [Part 3: 개발자 블로그 글 — Ring Buffer 내부 동작](https://tech-monster.tistory.com/298)

---

## 1. LMAX 배경

LMAX는 금융 기술 기업으로, Java 플랫폼에서 저지연·고처리량 동시성 처리를 달성하기 위해 Disruptor를 개발했다.
핵심 철학은 **Mechanical Sympathy** — 하드웨어와 소프트웨어가 조화롭게 동작하도록 설계하는 것.

---

## 2. 기존 동시성 메커니즘의 문제점

### 2.1 Lock

- OS 커널 개입 + 컨텍스트 스위칭 → CPU 캐시 손실
- 성능 벤치마크 (단순 카운터 증가):
  - 싱글 스레드: **300ms**
  - Lock 사용 (단일 스레드): **10,000ms**
  - Lock 사용 (두 스레드): **224,000ms**

### 2.2 CAS (Compare-And-Swap)

- Lock-free 대안이지만 여전히 오버헤드 존재
- 인스트럭션 파이프라인 잠금 + 메모리 배리어 비용

### 2.3 Memory Barrier

- Java `volatile` 필드 → 메모리 배리어로 스레드 간 가시성 보장
- CPU 최적화(명령어 재배치)에도 불구하고 올바른 순서 보장

### 2.4 Cache Line & False Sharing

- 독립적인 변수가 동일한 **64바이트 캐시 라인**을 공유할 때 발생
- 서로 다른 스레드가 같은 캐시 라인의 다른 변수를 수정 → 불필요한 캐시 무효화(contention)

### 2.5 Queue의 근본적 문제

| 문제 | 설명 |
|------|------|
| Head/Tail 경합 | 생산자·소비자가 동일 포인터를 경쟁적으로 수정 |
| False Sharing | Head와 Tail이 같은 캐시 라인에 위치할 가능성 |
| GC 압력 | Linked-list 기반 큐는 노드 할당/해제가 빈번 |
| 복잡한 동시성 설계 | 정확성 보장이 극도로 어려움 |

### 2.6 Pipeline 병목

- 멀티 스테이지 처리에서 큐를 중간 연결로 사용 → enqueue/dequeue 오버헤드 누적
- 브랜치 머징 비용 추가

---

## 3. Disruptor 설계 원리

### 3.1 메모리 사전 할당 (Pre-allocation)

Ring Buffer 구조에 **시작 시점에 모든 메모리를 미리 할당**:

1. GC 압력 제거 — 객체를 시스템 수명 동안 재사용
2. 공간 지역성(Spatial Locality) 확보 — 연속 메모리 할당으로 캐시 친화적
3. Minor → Major GC 승격 문제 방지

### 3.2 관심사 분리 (Separation of Concerns)

전통적 큐에서 혼재된 3가지 책임을 분리:

| 책임 | 설명 |
|------|------|
| **항목 저장** | Ring Buffer가 담당 |
| **생산자 시퀀싱/조정** | ProducerBarrier가 담당 |
| **소비자 알림** | ConsumerBarrier가 담당 |

→ 단일 스레드 소유권(single-threaded ownership)으로 쓰기 경합 제거

### 3.3 시퀀싱과 동기화

- Lock/CAS 대신 **메모리 배리어 + 시퀀스 넘버** 사용
- 다수 생산자 경쟁 시 **Busy Spin** 대기 → CAS 연산이 마이크로초 이하로 반환되므로 고속

### 3.4 Ring Buffer 구조

- **2의 거듭제곱 크기** 사용 → 비싼 modulo 연산 대신 **비트 마스킹**으로 인덱스 계산
- 예: `index = sequence & (bufferSize - 1)`

### 3.5 배칭 효과 (Batching)

- 소비자가 커서가 자신의 마지막 위치를 넘어갈 때, 여러 항목을 동기화 오버헤드 없이 일괄 처리
- 전통 큐의 "J-curve" 성능 저하 대신 **예측 가능한 성능 특성** 유지
- Little's Law를 따르며, 메모리 서브시스템 포화까지 거의 일정한 지연시간

---

## 4. 성능 비교

### 처리량 (Throughput)

Doug Lea의 `ArrayBlockingQueue`(가장 빠른 bounded queue 구현체) 대비 대폭 향상.

### 지연시간 (Latency) — 3단계 파이프라인 테스트

| 구현체 | Hop당 지연시간 | 테스트 환경 |
|--------|---------------|-------------|
| **Disruptor** | **52 ns** | 2.2GHz Core i7-2720QM |
| ArrayBlockingQueue | 32,757 ns | 동일 |

→ ABQ의 지연시간 원인: Lock 사용 + Condition Variable 시그널링 오버헤드

---

## 5. 핵심 컴포넌트 (아키텍처)

| 컴포넌트 | 역할 |
|----------|------|
| **Ring Buffer** | 고정 크기 원형 버퍼, 사전 할당된 Entry 관리 (v3.0+) |
| **Sequence** | 컴포넌트 위치 식별, AtomicLong 유사 + false sharing 방지 패딩 |
| **Sequencer** | 진정한 코어 — 생산자-소비자 동기화 알고리즘 구현 |
| **Sequence Barrier** | 소비자가 사용 가능한 이벤트를 처리할 수 있는지 판단하는 로직 |
| **Wait Strategy** | 소비자 대기 전략 정의 (Blocking, SpinWait, Yield 등) |
| **Event Processor** | 이벤트 관리 + 소비자 시퀀스 소유권 유지 |
| **Event Handler** | 사용자가 구현하는 소비자 인터페이스 |
| **ProducerBarrier** | 슬롯 획득 관리, 래핑어라운드 방지 |
| **ConsumerBarrier** | 소비자 시그널링 + 의존성 그래프 지원 |

---

## 6. Ring Buffer 쓰기 내부 동작

### 6.1 2단계 커밋 프로세스 (Producer)

```
1. nextEntry()  → 다음 슬롯 요청
2. commit()     → 쓰기 완료 후 커밋
```

- 분리 이유: **부분적 쓰레기 값(partial garbage values)**이 소비자에게 노출되는 것을 방지
- 서로 다른 속도의 소비자가 독립적으로 처리 가능

### 6.2 다중 생산자 조정 (Multi-Producer)

- `ClaimStrategy`가 시퀀스 넘버를 Ring Buffer 커서와 **별도로** 추적
- Producer 1이 아직 커밋하지 않았는데 Producer 2가 준비되면 → Producer 2는 이전 시퀀스 완료까지 **블록**
- 순서 보장 유지

### 6.3 배치 쓰기 (Bulk Write)

- 버퍼 크기 10, 가장 느린 소비자 위치가 9일 때 → 생산자가 슬롯 3~8에 한번에 쓰기 가능
- 소비자 위치 확인을 매번 하지 않아도 됨

---

## 7. netty-basecamp 프로젝트와의 관계

현재 프로젝트에서 Disruptor는 **Log4j2 비동기 로깅**에 사용 중:

| 의존성 | 버전 | 용도 |
|--------|------|------|
| Log4j2 | 2.25.3 | 로깅 프레임워크 |
| LMAX Disruptor | 4.0.0 | Log4j2 AsyncLogger 백엔드 |

Log4j2의 `AsyncLogger`는 내부적으로 Disruptor의 Ring Buffer를 사용하여
로깅 이벤트를 비동기로 처리함으로써 애플리케이션 스레드의 블로킹을 최소화한다.

> **참고**: 블로그의 원본 자료는 2011년 소스 기준이므로 현재 Disruptor 4.0과 API 차이가 있을 수 있으나,
> 근본적인 아키텍처 패턴(Ring Buffer, Sequence, Barrier)은 동일하게 유지됨.
