package org.example.netty_basecamp.cartracking.netty.perf;

import java.util.LinkedHashMap;

/**
 * 요청 1건의 파이프라인 구간별 소요시간을 기록하는 스톱워치.
 *
 * 사용 예 (MQTT 파이프라인):
 *   PipelineTrace trace = PipelineMetrics.start("MQTT");
 *   // ... 역직렬화 ...
 *   trace.mark("DESERIALIZE");    // start ~ 여기까지 소요시간 기록
 *   // ... 도메인 로직 ...
 *   trace.mark("DOMAIN");         // DESERIALIZE ~ 여기까지 소요시간 기록
 *   // ... broadcast ...
 *   trace.end("BROADCAST");       // DOMAIN ~ 여기까지 기록 + TOTAL 계산 + PipelineMetrics에 제출
 *
 * 내부적으로 System.nanoTime()을 사용하지만, 호출부에서는 phase 이름만 넘기면 된다.
 * LinkedHashMap을 사용하여 mark() 호출 순서가 보존된다.
 */
public class PipelineTrace {

    // 이 trace가 속한 파이프라인 이름 (예: "MQTT", "REST")
    private final String pipeline;

    // trace 시작 시각 — TOTAL 계산에 사용
    private final long startNanos = System.nanoTime();

    // 마지막 mark() 시점 — 다음 mark()와의 차이가 해당 구간의 소요시간
    private long lastNanos = startNanos;

    // 구간별 소요시간 (나노초). LinkedHashMap이라 삽입 순서 보존.
    // 예: {"DESERIALIZE": 45000, "DOMAIN": 310000, "SERIALIZE": 28000}
    private final LinkedHashMap<String, Long> phases = new LinkedHashMap<>();

    PipelineTrace(String pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * 구간 하나를 기록한다.
     * "이전 mark()부터 지금까지"의 소요시간을 phase 이름으로 저장.
     *
     * 예: mark("DESERIALIZE") → startNanos ~ 지금 = 역직렬화 소요시간
     *     mark("DOMAIN")      → DESERIALIZE 끝 ~ 지금 = 도메인 로직 소요시간
     */
    public PipelineTrace mark(String phase) {
        long now = System.nanoTime();
        phases.put(phase, now - lastNanos);  // 이전 시점부터 지금까지의 차이 = 이 구간의 소요시간
        lastNanos = now;                      // 다음 구간의 시작점을 현재로 갱신
        return this;
    }

    /**
     * 마지막 구간을 기록하고, TOTAL을 계산하고, PipelineMetrics에 제출한다.
     * end() 이후 이 trace 객체는 더 이상 사용하지 않는다.
     */
    public void end(String lastPhase) {
        mark(lastPhase);                                    // 마지막 구간 기록
        phases.put("TOTAL", System.nanoTime() - startNanos); // 전체 소요시간 = 지금 - 맨 처음
        PipelineMetrics.record(pipeline, phases);            // 집계 대상에 제출
    }
}
