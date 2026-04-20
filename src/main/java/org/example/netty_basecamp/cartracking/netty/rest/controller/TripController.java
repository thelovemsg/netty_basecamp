package org.example.netty_basecamp.cartracking.netty.rest.controller;

import org.example.netty_basecamp.cartracking.netty.rest.dto.LocationRequest;
import org.example.netty_basecamp.cartracking.netty.rest.dto.ScheduleTripRequest;
import org.example.netty_basecamp.cartracking.netty.rest.route.RequestContext;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;

public class TripController {

    private final TripApplicationService tripService;

    public TripController(TripApplicationService tripService) {
        this.tripService = tripService;
    }

    /** POST /api/cartracking/trips — 운행 배차 */
    public Object schedule(RequestContext ctx) {
        ScheduleTripRequest req = ctx.readBody(ScheduleTripRequest.class);
        return tripService.scheduleTrip(
                req.vehicleId(),
                Location.of(req.originLat(), req.originLng()),
                Location.of(req.destLat(), req.destLng()));
    }

    /** POST /api/cartracking/trips/{vehicleId}/depart — 출발 */
    public Object depart(RequestContext ctx) {
        return tripService.departTrip(ctx.pathVariableAsLong("vehicleId"));
    }

    /** POST /api/cartracking/trips/{vehicleId}/snapshots — 위치 스냅샷 기록 */
    public Object snapshot(RequestContext ctx) {
        LocationRequest req = ctx.readBody(LocationRequest.class);
        return tripService.recordSnapshot(
                ctx.pathVariableAsLong("vehicleId"),
                Location.of(req.lat(), req.lng()));
    }

    /** POST /api/cartracking/trips/{vehicleId}/complete — 운행 완료 */
    public Object complete(RequestContext ctx) {
        return tripService.completeTrip(ctx.pathVariableAsLong("vehicleId"));
    }

    /** GET /api/cartracking/trips/{journeyId}/route — 운행 경로 조회 */
    public Object route(RequestContext ctx) {
        return tripService.getTripRoute(ctx.pathVariableAsLong("journeyId"));
    }
}
