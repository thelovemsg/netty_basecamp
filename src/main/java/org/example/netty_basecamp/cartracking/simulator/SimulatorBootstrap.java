package org.example.netty_basecamp.cartracking.simulator;

import org.example.netty_basecamp.cartracking.mqtt.TelemetryPublisher;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;
import org.example.netty_basecamp.cartracking.vehicle.domain.Vehicle;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimulatorBootstrap {

    private static final Logger log = LogManager.getLogger(SimulatorBootstrap.class);

    private final VehicleRepository vehicleRepository;
    private final TelemetryPublisher publisher;
    private final TripApplicationService tripService;
    private ExecutorService virtualExecutor;
    private final List<VehicleSimulator> simulators = new ArrayList<>();
    private boolean started = false;

    public SimulatorBootstrap(VehicleRepository vehicleRepository, TelemetryPublisher publisher,
                              TripApplicationService tripService) {
        this.vehicleRepository = vehicleRepository;
        this.publisher = publisher;
        this.tripService = tripService;
    }

    public void start() {
        if (started) {
            throw new IllegalStateException("시뮬레이터가 이미 실행 중입니다.");
        }
        List<Vehicle> vehicles = vehicleRepository.findAll();
        if (vehicles.isEmpty()) {
            throw new IllegalStateException("등록된 차량이 없습니다. 차량을 먼저 등록해주세요.");
        }

        virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        for (Vehicle v : vehicles) {
            VehicleSimulator sim = new VehicleSimulator(
                    v.getId(), new SeoulRouteGenerator(), new GpsInterpolator(),
                    publisher, 5000L
            );
            simulators.add(sim);
            virtualExecutor.submit(sim);
        }
        started = true;
        log.info("시뮬레이터 시작 — 차량 {}대", vehicles.size());
    }

    public void stop() {
        simulators.forEach(VehicleSimulator::stop);
        virtualExecutor.shutdown();

        // 각 차량의 진행 중인 운행을 완료 처리
        for (VehicleSimulator sim : simulators) {
            try {
                tripService.completeTrip(sim.getVehicleId());
                log.info("운행 완료 처리 [vehicleId={}]", sim.getVehicleId());
            } catch (IllegalStateException e) {
                log.debug("운행 완료 건너뜀 [vehicleId={}]: {}", sim.getVehicleId(), e.getMessage());
            }
        }

        simulators.clear();
        started = false;
        log.info("시뮬레이터 종료 — 전체 운행 완료 처리됨");
    }

    public boolean isStarted() {
        return started;
    }
}
