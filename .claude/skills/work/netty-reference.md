# UPG_LIB_6A 프로젝트 아키텍처 분석

> Netty 기반 비동기 결제 게이트웨이 서버의 스레드 모델, 채널 파이프라인, Work 확장성 구조 분석

### 📖 이 문서를 읽기 전에 — 핵심 용어 정리

| 용어 | 쉬운 설명 |
|---|---|
| **Netty** | Java로 만든 고성능 네트워크 서버 프레임워크. 비동기 I/O를 쉽게 구현할 수 있게 해줌 |
| **비동기(Async)** | 요청을 보내고 응답을 기다리는 동안 다른 일을 처리할 수 있는 방식. 동기(Sync)는 응답이 올 때까지 아무것도 못 함 |
| **스레드(Thread)** | 프로그램이 동시에 여러 작업을 처리하기 위한 실행 단위. 스레드가 많을수록 동시 처리량이 높아짐 |
| **블로킹(Blocking)** | DB 쿼리처럼 결과가 올 때까지 스레드가 멈춰서 기다리는 것. 스레드가 블로킹되면 다른 요청을 처리할 수 없음 |
| **채널(Channel)** | 클라이언트와 서버 간의 네트워크 연결 하나를 의미. 소켓 연결 = 채널 |
| **파이프라인(Pipeline)** | 물이 파이프를 순서대로 통과하듯, 네트워크 데이터가 여러 처리 단계(핸들러)를 순서대로 거치는 구조 |
| **핸들러(Handler)** | 파이프라인 안에서 데이터를 처리하는 각각의 단계. 예: 바이트→문자 변환, HTTP 파싱, 비즈니스 로직 실행 등 |
| **GiftBox** | 이 프로젝트에서 만든 요청/응답 데이터 담는 그릇(HashMap 기반). 모든 데이터가 이 객체에 담겨서 전달됨 |
| **전문(電文)** | 시스템 간 약속된 형식의 메시지. 어떤 필드가 몇 바이트인지 미리 정해놓고 그 규격대로 데이터를 주고받음 |
| **리플렉션(Reflection)** | Java에서 클래스 이름(문자열)만 가지고 해당 클래스를 찾아서 실행하는 기술 |

---

## 1. 전체 아키텍처 개요

```
                        ┌──────────────────────────────────────────┐
  Client Request ──────▶│  Boss Thread Group (parent_thread_cnt=1) │
  (TCP/HTTP/HTTPS)      │        연결 수락 (Accept)                 │
                        └─────────────┬────────────────────────────┘
                                      │
                        ┌─────────────▼────────────────────────────┐
                        │ Worker Thread Group (child_thread_cnt=2)  │
                        │   I/O Thread 1  │  I/O Thread 2          │
                        │   (인코딩/디코딩)  │  (인코딩/디코딩)         │
                        └─────────────┬────────────────────────────┘
                                      │
                        ┌─────────────▼────────────────────────────┐
                        │ Executor Group (work_thread_cnt=4)        │
                        │ Thread 1 │ Thread 2 │ Thread 3 │ Thread 4│
                        └─────────────┬────────────────────────────┘
                                      │
                        ┌─────────────▼────────────────────────────┐
                        │         Work 클래스 (비즈니스 로직)          │
                        │ PCD0100001 │ UHA0100001 │ MEG0100001 ... │
                        └──────────────────────────────────────────┘
```

---

## 2. 스레드(Thread) 설계 — 3계층 스레드 모델

프로젝트는 **3계층 스레드 분리** 전략으로 Netty의 비동기 성능을 극대화합니다.

### 2.1 설정값 (`upg.properties`)

| 프로퍼티 | 기본값 | 역할 |
|---|---|---|
| `upg.parent_thread_cnt` | `1` | Boss 스레드 수 (연결 수락 전담) |
| `upg.child_thread_cnt` | `2` | Worker 스레드 수 (I/O 이벤트 처리) |
| `upg.work_thread_cnt` | `4` | 비즈니스 로직 전용 스레드 수 |

### 2.2 각 계층의 역할

#### ① Boss Group — `NioEventLoopGroup(parent_thread_cnt)`

```java
// ServerHandler.java:66
NioEventLoopGroup nioEventLoopGroup1 = new NioEventLoopGroup(
    Integer.parseInt(Config.getInstance().getProperty("upg.parent_thread_cnt"))
);
```

- **역할**: 클라이언트 TCP 연결 수락(Accept)만 전담
- **스레드 수**: `1`개로 충분 (단일 포트 바인딩)
- **왜 1개인가?**: Accept 이벤트는 매우 경량이므로 1개 스레드로 초당 수만 건의 커넥션을 처리 가능

#### ② Worker Group — `NioEventLoopGroup(child_thread_cnt)`

```java
// ServerHandler.java:67
NioEventLoopGroup nioEventLoopGroup2 = new NioEventLoopGroup(
    Integer.parseInt(Config.getInstance().getProperty("upg.child_thread_cnt"))
);
```

- **역할**: 수립된 소켓의 I/O 이벤트 처리 (데이터 읽기/쓰기, 인코딩/디코딩)
- **스레드 수**: `2`개
- **핵심 원칙**: I/O 스레드에서는 블로킹 작업을 절대 수행하지 않음 → 메시지 파싱과 직렬화만 수행

#### ③ Executor Group — `DefaultEventExecutorGroup(work_thread_cnt)`

```java
// ServerHandler.java:54
private static EventExecutorGroup executorGroup = 
    new DefaultEventExecutorGroup(
        Integer.parseInt(Config.getInstance().getProperty("upg.work_thread_cnt", "1"))
    );
```

- **역할**: DB 접근, 외부 API 호출 등 **블로킹이 발생하는 비즈니스 로직** 전담
- **스레드 수**: `4`개
- **핵심 설계 의도**: I/O 스레드(Worker)가 DB 쿼리 등으로 블로킹되지 않도록 별도 스레드 풀로 분리

### 2.3 왜 이렇게 설계했는가?

```
Client          Boss Thread     Worker Thread    Executor Thread    DB
  │                │                │                 │              │
  │─TCP Connect──▶│                │                 │              │
  │                │─Channel 등록──▶│                 │              │
  │                │                │─HTTP 디코딩─────│              │
  │                │                │ (Non-Blocking)  │              │
  │                │                │─Work 호출 위임──▶│              │
  │                │                │                 │─SQL 쿼리───▶│
  │                │                │  ★Worker는 즉시  │              │
  │                │                │  해제되어 다른    │◀─결과 반환──│
  │                │                │  I/O 처리 가능★  │              │
  │                │                │◀─응답 인코딩 요청│              │
  │◀──HTTP 응답 전송─────────────────│                 │              │
```

> **⚠️ 중요**: Worker 스레드가 DB I/O로 블로킹되면 다른 클라이언트의 요청도 처리 불가가 됩니다.
> `DefaultEventExecutorGroup`으로 WorkHandler를 분리함으로써, Worker 스레드는 항상 Non-Blocking으로 I/O만 처리하고 블로킹 작업은 Executor 스레드가 처리합니다.

---

## 3. 채널(Channel) 파이프라인 설계

파이프라인이란 **데이터가 거쳐가는 처리 단계들의 순서**입니다.  
수도관처럼 물(데이터)이 한 방향으로 흐르면서 각 단계(핸들러)를 거치며 가공됩니다:

```
요청 방향 (Inbound):   수신 바이트 → 문자 변환 → 메시지 파싱 → 비즈니스 로직
응답 방향 (Outbound):  비즈니스 로직 → 메시지 직렬화 → 바이트 변환 → 전송
```

`ServerHandler.java`의 `initChannel()` 메서드에서 프로토콜별로 다른 파이프라인을 구성합니다.

어떤 프로토콜을 사용할지는 **`upg.properties` 설정 파일 하나로** 결정됩니다:

```properties
# conf/upg.properties
upg.listenType=HTTPS    # TCP / TCPP / HTTP / HTTPS 중 하나 선택
upg.port=51002          # 서버 리스닝 포트
```

```java
// ServerHandler.java:54 — 설정값을 읽어서 static 변수에 저장
public static String listenType = Config.getInstance().getProperty("upg.listenType");

// initChannel() 내부 — listenType 값에 따라 파이프라인이 완전히 달라짐
if (listenType.equals("TCPP"))      { /* Proxy Protocol + TCP 파이프라인 */ }
else if (listenType.equals("TCP"))  { /* TCP 파이프라인 */ }
else if (listenType.equals("HTTPS")){ /* SSL + HTTP 파이프라인 */ }
else                                { /* HTTP 파이프라인 (기본값) */ }
```

> **서버 하나에 프로토콜 하나**: 하나의 서버 인스턴스는 `listenType`에 설정된 하나의 프로토콜만 처리합니다.  
> 여러 프로토콜이 필요하면 서버를 여러 개 띄우고 포트를 다르게 설정합니다.  
> ※ UDP는 지원하지 않습니다 — `NioServerSocketChannel`(TCP 기반)만 사용합니다.

### 3.1 TCP/TCPP 파이프라인

```
┌─────────────────────────────────────────────────┐
│           TCP Channel Pipeline                  │
├─────────────────────────────────────────────────┤
│  [선택] ProxyProtocolHandler (TCPP인 경우만)      │
│  ↓                                               │
│  StringLengthFieldBasedFrameDecoder              │
│    └─ offset=10, length=6, adj=-16              │
│  ↓                                               │
│  StringDecoder (UTF-8)                           │
│  ↓                                               │
│  StrMessageInboundCodec  → GiftBox 생성           │
│  ↓                                               │
│  WorkHandler  (비즈니스 로직 실행)                  │
│  ↓                                               │
│  StringEncoder (UTF-8)                           │
│  ↓                                               │
│  StrMessageOutboundCodec → 응답 직렬화             │
└─────────────────────────────────────────────────┘
```
> **특징**: TCP 모드에서는 Worker 스레드에서 직접 WorkHandler 실행 (⚠️ 주의사항 8장 참고)

### 3.2 HTTP 파이프라인

```
┌─────────────────────────────────────────────────┐
│           HTTP Channel Pipeline                 │
├─────────────────────────────────────────────────┤
│  HttpServerCodec                                │
│  ↓                                               │
│  HttpObjectAggregator (512KB)                   │
│  ↓                                               │
│  HttpMessageInboundCodec → GiftBox 생성           │
│  ↓                                               │
│  WorkHandler ★executorGroup에서 실행★             │
│  ↓                                               │
│  HttpMessageOutboundCodec → HTTP 응답 생성         │
└─────────────────────────────────────────────────┘
```
> **핵심**: `pipeline.addLast(executorGroup, new WorkHandler(...))`
> WorkHandler가 별도 Executor 스레드 풀에서 실행됨

### 3.3 HTTPS 파이프라인

```
┌─────────────────────────────────────────────────┐
│           HTTPS Channel Pipeline                │
├─────────────────────────────────────────────────┤
│  SSLHandler (SslContext 기반)                     │
│  ↓                                               │
│  HttpServerCodec                                │
│  ↓                                               │
│  HttpObjectAggregator (512KB)                   │
│  ↓                                               │
│  HttpMessageInboundCodec → GiftBox 생성           │
│  ↓                                               │
│  WorkHandler ★executorGroup에서 실행★             │
│  ↓                                               │
│  HttpMessageOutboundCodec → HTTP 응답 생성         │
└─────────────────────────────────────────────────┘
```
> **차이점**: SSL/TLS 핸드셰이크 처리 이후 HTTP와 동일한 파이프라인

### 3.4 파이프라인 핵심 코드

```java
// HTTP/HTTPS에서 executorGroup으로 WorkHandler를 분리 실행
// ServerHandler.java:192
pipeline.addLast(executorGroup, new WorkHandler(documentManager, ipinfo));
```

이 한 줄이 핵심입니다:
- `executorGroup` 파라미터를 전달하면 → Netty가 해당 핸들러를 **별도 스레드 풀**에서 실행
- 전달하지 않으면 (TCP 모드) → Worker I/O 스레드에서 직접 실행

---

## 4. Work 파일 확장성 구조 — 리플렉션 기반 동적 라우팅

### 4.1 전체 흐름

```
HTTP 요청(ID=PCD01, VERSION=00001)
      ↓
HttpMessageInboundCodec  
      ↓  파라미터 파싱 → GiftBox 생성 (전문 모델 바인딩)
WorkHandler
      ↓  Class.forName("co.kr.upg.work.PCD0100001")
work() 메서드 실행 (리플렉션 호출)
      ↓
응답 GiftBox 구성 → writeAndFlush → 클라이언트
```

### 4.2 WorkHandler의 라우팅 메커니즘

`WorkHandler.java`에서 핵심 라우팅 로직:

```java
// WorkHandler.java:61-87
String id = req.getModel().getId();          // 예: "PCD01"
String version = req.getModel().getVersion(); // 예: "00001"

// 동적 클래스 로딩 — 패키지 규약: co.kr.upg.work.{ID}{VERSION}
Class<?> workClass = Class.forName("co.kr.upg.work." + id + version);
Object obj = workClass.newInstance();

// work() 메서드 리플렉션 호출
Method work = obj.getClass().getDeclaredMethod(
    "work", ChannelHandlerContext.class, SqlSessionFactory.class, GiftBox.class, GiftBox.class
);
work.invoke(obj, ctx, sqlClient, req, rep);
```

### 4.3 Work 클래스 규약 (확장 방법)

모든 Work 클래스는 `WorkAbstract`를 상속합니다:

```java
public abstract class WorkAbstract {
    public abstract void work(
        ChannelHandlerContext chx,
        SqlSessionFactory sqlClient,
        GiftBox req,     // 요청 데이터
        GiftBox rep      // 응답 데이터
    ) throws Exception;
}
```

#### 새로운 기능 추가 방법 (3단계)

| 단계 | 작업 | 파일 |
|---|---|---|
| ① XML 전문 정의 | 요청/응답 메시지 구조를 XML에 추가 | `conf/upg_ap_meg.xml` |
| ② Work 클래스 생성 | `co.kr.upg.work` 패키지에 `{ID}{VERSION}.java` 생성 | `src/co/kr/upg/work/XXX0100001.java` |
| ③ 완료 | 서버 재시작시 리플렉션으로 자동 연결됨 | — |

> **💡 프레임워크 코드(서버/파이프라인)를 전혀 수정하지 않고** Work 클래스만 추가하면 새 기능이 자동으로 라우팅됩니다.

#### 실전 예시: "NEW01" 이라는 새 기능 추가하기

**1단계) `conf/upg_ap_meg.xml`에 요청/응답 전문 추가:**
```xml
<!-- 요청 전문: ID의 4번째 문자가 '0' -->
<message id="NEW01" version="00001" description="새기능 요청">
    <column name="USER_NAME" mode="AN" size="30" required="Y" encrypt="N" description="사용자명" />
</message>

<!-- 응답 전문: ID의 4번째 문자가 '1' (규칙: 0→1) -->
<message id="NEW11" version="00001" description="새기능 응답">
    <column name="RESULT" mode="AN" size="100" required="N" encrypt="N" description="결과" />
</message>
```

**2단계) `src/co/kr/upg/work/NEW0100001.java` 파일 생성:**
```java
package co.kr.upg.work;

import co.kr.upg.common.WorkAbstract;
import co.kr.upg.common.document.GiftBox;
import io.netty.channel.ChannelHandlerContext;
import org.apache.ibatis.session.SqlSessionFactory;

public class NEW0100001 extends WorkAbstract {
    @Override
    public void work(ChannelHandlerContext ctx, SqlSessionFactory sqlClient,
                     GiftBox req, GiftBox rep) throws Exception {
        
        // req에서 요청 데이터 꺼내기
        String userName = req.getString("USER_NAME");
        
        // 비즈니스 로직 수행 (DB 조회 등)
        // ...
        
        // rep에 응답 데이터 넣기
        rep.put("RESULT", "처리 완료: " + userName);
        rep.put("RESP_CODE", "00000000");  // 성공 코드
        rep.put("RESP_MSG", "SUCCESS");
    }
}
```

**3단계) 끝!** 서버 재시작 후 아래처럼 호출하면 자동 연결됩니다:
```
POST http://서버:51002/
ID=NEW01&VERSION=00001&RETURN_TYPE=JSON&MSG_LANGUAGE_CD=KOR&USER_NAME=홍길동
```

### 4.4 Work 파일 네이밍 규칙

현재 등록된 Work 클래스 32개의 카테고리:

| 접두사 | 의미 | 예시 |
|---|---|---|
| `PCD` | PMS 코드 관리 | `PCD0100001` (생성), `PCD0200001` (수정) |
| `UHA` | UHPG 결제 승인 | `UHA0100001` |
| `WPA` | WorldPay 결제 | `WPA0100001`, `WPA0200001`, `WPA0300001` |
| `MEG` | SMS/이메일 발송 | `MEG0100001`, `MEG0200001` |
| `PRO` | 프로시저 호출 | `PRO0100001` ~ `PRO0500001` |
| `SQL` | SQL 쿼리 실행 | `SQL0100001` ~ `SQL0300001` |
| `LOG` | 로그 처리 | `LOG0100001`, `LOG0200001` |
| `CCC` | 결제 취소 | `CCC0100001` |
| `TML` | HTML 페이지 렌더링 | `TML0100001` |
| `HAL` | AWS 헬스체크 | `HAL0100001` |
| `WTL` | 웹 로그인 | `WTL0100001` |
| `UHM` | 모니터링 | `UHM0100001` ~ `UHM0700001` |
| `TTI` | 테스트 TID | `TTI0100001` |

---

## 5. 메시지 프로토콜 — GiftBox와 DocumentManager

### 5.1 XML 기반 전문 정의

`conf/upg_ap_meg.xml`에서 모든 요청/응답의 메시지 구조를 선언적으로 정의합니다:

```xml
<header>
    <column name="ID"       mode="AN" size="5"  required="Y" description="전문ID" />
    <column name="VERSION"  mode="AN" size="5"  required="Y" description="버전" />
    <column name="LENGTH"   mode="N"  size="6"  required="N" description="전체길이" />
    ...
</header>

<message id="MEG01" version="00001" description="SMS 발송">
    <column name="TO_TEL"  mode="AN" size="20" description="수신번호" />
    <column name="TYPE"    mode="AN" size="10" description="SMS" />
    <dynamic name="MESSAGE_LENGTH" mode="N" size="5">
        <column name="MESSAGE" mode="AN" size="0" description="메시지 본문" />
    </dynamic>
</message>
```

**XML 컬럼 속성 설명:**

| 속성 | 의미 | 예시 |
|---|---|---|
| `name` | 필드 이름 (GiftBox에서 이 이름으로 get/put) | `"CARD_NO"` |
| `mode` | 데이터 타입 (TCP 직렬화 시 패딩 방식 결정) | 아래 표 참고 |
| `size` | TCP 모드에서의 고정 바이트 길이 (HTTP에서는 무시) | `20` |
| `required` | 필수 입력 여부 | `"Y"` / `"N"` |
| `encrypt` | 암호화 대상 여부 | `"Y"` / `"N"` |

**mode 값의 의미:**

| mode 코드 | 의미 | TCP 직렬화 패딩 | 예시 (size=10) |
|---|---|---|---|
| `A` | 문자(Alpha) | 우측 공백 채움 | `"HELLO     "` |
| `AN` | 영숫자(AlphaNumeric) | 우측 공백 채움 | `"ABC123    "` |
| `AH` | 한글 포함 문자 | 우측 공백 (한글 바이트 수 계산) | `"안녕      "` |
| `N` | 숫자(Numeric) | 좌측 0 채움 | `"0000000123"` |

**`<dynamic>` 태그**: 가변 길이 필드. 바로 위의 길이 필드 값만큼 다음 데이터를 읽음  
**`<loop>` 태그** (XML에는 없지만 코드에서 지원): 반복 데이터. 반복 횟수만큼 내부 컬럼을 반복 읽기

### 5.2 GiftBox — 요청/응답 데이터 컨테이너

```
  Box (HashMap 기반)
    ▲
    │ extends
  GiftBox
    ├─ byte[] binary    (바이너리 메시지)
    ├─ Document model   (XML 전문 모델 참조)
    ├─ String clientIp  (클라이언트 IP)
    └─ validate()       (전문 유효성 검증)
```

- `GiftBox`는 `HashMap` 기반의 키-값 데이터 컨테이너
- `Document` 모델과 연결되어 XML 전문 정의에 따라 자동 파싱/직렬화

#### GiftBox를 HashMap으로 만들어도 괜찮은 이유

`LinkedHashMap`은 Thread-Safe하지 않지만, 이 프레임워크에서는 **안전합니다**:

1. **매 요청마다 새 인스턴스 생성** — `DocumentManager.newGiftBox()`가 매번 `new GiftBox()`를 호출하므로 요청 A와 요청 B의 GiftBox는 완전히 별개 객체
2. **단일 파이프라인에서 순차 처리** — 하나의 GiftBox는 `Inbound Codec → WorkHandler → Outbound Codec` 순서로 하나의 스레드 흐름에서만 사용됨

#### GiftBox 사용 시 주의사항

```java
// ✅ 올바른 사용 — work() 메서드 내에서만 사용
public void work(..., GiftBox req, GiftBox rep) {
    String value = req.getString("KEY");   // getString()으로 안전하게 꺼내기
    rep.put("RESULT", value);              // put()으로 응답에 넣기
}

// ❌ 위험한 사용 — static 변수에 GiftBox를 저장하면 여러 스레드가 동시 접근
private static GiftBox cachedBox;  // 절대 금지!

// ⚠️ 주의 — 타입 안전성 없음 (어떤 타입이든 넣을 수 있으므로 꺼낼 때 주의)
rep.put("AMT", 1000);      // Integer
rep.put("AMT", "1000");    // String — 둘 다 들어감. getString()/getLong() 헬퍼 메서드 활용 권장
```

### 5.3 메시지 코덱 동작 흐름

```
[Inbound]  HTTP 요청 → HttpMessageInboundCodec.decode()
           → 파라미터 추출 (GET: QueryString / POST: Form Data)
           → ID+VERSION으로 Document 매핑
           → GiftBox 생성 후 파이프라인으로 전달

[Outbound] GiftBox (응답) → HttpMessageOutboundCodec.encode()
           → RETURN_TYPE에 따라 JSON/XML/DSXML/SHTML/HTML 직렬화
           → FullHttpResponse 생성 후 클라이언트 전송
```

---

## 6. 서버 기동과 Graceful Shutdown

### 6.1 기동 순서 (`UpgServerMain.run()`)

```
1. UPG_MODE 판별 (kms / test / real)
2. JMX MBean 등록 (모니터링)
3. KeyInitializer 실행 (암호화 키 로딩)
4. DB 연결 (SqlMapMgr 초기화 + 연결 테스트)
5. ServerHandler.runServer() → Netty 서버 시작
6. channel.closeFuture().sync() → 서버 종료까지 대기
```

### 6.2 Graceful Shutdown

```java
// ServerContext.shutdown() → ServerHandler.channelClose()
executorGroup.shutdownGracefully().sync(); // Executor 종료
ch.disconnect();  // 채널 연결 해제
ch.close();       // 채널 닫기
// finally 블록에서
nioEventLoopGroup1.shutdownGracefully(); // Boss 종료
nioEventLoopGroup2.shutdownGracefully(); // Worker 종료
```

---

## 7. 요약: 비동기 성능 최적화 포인트

| 최적화 포인트 | 구현 방식 | 효과 |
|---|---|---|
| **I/O와 비즈니스 로직 분리** | `DefaultEventExecutorGroup`으로 Work 스레드 분리 | Worker 스레드 블로킹 방지 |
| **Boss/Worker 분리** | NioEventLoopGroup 2개 사용 | Accept과 I/O 처리 독립 실행 |
| **Zero-Copy 프레임 디코딩** | `StringLengthFieldBasedFrameDecoder` | TCP 스트림을 효율적으로 프레이밍 |
| **리플렉션 기반 라우팅** | `Class.forName()`으로 동적 Work 로딩 | 프레임워크 코드 무수정 확장 |
| **XML 선언적 전문 정의** | `DocumentManager` + XML | 메시지 구조 변경이 코드 변경 없이 가능 |
| **비동기 응답 콜백** | `ChannelFuture.addListener()` | 응답 전송 완료 후 채널 자동 정리 |

---

## 8. ⚠️ 추가로 신경 써야 할 사항들

### 8.1 🔴 TCP 모드의 Worker 스레드 블로킹 위험

```java
// TCP 파이프라인 — executorGroup 없이 WorkHandler 실행
pipeline.addLast(new WorkHandler(documentManager, ipinfo));  // Worker 스레드에서 직접 실행!

// HTTP 파이프라인 — executorGroup으로 분리
pipeline.addLast(executorGroup, new WorkHandler(documentManager, ipinfo));  // Executor에서 실행
```

**문제**: TCP 모드에서는 `executorGroup` 없이 WorkHandler를 등록합니다.
이 경우 Work 클래스 내부에서 DB 쿼리 등 블로킹 작업을 하면 **Worker I/O 스레드가 직접 블로킹**됩니다.
`child_thread_cnt=2`이므로 동시에 2개 이상의 느린 요청이 들어오면 **전체 서버가 멈출 수 있습니다**.

**대응**:
- TCP 모드를 사용한다면 HTTP처럼 `executorGroup`을 추가하거나
- TCP Work 클래스에서 블로킹 작업을 하지 않도록 주의

### 8.2 🔴 Work 클래스의 스레드 안전성 (Thread Safety)

```java
// WorkHandler.java:66
Object obj = workClass.newInstance(); // 매 요청마다 새 인스턴스 생성
```

- 매 요청마다 `newInstance()`로 새 객체를 만들므로 인스턴스 변수 충돌은 없습니다.
- **하지만** Work 클래스에서 `static` 변수나 공유 자원(예: 캐시, 전역 맵)을 사용하면 **동시성 문제**가 발생할 수 있습니다.
- `work_thread_cnt=4`이므로 최대 4개 Work가 동시 실행됩니다 → `static` 자원에는 반드시 `synchronized`나 `ConcurrentHashMap` 등을 사용하세요.

### 8.3 🟡 work_thread_cnt 튜닝 — 동시 처리량의 병목

현재 `work_thread_cnt=4`로 설정되어 있습니다. 이 값이 **서버의 동시 처리 가능 요청 수**를 직접 결정합니다.

```
동시 요청 5개 → 4개 처리 중 + 1개 대기
DB 쿼리가 평균 100ms라면 → 초당 최대 약 40 TPS
DB 쿼리가 평균 1초라면 → 초당 최대 약 4 TPS
```

- 트래픽에 따라 이 값을 늘려야 합니다 (CPU 코어 수 × 2 정도가 일반적)
- 너무 크게 설정하면 DB 커넥션 풀이 부족해질 수 있으므로 DB 커넥션 수와 함께 조정 필요

### 8.4 🟡 SqlSessionFactory의 DB 커넥션 관리

```java
// WorkHandler.java:59
SqlSessionFactory sqlClient = SqlMapMgr.getSqlMap();
```

- `SqlSessionFactory`는 싱글톤으로 공유되지만, `SqlSession`은 Work 클래스 내부에서 열고 닫습니다.
- **주의**: Work에서 `try-with-resources`로 SqlSession을 닫지 않으면 DB 커넥션 누수가 발생합니다.

```java
// 올바른 패턴 (PCD0100001.java에서 이미 사용 중)
try (SqlSession session = sqlClient.openSession(false)) {
    // DB 작업
    session.commit();
}  // 자동으로 close됨 ← 반드시 이 패턴을 사용

// 잘못된 패턴
SqlSession session = sqlClient.openSession();
// DB 작업
// session.close()를 빼먹으면 커넥션 누수!
```

### 8.5 🟡 CORS 설정이 임시 상태

```java
// ExceptionUtil.java:198
defaultFullHttpResponse.headers().set("Access-Control-Allow-Origin", "*"); // 모든 출처를 허용
```

- 에러 응답에서 `Access-Control-Allow-Origin: *`으로 설정되어 있습니다 (주석에 "임시"라고 표기).
- 결제 게이트웨이에서 `*`는 보안상 위험합니다 → 운영 환경에서는 특정 도메인만 허용하도록 변경 필요

### 8.6 🟡 응답 ID 변환 규칙 — 반드시 숙지

```java
// WorkHandler.java:71
id = id.substring(0, 3) + "1" + id.substring(4, 5);
// 예: "PCD01" → "PCD11", "UHA01" → "UHA11", "MEG01" → "MEG11"
```

- 요청 ID의 4번째 문자를 `0`→`1`로 바꿔 응답 ID를 만듭니다.
- XML 전문에서 요청/응답 쌍이 이 규칙을 따르지 않으면 응답 구조를 찾지 못합니다.
- **새 전문 추가 시 반드시 이 규칙을 지켜야 합니다**: 요청 `XXX0Y` → 응답 `XXX1Y`

### 8.7 🟢 리플렉션 오버헤드

- 매 요청마다 `Class.forName()` + `newInstance()` + `getDeclaredMethod()` + `invoke()`를 호출합니다.
- 결제 시스템 특성상 초당 수천 건의 요청이 아니라면 성능 이슈는 크지 않지만, 고빈도 트래픽 환경이라면 클래스/메서드 캐싱을 고려할 수 있습니다.

### 8.8 🟢 ExceptionUtil의 cause.printStackTrace()

```java
// ExceptionUtil.java:96, 125, 169 등
cause.printStackTrace(); // System.err에 출력
```

- 에러 핸들링 시 `printStackTrace()`가 호출되어 **System.err에 스택트레이스가 출력**됩니다.
- 운영 환경에서는 로거(`logger.error`)로 통일하는 것이 로그 관리에 유리합니다.

---

## 9. 네트워크 요청/응답 처리 상세 분석

### 9.1 전체 요청-응답 라이프사이클

```
┌─────────────────────────────────────────────────────────────────────┐
│                    전체 요청-응답 흐름                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [1] 클라이언트 요청 수신                                             │
│      │                                                              │
│  [2] Inbound Codec: 원시 데이터 → GiftBox(req) 변환                  │
│      │  ├─ HTTP: HttpMessageInboundCodec.decode()                   │
│      │  └─ TCP:  StrMessageInboundCodec.decode()                    │
│      │                                                              │
│  [3] WorkHandler: 리플렉션으로 Work 클래스 호출                       │
│      │  ├─ 응답 ID 생성 (XXX0Y → XXX1Y)                             │
│      │  ├─ DocumentManager.newGiftBox()로 GiftBox(rep) 생성          │
│      │  └─ work.invoke() → 비즈니스 로직 실행                        │
│      │                                                              │
│  [4] 응답 전송                                                       │
│      │  ├─ 정상: writeAndFlush(rep) → Outbound Codec                │
│      │  └─ 에러: ExceptionUtil.exceptionCaught()                    │
│      │                                                              │
│  [5] Outbound Codec: GiftBox(rep) → HTTP Response / TCP 바이트 변환  │
│      │  ├─ HTTP: HttpMessageOutboundCodec.encode()                  │
│      │  └─ TCP:  StrMessageOutboundCodec.encode()                   │
│      │                                                              │
│  [6] 전송 완료 후 채널 닫기 (ChannelFutureListener)                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 9.2 Inbound(수신) 처리 — 요청이 GiftBox가 되기까지

#### HTTP 모드: `HttpMessageInboundCodec.decode()`

```
클라이언트 HTTP 요청
    │
    ▼
1. MDC 초기화 (channelID, clientIP)
    │  ├─ X-Forwarded-For 헤더 있으면 → 프록시 뒤 실제 IP 추출
    │  └─ 없으면 → 소켓에서 직접 IP 추출
    │
    ▼
2. HTTP 메서드별 파라미터 추출
    │  ├─ GET:  QueryStringDecoder로 URL 쿼리 파라미터 파싱
    │  └─ POST: QueryString + HttpPostRequestDecoder로 Form 데이터 파싱
    │           (모든 키는 toUpperCase()로 대문자 통일)
    │
    ▼
3. 특수 URI 처리
    │  ├─ "/" + GET + ID 없음 → 헬스체크 (HAL0100001 GiftBox 생성)
    │  └─ URI에 ID/VERSION 없음 → 웹서비스 모드 (TML0100001로 매핑)
    │
    ▼
4. 필수값 검증
    │  ├─ MSG_LANGUAGE_CD (없으면 → U0000001 에러)
    │  ├─ ID (없으면 → U0009994 에러)
    │  ├─ VERSION (없으면 → U0009995 에러)
    │  └─ RETURN_TYPE (없으면 → U0009996 에러)
    │
    ▼
5. GiftBox 생성 & 파이프라인 전달
       DocumentManager.newGiftBox(ID + VERSION) 호출
       → XML 전문 정의에 맞는 Document 모델 바인딩
       → 파라미터를 GiftBox에 put()
       → out.add(reqBox) → 다음 핸들러(WorkHandler)로 전달
```

#### TCP 모드: `StrMessageInboundCodec.decode()`

```
TCP 바이트 스트림 수신
    │
    ▼
1. StringLengthFieldBasedFrameDecoder가 먼저 프레이밍
    │  offset=10(ID 5자 + VERSION 5자 이후),  length=6자,  adj=-16
    │  → 전문 길이 필드(LENGTH)를 읽어 정확한 바이트 경계로 잘라냄
    │
    ▼
2. StringDecoder가 바이트 → String 변환 (UTF-8)
    │
    ▼
3. StrMessageInboundCodec.decode()
       DocumentReader.readFull(바이트스트림)
       │
       ├─ 헤더 고정길이 영역 읽기 (ID, VERSION, LENGTH, TRANS_DTTM ...)
       │    → 각 컬럼의 size만큼 byte[]에서 잘라내서 GiftBox에 put
       │
       ├─ ID+VERSION으로 Document 조회
       │
       └─ Body 컬럼 순회하며 읽기
            ├─ 일반 Column: 고정 size만큼 읽기
            ├─ DynamicColumn: 길이필드 읽고 → 가변길이 데이터 읽기
            └─ Loop: 반복횟수 읽고 → 내부 컬럼 반복 파싱
```

**TCP 전문 바이트 레이아웃 예시:**

```
|  ID  |VERSION|LENGTH|TRANS_DTTM    |...|BODY_FIELD_1|DYN_LEN|DYN_DATA...|
|5byte | 5byte |6byte | 14byte      |...|  N byte    | 5byte | 가변      |
|PCD01 |00001  |000156|20260325093000|...|VALUE       |00025  |JSON데이터 |
```

---

### 9.3 WorkHandler — 요청 처리 & 응답 생성

```java
// WorkHandler.java 핵심 흐름

// [1] 요청에서 ID, VERSION 추출
String id = req.getModel().getId();          // "PCD01"
String version = req.getModel().getVersion(); // "00001"

// [2] 리플렉션으로 Work 클래스 로딩 & 실행
Class<?> workClass = Class.forName("co.kr.upg.work." + id + version);
Object obj = workClass.newInstance();
Method work = obj.getClass().getDeclaredMethod("work", ...);

// [3] 응답 ID 생성 (4번째 문자 0→1)
id = id.substring(0, 3) + "1" + id.substring(4, 5);
// "PCD01" → "PCD11"

// [4] 응답 GiftBox 생성 (XML 전문에서 응답 구조 로딩)
GiftBox rep = documentManager.newGiftBox(respStr, req);
// → 요청 GiftBox의 헤더값을 응답에 복사
// → MODE_N 컬럼은 기본값 "0", MODE_A 컬럼은 기본값 ""

// [5] Work 비즈니스 로직 실행
work.invoke(obj, ctx, sqlClient, req, rep);
// → Work 클래스에서 rep.put("RESP_CODE", "00000000") 등으로 응답 데이터 설정

// [6] 비동기 응답 전송 & 채널 정리
ChannelFuture cf = userChannel.writeAndFlush(rep);
cf.addListener(future -> {
    if (!future.isSuccess()) {
        // 망취소 로직 (전송 실패 시)
    }
    userChannel.close();  // 전송 완료 후 채널 닫기
});
```

---

### 9.4 Outbound(송신) 처리 — GiftBox가 응답이 되기까지

#### HTTP 모드: `HttpMessageOutboundCodec.encode()`

`RETURN_TYPE` 값에 따라 6가지 응답 포맷을 분기 처리합니다:

```
GiftBox(rep)
    │
    ▼  RETURN_TYPE 확인
    │
    ├─ "JSON" (기본값) ─────────────────────────────────────────────┐
    │   JsonUtil.JsonToString(rep)                                 │
    │   → Content-Type: application/json; charset=UTF-8            │
    │   → Access-Control-Allow-Origin: * (CORS 허용)               │
    │                                                              │
    ├─ "XML" ──────────────────────────────────────────────────────┤
    │   GiftBox의 모든 key-value를 XML 태그로 변환                  │
    │   <Data><List><KEY><![CDATA[VALUE]]></KEY>...</List></Data>   │
    │   → Content-Type: application/xml; charset=UTF-8             │
    │                                                              │
    ├─ "DSXML" ────────────────────────────────────────────────────┤
    │   rep.get("DATA")를 그대로 XML 바디로 전송                    │
    │   → DataSet 형식 (.NET 호환)                                 │
    │   → Content-Type: application/xml; charset=UTF-8             │
    │                                                              │
    ├─ "HTML" ─────────────────────────────────────────────────────┤
    │   rep.get("DATA")를 HTML 본문으로 그대로 전송                  │
    │   → Content-Type: text/html; charset=UTF-8                   │
    │   → Server: Orecle-WebLogic/12.2.1.4.0 (위장 헤더)           │
    │                                                              │
    ├─ "SHTML" ────────────────────────────────────────────────────┤
    │   자동 서브밋 HTML 폼 생성 (결제 결과 리다이렉트용)             │
    │   → <form action="RETURN_URL">                               │
    │     <input type="hidden" name="KEY" value="VALUE"/>          │
    │   → <body onload="document.result.submit()">                │
    │   → 브라우저가 자동으로 RETURN_URL에 POST 전송                │
    │                                                              │
    └─ "MEG" ──────────────────────────────────────────────────────┘
        DocumentWriter로 TCP 고정길이 포맷으로 직렬화
        → Content-Type: application/txt; charset=UTF-8
        (TCP 전문 포맷을 HTTP 응답 바디에 담아 전송)
```

> **SHTML의 동작 원리**: 결제 완료 후 결과를 가맹점에 전달할 때 사용.
> 서버가 클라이언트 브라우저에 자동 서브밋 폼 HTML을 보내면,
> 브라우저가 `onload` 시 자동으로 `RETURN_URL`에 POST 전송 → 가맹점 서버가 결과 수신.

#### TCP 모드: `StrMessageOutboundCodec.encode()`

```
GiftBox(rep)
    │
    ▼
DocumentWriter.write(rep, outputStream)
    │
    ├─ [1] 전체 길이 계산 (헤더 + 바디)
    │
    ├─ [2] 바디 직렬화 (컬럼 순서대로)
    │       ├─ 일반 Column: 고정 size로 패딩
    │       │    ├─ MODE_A (문자):  우측 공백 패딩  "VALUE     "
    │       │    ├─ MODE_AN(영숫자): 우측 공백 패딩  "VALUE     "
    │       │    └─ MODE_N (숫자):  좌측 0 패딩    "000000123"
    │       │
    │       ├─ DynamicColumn: 길이값 쓰기 + 가변데이터 쓰기
    │       │    예: "00025" + "실제 JSON 데이터..."
    │       │
    │       └─ Loop: 반복횟수 쓰기 + 내부컬럼 × N회 반복 쓰기
    │
    ├─ [3] 헤더 직렬화 (LENGTH 필드에 전체 길이 설정)
    │
    └─ [4] 헤더 + 바디 결합 → String으로 변환 → 파이프라인 전달
           → StringEncoder가 UTF-8 바이트로 인코딩 → 소켓 전송
```

---

### 9.5 에러 핸들링 흐름

```
Work.work()에서 예외 발생
    │
    ├─ throw UserDefineException (비즈니스 에러)
    │   └─ 에러코드 + 다국어 메시지 (resp_code_kor.properties / resp_code_usa.properties)
    │
    └─ 기타 Exception (시스템 에러)
    │
    ▼
WorkHandler.exceptionCaught() 또는 Codec.exceptionCaught()
    │
    ▼
ExceptionUtil.exceptionCaught(exceptionInfo, ctx, cause)
    │
    ├─ [1] 예외 타입 판별
    │       ├─ UserDefineException → RESP_CODE, RESP_MSG 설정
    │       ├─ UserDefineWPException → WorldPay 전용 에러 처리
    │       └─ 기타 → "ETC_COME" 코드 + 범용 에러 메시지
    │
    ├─ [2] listenType / RETURN_TYPE에 따라 에러 응답 포맷 결정
    │       ├─ TCP → DocumentWriter로 고정길이 응답 쓰기
    │       ├─ JSON → JSON 에러 응답 (CORS 허용)
    │       ├─ XML/DSXML → XML 에러 응답
    │       ├─ SHTML → 자동 서브밋 폼 (RETURN_URL로 에러 전달)
    │       └─ MEG → 텍스트 에러 응답
    │
    └─ [3] writeAndFlush + ChannelFutureListener.CLOSE
            → 에러 응답 전송 후 채널 닫기
```

> **핵심 포인트**: 에러가 발생해도 클라이언트는 반드시 응답을 받습니다.
> 어떤 핸들러에서 에러가 발생하든 `exceptionCaught()`가 파이프라인을 타고
> 올라가면서 에러 응답을 직접 생성해서 전송합니다.

---

### 9.6 필드 암호화/복호화 — Box.putEnc() / getEnc()

민감 데이터(카드번호, 유효기간 등)는 `Box` 클래스에서 자동 암호화/복호화됩니다:

```
암호화 (putEnc)                          복호화 (getEnc)
─────────────                           ─────────────
평문 "1234567890123456"                  Base64 인코딩된 암호문
    │                                        │
    ▼                                        ▼
KeyManager.getCurrentKeyId()             Base64 디코딩
    → 현재 활성 DEK 키 ID (2바이트)           │
    │                                        ▼
    ▼                                    바이트 분리
랜덤 IV 생성 (16바이트)                   ├─ keyId (0~2)  → DEK 조회
    │                                    ├─ IV    (2~18) → 복호화 IV
    ▼                                    └─ 암호문 (18~)  → 복호화 대상
AES/CBC/PKCS5Padding 암호화                  │
    │                                        ▼
    ▼                                    AES/CBC/PKCS5Padding 복호화
결합: keyId(2B) + IV(16B) + 암호문            │
    │                                        ▼
    ▼                                    평문 "1234567890123456"
Base64 인코딩 → GiftBox에 저장
```

**사용 예시 (PCD0100001.java에서):**

```java
// 암호화된 필드 복호화
req.put("ENC_MOID", req.get("MOID"));     // 원본값을 ENC_ 접두사 키에 저장
req.put("MOID", req.getEnc("ENC_MOID"));  // getEnc()로 복호화하여 평문으로 교체
```

> **규칙**: `putEnc()`/`getEnc()` 메서드는 키 이름에 "ENC"가 포함되어 있어야만 동작합니다.
> "ENC"가 없는 키로 호출하면 null을 반환하거나 아무 작업도 하지 않습니다.