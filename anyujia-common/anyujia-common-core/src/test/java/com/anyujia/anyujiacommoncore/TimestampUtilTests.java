package com.anyujia.anyujiacommoncore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.anyujia.anyujiacommoncore.utils.TimestampUtil;
import org.junit.jupiter.api.Test;

public class TimestampUtilTests {

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    @Test
    void getCurrentSecondsShouldReturnCurrentSecondTimestamp() {
        long before = Instant.now().getEpochSecond();
        long current = TimestampUtil.getCurrentSeconds();
        long after = Instant.now().getEpochSecond();

        assertThat(current).isBetween(before, after);
    }

    @Test
    void getCurrentMillisShouldReturnCurrentMillisecondTimestamp() {
        long before = Instant.now().toEpochMilli();
        long current = TimestampUtil.getCurrentMillis();
        long after = Instant.now().toEpochMilli();

        assertThat(current).isBetween(before, after);
    }

    @Test
    void getSecondsLaterShouldAddSeconds() {
        long secondsLater = TimestampUtil.getSecondsLaterSeconds(60);
        long millisLater = TimestampUtil.getSecondsLaterMillis(60);

        assertThat(secondsLater - TimestampUtil.getCurrentSeconds()).isBetween(59L, 60L);
        assertThat(millisLater - TimestampUtil.getCurrentMillis()).isBetween(59_000L, 60_000L);
    }

    @Test
    void getDaysLaterShouldAddDays() {
        long expectedSeconds = ZonedDateTime.now(UTC_ZONE_ID).plusDays(1).toEpochSecond();
        long actualSeconds = TimestampUtil.getDaysLaterSeconds(1);

        assertThat(actualSeconds).isBetween(expectedSeconds - 1, expectedSeconds + 1);
    }

    @Test
    void getMonthsLaterShouldAddMonths() {
        long expectedSeconds = ZonedDateTime.now(UTC_ZONE_ID).plusMonths(1).toEpochSecond();
        long actualSeconds = TimestampUtil.getMonthsLaterSeconds(1);

        assertThat(actualSeconds).isBetween(expectedSeconds - 1, expectedSeconds + 1);
    }

    @Test
    void getYearLaterShouldAddYears() {
        long expectedSeconds = ZonedDateTime.now(UTC_ZONE_ID).plusYears(1).toEpochSecond();
        long actualSeconds = TimestampUtil.getYearLaterSeconds(1);

        assertThat(actualSeconds).isBetween(expectedSeconds - 1, expectedSeconds + 1);
    }

    @Test
    void calculateDifferenceShouldReturnTimestampDifference() {
        assertThat(TimestampUtil.calculateDifferenceMillis(1_000L, 2_500L)).isEqualTo(1_500L);
        assertThat(TimestampUtil.calculateDifferenceSeconds(10L, 25L)).isEqualTo(15L);
        assertThat(TimestampUtil.calculateDifferenceSeconds(25L, 10L)).isEqualTo(-15L);
    }
}
