package org.example.netty_basecamp.cartracking.netty.perf;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * 파이프라인 성능 계측의 집계 + 리포트 담당.
 *
 * 동작 흐름:
 * 1. 여러 스레드에서 PipelineTrace.end() → record()가 호출됨 (데이터 수집)
 * 2. 데몬 스레드가 10초마다 report() 실행 → 수집된 데이터를 drain하여 통계 계산 → Log4j2로 출력
 *
 * 출력 예시:
 * [MQTT] 10s count=2000 | DESERIALIZE: avg=45μs p99=120μs max=250μs | DOMAIN: avg=310μs p99=800μs max=1500μs | ...
 * [REST] 10s count=500  | VT_WAIT: avg=80μs p99=300μs max=800μs | HANDLER: avg=850μs p99=2500μs max=5000μs | ...
 */
public class PipelineMetrics {

    private static final Logger perf = LogManager.getLogger("perf");
    private static final int REPORT_INTERVAL_SECONDS = 10;

    /**
     * 구간별 소요시간(나노초)을 모아두는 저장소.
     *
     * key: "파이프라인.구간" (예: "MQTT.DESERIALIZE", "MQTT.DOMAIN", "REST.HANDLER")
     * value: 해당 구간의 소요시간 목록 (나노초). 여러 요청에서 쌓인 값들.
     *
     * 예: "MQTT.DESERIALIZE" → [45000, 52000, 38000, 41000, ...] (각각 한 MQTT 메시지의 역직렬화 소요시간)
     *
     * ConcurrentLinkedQueue를 쓰는 이유:
     * - MQTT callback 스레드, Virtual Thread 등 여러 스레드에서 동시에 add() 호출
     * - ConcurrentLinkedQueue는 CAS 기반 lock-free → 스레드를 블로킹하지 않음
     * - 10초마다 report()에서 poll()로 전부 꺼내감 (drain)
     */
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> store = new ConcurrentHashMap<>();

    /**
     * 파이프라인별 구간 순서를 기억하는 저장소.
     *
     * key: 파이프라인 이름 (예: "MQTT", "REST")
     * value: 구간 이름 목록, 순서대로 (예: ["DESERIALIZE", "DOMAIN", "SERIALIZE", "BROADCAST", "TOTAL"])
     *
     * 왜 필요한가:
     * - store의 key는 "MQTT.DESERIALIZE" 같은 flat 구조라서, 어떤 구간이 어떤 파이프라인에 속하는지,
     *   어떤 순서로 출력해야 하는지 알 수 없다.
     * - report()에서 "MQTT" 파이프라인의 구간을 DESERIALIZE → DOMAIN → SERIALIZE → BROADCAST → TOTAL
     *   순서대로 출력하려면, 최초 record() 시점에 이 순서를 기억해둬야 한다.
     * - PipelineTrace의 LinkedHashMap이 순서를 보존하므로, 첫 번째 record() 호출 시
     *   그 순서를 그대로 가져와 저장한다.
     *
     * CopyOnWriteArrayList를 쓰는 이유:
     * - computeIfAbsent()에서 딱 한 번만 생성하고, 이후에는 읽기만 하므로 COW가 적합
     */
    private static final ConcurrentHashMap<String, List<String>> phaseOrders = new ConcurrentHashMap<>();

    /**
     * 10초마다 report()를 실행하는 데몬 스레드.
     * 데몬이므로 JVM 종료 시 자동으로 같이 종료된다 (shutdown 불필요).
     */
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "perf-reporter");
        t.setDaemon(true);
        return t;
    });

    // 클래스 로딩 시 스케줄 등록 — 10초 후 첫 실행, 이후 10초 간격
    static {
        scheduler.scheduleAtFixedRate(PipelineMetrics::report, REPORT_INTERVAL_SECONDS, REPORT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public static PipelineTrace startMqtt() {
        return new PipelineTrace("MQTT");
    }

    public static PipelineTrace startRest() {
        return new PipelineTrace("REST");
    }

    /**
     * PipelineTrace.end()에서 호출됨. 완료된 1건의 구간별 소요시간을 store에 쌓는다.
     *
     * @param pipeline 파이프라인 이름 (예: "MQTT")
     * @param phases   구간별 소요시간 (예: {"DESERIALIZE": 45000, "DOMAIN": 310000, ..., "TOTAL": 448000})
     *
     * 동작:
     * 1. phaseOrders에 이 파이프라인의 구간 순서를 기록 (최초 1회만)
     * 2. store에 각 구간의 소요시간을 추가
     *    예: "MQTT.DESERIALIZE" 큐에 45000 추가, "MQTT.DOMAIN" 큐에 310000 추가, ...
     */
    static void record(String pipeline, java.util.LinkedHashMap<String, Long> phases) {
        // 첫 번째 record 호출 시에만 구간 순서를 저장 (이후에는 무시됨 — computeIfAbsent)
        phaseOrders.computeIfAbsent(pipeline, k -> new CopyOnWriteArrayList<>(phases.keySet()));

        // 각 구간의 소요시간을 해당 큐에 추가
        phases.forEach((phase, nanos) ->
                store.computeIfAbsent(pipeline + "." + phase, k -> new ConcurrentLinkedQueue<>()).add(nanos)
        );
    }

    /**
     * 10초마다 실행. store에 쌓인 데이터를 꺼내서 통계를 계산하고 로그로 출력한다.
     *
     * perf logger가 OFF이면 즉시 반환 — drain/sort/계산 모두 건너뜀.
     */
    private static void report() {
        if (!perf.isDebugEnabled()) return;

        // 파이프라인별로 반복 (예: "MQTT", "REST")
        phaseOrders.forEach((pipeline, phases) -> {
            int count = 0;
            StringBuilder sb = new StringBuilder();

            // 구간 순서대로 반복 (예: DESERIALIZE → DOMAIN → SERIALIZE → BROADCAST → TOTAL)
            for (String phase : phases) {
                // store에서 해당 구간의 소요시간 큐를 가져옴
                ConcurrentLinkedQueue<Long> queue = store.get(pipeline + "." + phase);
                if (queue == null) continue;

                // 큐에서 전부 꺼냄 (drain) — 다음 10초 주기에는 새 데이터만 집계됨
                List<Long> durations = drain(queue);
                if (durations.isEmpty()) continue;

                // TOTAL 구간의 건수 = 처리된 요청 수
                if ("TOTAL".equals(phase)) {
                    count = durations.size();
                }

                // 통계 계산 (avg, p99, max)
                Stats stats = calcStats(durations);
                sb.append(String.format(" | %s: avg=%dμs p99=%dμs max=%dμs", phase, stats.avgUs, stats.p99Us, stats.maxUs));
            }

            // 데이터가 있었을 때만 로그 출력
            // 예: [MQTT] 10s count=2000 | DESERIALIZE: avg=45μs p99=120μs max=250μs | ...
            if (count > 0) {
                perf.debug("[{}] {}s count={}{}", pipeline, REPORT_INTERVAL_SECONDS, count, sb);
            }
        });
    }

    /**
     * ConcurrentLinkedQueue에서 모든 요소를 꺼내 List로 반환한다.
     * poll()은 큐에서 제거하므로, 다음 report() 주기에는 새 데이터만 남는다.
     */
    private static List<Long> drain(ConcurrentLinkedQueue<Long> queue) {
        List<Long> list = new ArrayList<>();
        Long val;
        while ((val = queue.poll()) != null) {
            list.add(val);
        }
        return list;
    }

    /**
     * 나노초 목록에서 avg, p99, max를 계산한다.
     * 정렬 후 인덱스로 p99를 구하는 간단한 방식.
     * 결과는 마이크로초(μs)로 변환하여 반환.
     */
    private static Stats calcStats(List<Long> nanos) {
        Collections.sort(nanos);
        long sum = 0;
        for (long n : nanos) sum += n;
        long avg = sum / nanos.size();
        int p99Index = Math.max(0, (int) (nanos.size() * 0.99) - 1);  // 상위 1%를 제외한 최대값
        long p99 = nanos.get(p99Index);
        long max = nanos.getLast();
        return new Stats(avg / 1000, p99 / 1000, max / 1000);  // 나노초 → 마이크로초 변환
    }

    private record Stats(long avgUs, long p99Us, long maxUs) {}
}
