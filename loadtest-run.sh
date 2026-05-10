#!/bin/bash

JMETER="D:/apache-jmeter-5.6.3/bin/jmeter.bat"
SERVER_URL="http://localhost:8081"
JMX_FILE="D:/apache-jmeter-5.6.3/result_cartracking/cartracking_test.jmx"
RESULT_DIR="D:/apache-jmeter-5.6.3/result_cartracking"
WAIT_SEC=10

mkdir -p "$RESULT_DIR"

run_stage() {
  local stage=$1
  local count=$2
  local restThreads=$3
  local duration=$4

  echo "===== $stage: 차량 ${count}대 / REST ${restThreads}t / ${duration}s ====="

  # 시뮬레이터 시작
  curl -s -X POST "${SERVER_URL}/api/cartracking/simulator/start?count=${count}"
  echo ""
  echo "${WAIT_SEC}초 대기 (워밍업)..."
  sleep $WAIT_SEC

  # 이전 결과 삭제
  rm -f "${RESULT_DIR}/${stage}.jtl"
  rm -rf "${RESULT_DIR}/${stage}/"

  # JMeter 실행
  echo "JMeter 실행 중..."
  "$JMETER" -n -t "$JMX_FILE" \
    -JrestThreads=$restThreads \
    -Jduration=$duration \
    -l "${RESULT_DIR}/${stage}.jtl" -e -o "${RESULT_DIR}/${stage}/"

  # 시뮬레이터 중지
  curl -s -X POST "${SERVER_URL}/api/cartracking/simulator/stop"
  echo ""
  echo "===== $stage 완료. 서버 재시작 후 Enter 눌러 ====="
  read -r
}

#        stage  count  restThreads  duration(s)
run_stage "S1"   10     10           60
run_stage "S2"   50     30           60
run_stage "S3"   100    50           60
run_stage "S4"   500    50           60
run_stage "S5"   1000   50           60

echo "===== 전체 완료 ====="
