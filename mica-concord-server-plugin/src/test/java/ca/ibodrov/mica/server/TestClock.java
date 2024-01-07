package ca.ibodrov.mica.server;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static java.util.Objects.requireNonNull;

public class TestClock extends Clock {

    private Instant instant;
    private ZoneId zoneId;

    public static TestClock fixed(Instant instant, ZoneId zoneId) {
        return new TestClock(instant, zoneId);
    }

    public TestClock(Instant instant, ZoneId zoneId) {
        this.instant = requireNonNull(instant);
        this.zoneId = requireNonNull(zoneId);
    }

    @Override
    public ZoneId getZone() {
        return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }
}
