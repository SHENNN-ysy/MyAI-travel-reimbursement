package com.aidemo.myaitravelreimbursement.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期工具类
 */
public class DateUtils {

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter FILENAME_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : null;
    }

    public static String formatForFilename(LocalDate date) {
        return date != null ? date.format(FILENAME_DATE_FORMATTER) : null;
    }

    public static LocalDate parseDate(String dateStr) {
        return dateStr != null && !dateStr.isEmpty() ? LocalDate.parse(dateStr, DATE_FORMATTER) : null;
    }
}
