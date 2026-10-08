package com.anyujia.anyujiacommoncore.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 时间戳工具类。
 *
 * 时间戳本身不包含时区信息，本工具类在计算天、月、年时统一使用 UTC 时区，避免服务器本地时区不同导致结果不一致。
 * 参数传入正数表示获取未来时间戳，传入负数表示获取过去时间戳。
 */
public final class TimestampUtil {

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    private TimestampUtil() {
    }

    /**
     * 获取当前时间戳，单位为秒。
     *
     * @return 当前秒级时间戳
     */
    public static long getCurrentSeconds() {
        return Instant.now().getEpochSecond();
    }

    /**
     * 获取当前时间戳，单位为毫秒。
     *
     * @return 当前毫秒级时间戳
     */
    public static long getCurrentMillis() {
        return Instant.now().toEpochMilli();
    }

    /**
     * 获取当前时间加指定秒数后的秒级时间戳。
     *
     * @param seconds 增加的秒数；正数表示未来，负数表示过去
     * @return 秒级时间戳
     */
    public static long getSecondsLaterSeconds(long seconds) {
        return Instant.now().plusSeconds(seconds).getEpochSecond();
    }

    /**
     * 获取当前时间加指定秒数后的毫秒级时间戳。
     *
     * @param seconds 增加的秒数；正数表示未来，负数表示过去
     * @return 毫秒级时间戳
     */
    public static long getSecondsLaterMillis(long seconds) {
        return Instant.now().plusSeconds(seconds).toEpochMilli();
    }

    /**
     * 获取当前时间加指定天数后的秒级时间戳。
     *
     * @param days 增加的天数；正数表示未来，负数表示过去
     * @return 秒级时间戳
     */
    public static long getDaysLaterSeconds(long days) {
        ZonedDateTime daysLater = ZonedDateTime.now(UTC_ZONE_ID).plusDays(days);
        return daysLater.toEpochSecond();
    }

    /**
     * 获取当前时间加指定天数后的毫秒级时间戳。
     *
     * @param days 增加的天数；正数表示未来，负数表示过去
     * @return 毫秒级时间戳
     */
    public static long getDaysLaterMillis(long days) {
        ZonedDateTime daysLater = ZonedDateTime.now(UTC_ZONE_ID).plusDays(days);
        return daysLater.toInstant().toEpochMilli();
    }

    /**
     * 获取当前时间加指定月数后的秒级时间戳。
     *
     * 月份长度不是固定天数，该方法按 UTC 日历月计算，例如 1 月 31 日加 1 个月会按 Java 时间规则得到 2 月最后一天。
     *
     * @param months 增加的月数；正数表示未来，负数表示过去
     * @return 秒级时间戳
     */
    public static long getMonthsLaterSeconds(long months) {
        ZonedDateTime monthsLater = ZonedDateTime.now(UTC_ZONE_ID).plusMonths(months);
        return monthsLater.toEpochSecond();
    }

    /**
     * 获取当前时间加指定月数后的毫秒级时间戳。
     *
     * 月份长度不是固定天数，该方法按 UTC 日历月计算，例如 1 月 31 日加 1 个月会按 Java 时间规则得到 2 月最后一天。
     *
     * @param months 增加的月数；正数表示未来，负数表示过去
     * @return 毫秒级时间戳
     */
    public static long getMonthsLaterMillis(long months) {
        ZonedDateTime monthsLater = ZonedDateTime.now(UTC_ZONE_ID).plusMonths(months);
        return monthsLater.toInstant().toEpochMilli();
    }

    /**
     * 获取当前时间加指定年数后的秒级时间戳。
     *
     * 年份按 UTC 日历年计算。闰年日期会按 Java 时间规则处理，例如 2 月 29 日加 1 年会得到下一年的 2 月 28 日。
     *
     * @param years 增加的年数；正数表示未来，负数表示过去
     * @return 秒级时间戳
     */
    public static long getYearLaterSeconds(long years) {
        ZonedDateTime yearLater = ZonedDateTime.now(UTC_ZONE_ID).plusYears(years);
        return yearLater.toEpochSecond();
    }

    /**
     * 获取当前时间加指定年数后的毫秒级时间戳。
     *
     * 年份按 UTC 日历年计算。闰年日期会按 Java 时间规则处理，例如 2 月 29 日加 1 年会得到下一年的 2 月 28 日。
     *
     * @param years 增加的年数；正数表示未来，负数表示过去
     * @return 毫秒级时间戳
     */
    public static long getYearLaterMillis(long years) {
        ZonedDateTime yearLater = ZonedDateTime.now(UTC_ZONE_ID).plusYears(years);
        return yearLater.toInstant().toEpochMilli();
    }

    /**
     * 计算两个毫秒级时间戳之间的差异。
     *
     * 返回值等于 timestamp2 - timestamp1。timestamp2 大于 timestamp1 时返回正数，否则返回负数。
     *
     * @param timestamp1 第一个毫秒级时间戳
     * @param timestamp2 第二个毫秒级时间戳
     * @return 两个时间戳之间相差的毫秒数
     */
    public static long calculateDifferenceMillis(long timestamp1, long timestamp2) {
        return ChronoUnit.MILLIS.between(Instant.ofEpochMilli(timestamp1), Instant.ofEpochMilli(timestamp2));
    }

    /**
     * 计算两个秒级时间戳之间的差异。
     *
     * 返回值等于 timestamp2 - timestamp1。timestamp2 大于 timestamp1 时返回正数，否则返回负数。
     *
     * @param timestamp1 第一个秒级时间戳
     * @param timestamp2 第二个秒级时间戳
     * @return 两个时间戳之间相差的秒数
     */
    public static long calculateDifferenceSeconds(long timestamp1, long timestamp2) {
        return ChronoUnit.SECONDS.between(Instant.ofEpochSecond(timestamp1), Instant.ofEpochSecond(timestamp2));
    }
}
