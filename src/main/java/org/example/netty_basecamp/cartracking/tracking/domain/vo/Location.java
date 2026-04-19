package org.example.netty_basecamp.cartracking.tracking.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class Location {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal LATITUDE_MIN = BigDecimal.valueOf(-90);
    private static final BigDecimal LATITUDE_MAX = BigDecimal.valueOf(90);
    private static final BigDecimal LONGITUDE_MIN = BigDecimal.valueOf(-180);
    private static final BigDecimal LONGITUDE_MAX = BigDecimal.valueOf(180);

    private final BigDecimal latitude;
    private final BigDecimal longitude;

    private Location(BigDecimal latitude, BigDecimal longitude) {
        if (latitude.compareTo(LATITUDE_MIN) < 0 || latitude.compareTo(LATITUDE_MAX) > 0) {
            throw new IllegalArgumentException("위도는 -90 ~ 90 범위여야 합니다.");
        }
        if (longitude.compareTo(LONGITUDE_MIN) < 0 || longitude.compareTo(LONGITUDE_MAX) > 0) {
            throw new IllegalArgumentException("경도는 -180 ~ 180 범위여야 합니다.");
        }
        this.latitude = latitude.setScale(SCALE, ROUNDING);
        this.longitude = longitude.setScale(SCALE, ROUNDING);
    }

    public static Location of(double lat, double lng) {
        return new Location(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
    }

    public static Location of(BigDecimal lat, BigDecimal lng) {
        return new Location(lat, lng);
    }

    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(latitude, location.latitude)
                && Objects.equals(longitude, location.longitude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }
}
