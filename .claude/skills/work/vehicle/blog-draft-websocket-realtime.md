# Netty + MQTT + WebSocket: 실시간 차량 추적 시스템의 전체 동작 구조

> 이전 글에서 MQTT 브로커(HiveMQ CE)를 선정하고 Publisher/Subscriber를 연결했다.
> 이번 글에서는 **시스템 전체가 어떻게 연결되어 실시간으로 동작하는지** 정리한다.

---

## 전체 아키텍처

```
                        ┌──────────────────────────────────────────────────────┐
                        │            Netty Server (port 8081)                   │
                        │                                                      │
  ┌───────────────┐     │  ┌────────────────────────────────────────────────┐  │
  │ Vehicle       │     │  │              Netty Pipeline                     │  │
  │ Simulator     │     │  │                                                │  │
  │ (N대)         │     │  │  HttpServerCodec                               │  │
  │               │     │  │       ↓                                        │  │
  │ 5초마다       │     │  │  HttpObjectAggregator                          │  │
  │ GPS publish   │     │  │       ↓                                        │  │
  └───────┬───────┘     │  │  WebSocketServerProtocolHandler ──→ WS 핸들러  │  │
          │             │  │       ↓                                        │  │
          │ MQTT        │  │  HttpRoutingHandler ──→ Virtual Thread         │  │
          ▼             │  └────────────────────────────────────────────────┘  │
  ┌───────────────┐     │                                                      │
  │   HiveMQ CE   │     │  ┌────────────────────────────────────────────────┐  │
  │   (브로커)     │     │  │         VehicleTelemetrySubscriber             │  │
  │   port 1883   │────────▶│                                                │  │
  └───────────────┘     │  │  1. MQTT 수신 (vehicle/+/telemetry)            │  │
                        │  │  2. Journey 자동 생성 (첫 GPS)                  │  │
                        │  │  3. LocationSnapshot 저장                       │  │
                        │  │  4. WebSocket broadcast ───────────────────┐    │  │
                        │  └───────────────────────────────────────────┼────┘  │
                        │                                              │        │
                        └──────────────────────────────────────────────┼────────┘
                                                                       │
                    ┌──────────────────────────────────────────────────┘
                    │
                    ▼
          ┌───────────────────┐
          │    Dashboard      │
          │    (브라우저)       │
          │                   │
          │  WebSocket 수신    │
          │       ↓           │
          │  Leaflet.js 지도   │
          │  마커 실시간 이동   │
          └───────────────────┘
```

### 동작 흐름

서버가 시작되면 다음이 순차적으로 일어난다:

1. **서버 기동** — Netty가 8081 포트를 열고, MQTT Publisher/Subscriber 두 개의 클라이언트가 HiveMQ 브로커에 연결된다. 차량 10대가 자동 등록(seed data)된다.

2. **시뮬레이터 시작** — `POST /api/cartracking/simulator/start`를 호출하면 등록된 차량마다 Virtual Thread가 하나씩 생성된다. 각 스레드는 서울 영역 내 랜덤 경로를 생성하고, GPS 보간 좌표를 5초 간격으로 MQTT에 publish한다.

3. **브로커 중계** — HiveMQ CE가 `vehicle/{id}/telemetry` 토픽으로 들어온 메시지를 Subscriber에게 전달한다. 브로커는 단순 중계 역할이며, QoS 1(AT_LEAST_ONCE)로 최소 1회 전달을 보장한다.

4. **Subscriber 수신 및 도메인 처리** — `VehicleTelemetrySubscriber`가 메시지를 받으면:
   - 해당 차량에 진행 중인 Journey가 없으면 → Journey를 자동 생성한다 (첫 GPS = 운행 시작)
   - 진행 중인 Journey가 있으면 → LocationSnapshot을 저장한다
   - 이 과정에서 Vehicle 상태가 AVAILABLE → ON_TRIP으로 바뀐다

5. **WebSocket broadcast** — Subscriber는 수신한 telemetry JSON을 `ChannelGroup.writeAndFlush()`로 현재 연결된 모든 WebSocket 클라이언트에 전송한다. 브라우저는 별도 요청 없이 서버가 push해주는 데이터를 받기만 하면 된다.

6. **대시보드 렌더링** — 브라우저는 WebSocket으로 받은 좌표를 `requestAnimationFrame`으로 4초간 선형 보간하여 마커를 부드럽게 이동시킨다. 동시에 수신 위치마다 점(dot)을 찍어 궤적을 표시한다.

7. **시뮬레이터 종료** — `POST /api/cartracking/simulator/stop`을 호출하면 모든 시뮬레이터 스레드가 멈추고, 진행 중이던 Journey는 COMPLETED 상태로 전환된다. Vehicle도 다시 AVAILABLE로 돌아간다.

---

## 1. Netty 파이프라인: HTTP와 WebSocket을 한 포트에서 처리

```java
// CarTrackingChannelInitializer.java
@Override
protected void initChannel(Channel ch) {
    ChannelPipeline p = ch.pipeline();
    p.addLast(new HttpServerCodec());
    p.addLast(new HttpObjectAggregator(65536));
    p.addLast(new WebSocketServerProtocolHandler("/ws/vehicles"));
    p.addLast(new WebSocketFrameHandler(websocketClients));
    p.addLast(new HttpRoutingHandler(routeRegistry, virtualExecutor));
}
```

핵심은 `WebSocketServerProtocolHandler`다. 이 핸들러는 `/ws/vehicles` 경로로 들어온 HTTP Upgrade 요청만 WebSocket 핸드셰이크로 처리하고, 나머지 일반 HTTP 요청은 다음 핸들러(`HttpRoutingHandler`)로 그대로 통과시킨다. 별도 라우터 없이 한 포트에서 REST + WebSocket을 동시에 처리할 수 있는 이유다.

---

## 2. Virtual Thread 오프로드: EventLoop를 블로킹하지 않는 구조

```java
// HttpRoutingHandler.java — channelRead0()
virtualExecutor.submit(() -> {
    try {
        Object result = match.getEntry().handle(requestContext);
        sendJson(ctx, OK, result, method, path);
    } catch (IllegalArgumentException e) {
        sendJson(ctx, BAD_REQUEST, Map.of("error", e.getMessage()), method, path);
    } catch (Exception e) {
        sendJson(ctx, INTERNAL_SERVER_ERROR, Map.of("error", e.getMessage()), method, path);
    }
});
```

Netty의 EventLoop 스레드는 I/O 전용이다. 여기서 블로킹(DB 조회, 락 대기 등)이 발생하면 다른 모든 연결의 read/write가 멈춘다.

해결: 요청 파싱까지는 EventLoop에서 하고, 도메인 로직은 Virtual Thread로 넘긴다. Java 21의 Virtual Thread는 생성 비용이 거의 없어서 요청마다 하나씩 만들어도 부담이 없다.

**응답은 다시 EventLoop로 돌려보낸다:**

```java
// HttpRoutingHandler.java — sendJson()
ctx.channel().eventLoop().execute(() ->
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
);
```

Netty Channel의 write는 반드시 해당 Channel의 EventLoop에서 실행해야 thread-safe하다. Virtual Thread에서 직접 writeAndFlush를 호출하면 응답이 유실될 수 있다.

---

## 3. MQTT Subscriber: GPS 수신 → Journey 자동 생성 → WebSocket broadcast

```java
// VehicleTelemetrySubscriber.java — subscribe()
client.subscribeWith()
    .topicFilter("vehicle/+/telemetry")
    .qos(MqttQos.AT_LEAST_ONCE)
    .callback(publish -> {
        byte[] payload = publish.getPayloadAsBytes();
        TelemetryPayload telemetry = objectMapper.readValue(payload, TelemetryPayload.class);

        Location location = Location.of(telemetry.latitude(), telemetry.longitude());
        LocationSnapshot snapshot = tripApplicationService.recordSnapshot(telemetry.vehicleId(), location);

        // 진행 중인 Journey가 없으면 자동 생성
        if (snapshot == null) {
            tripApplicationService.startTrip(telemetry.vehicleId(), location);
        }

        // WebSocket 연결된 모든 브라우저에 broadcast
        String json = objectMapper.writeValueAsString(telemetry);
        websocketClients.writeAndFlush(new TextWebSocketFrame(json));
    })
    .send();
```

실제 세계에서 운행은 차량이 GPS를 보내기 시작하는 순간 시작된다. 서버가 REST API로 "운행 시작"을 명령하는 것은 현실과 반대다. 그래서 첫 GPS 수신 시 Journey를 자동으로 생성하는 구조를 택했다.

`websocketClients`는 Netty의 `ChannelGroup`으로, 현재 연결된 모든 WebSocket 채널을 관리한다. `writeAndFlush`를 호출하면 그룹 내 전체 채널에 메시지가 전송된다.

---

## 4. WebSocket 연결 관리

```java
// WebSocketFrameHandler.java
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final ChannelGroup websocketClients;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        websocketClients.add(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 서버→브라우저 단방향 — 브라우저에서 보내는 메시지는 무시
    }
}
```

브라우저가 `ws://localhost:8081/ws/vehicles`로 연결하면 `handlerAdded`가 호출되어 ChannelGroup에 추가된다. 연결이 끊기면 Netty가 자동으로 그룹에서 제거한다.

이 시스템은 서버→브라우저 단방향 push다. 브라우저는 "구독"만 하고, 서버가 GPS를 수신할 때마다 알아서 보내준다.

---

## 5. 대시보드: WebSocket 수신 → 마커 애니메이션

```javascript
// dashboard.html
ws.onmessage = (event) => {
    const t = JSON.parse(event.data);
    const latlng = [parseFloat(t.latitude), parseFloat(t.longitude)];

    if (markers[t.vehicleId]) {
        // 기존 마커를 4초간 부드럽게 이동 (시뮬레이터 5초 간격에 맞춤)
        animateMarker(markers[t.vehicleId], latlng, 4000);
    }
};

function animateMarker(marker, targetLatLng, durationMs) {
    const start = marker.getLatLng();
    const startTime = performance.now();
    function step(now) {
        const t = Math.min((now - startTime) / durationMs, 1);
        const lat = start.lat + (targetLatLng[0] - start.lat) * t;
        const lng = start.lng + (targetLatLng[1] - start.lng) * t;
        marker.setLatLng([lat, lng]);
        if (t < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
}
```

GPS가 5초마다 오는데, 마커가 순간이동하면 자연스럽지 않다. `requestAnimationFrame`으로 4초간 선형 보간하여 부드럽게 이동시킨다. 5초보다 짧은 4초로 설정해서 다음 GPS가 오기 전에 애니메이션이 완료되도록 했다.

---

## 6. 서버 부트스트랩: 전체 조립

```java
// CarTrackingServer.java
public void start() throws InterruptedException {
    ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    EventLoopGroup bossGroup   = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(4, NioIoHandler.newFactory());

    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .option(ChannelOption.SO_BACKLOG, 128)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childHandler(new CarTrackingChannelInitializer(routeRegistry, virtualExecutor, websocketClients));

    ChannelFuture f = b.bind(port).sync();
    f.channel().closeFuture().sync();
}
```

| 설정 | 의미 |
|------|------|
| Boss 1 thread | 클라이언트 연결 수락 전담 |
| Worker 4 threads | 연결된 채널의 read/write 처리 |
| SO_BACKLOG 128 | OS TCP 연결 대기 큐 크기 |
| TCP_NODELAY | Nagle 알고리즘 비활성화 — 작은 패킷도 즉시 전송 |
| SO_KEEPALIVE | TCP 레벨 연결 유지 확인 |

---

## 7. 전체 흐름 요약

```
1. 서버 시작 → 차량 10대 자동 등록 (seed data)
2. MQTT Publisher/Subscriber 연결
3. 대시보드 접속 → WebSocket 연결 → ChannelGroup에 추가
4. 시뮬레이터 시작 (POST /api/cartracking/simulator/start)
5. 각 차량이 5초마다 GPS publish → HiveMQ → Subscriber 수신
6. Subscriber:
   - 첫 GPS → Journey 자동 생성
   - 이후 GPS → LocationSnapshot 저장
   - 매번 → WebSocket broadcast
7. 브라우저: GPS 수신 → 마커 애니메이션으로 실시간 이동 표시
8. 시뮬레이터 종료 → 전체 Journey COMPLETED 처리
```

---

## 다음 단계: Netty 부하 테스트

현재 시스템은 10대 차량으로 잘 동작한다. 하지만 이것만으로는 Netty의 성능 한계를 알 수 없다.

다음 글에서는 JMeter로 부하를 걸어 병목점을 찾고 개선할 예정이다:
- REST API에 동시 500~1000 요청 → TPS와 응답시간 측정
- WebSocket 1000개 연결 상태에서 broadcast 성능 측정
- 발견된 병목을 하나씩 개선하고 Before/After 비교

현재 코드에서 이미 예상되는 병목:
- HTTP Keep-Alive 미지원 (매 요청마다 TCP 연결/종료)
- WebSocket broadcast 시 같은 메시지를 N번 새로 생성
- Subscriber가 단일 스레드에서 도메인 로직 + broadcast를 동기 처리

---

## 핵심 파일 목록

| 파일 | 역할 |
|------|------|
| `CarTrackingServer.java` | Netty 서버 부트스트랩 |
| `CarTrackingChannelInitializer.java` | 파이프라인 구성 |
| `HttpRoutingHandler.java` | REST 요청 → Virtual Thread → 응답 |
| `WebSocketFrameHandler.java` | WebSocket 연결 관리 |
| `VehicleTelemetrySubscriber.java` | MQTT 수신 → 도메인 처리 → broadcast |
| `VehicleSimulator.java` | GPS 보간 + MQTT publish 루프 |
| `SimulatorBootstrap.java` | 시뮬레이터 생명주기 관리 |
| `CarTrackingAppConfig.java` | DI 조립 (Repository, Service, MQTT 클라이언트) |
| `dashboard.html` | Leaflet.js 지도 + WebSocket 실시간 수신 |
