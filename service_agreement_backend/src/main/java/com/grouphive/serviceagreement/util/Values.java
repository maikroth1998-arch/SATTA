package com.grouphive.serviceagreement.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

public final class Values {
  private static final BigDecimal FOUR = new BigDecimal("4");

  private Values() {
  }

  public static String clean(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  public static BigDecimal decimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }

    String text = String.valueOf(value).trim();
    if (text.isEmpty() || "unlimited".equalsIgnoreCase(text)) {
      return BigDecimal.ZERO;
    }

    text = text.replaceAll("\\s+", "");
    if (text.contains(",") && text.contains(".")) {
      text = text.replace(".", "").replace(",", ".");
    } else if (text.contains(",")) {
      text = text.replace(",", ".");
    }

    var matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(text);
    if (!matcher.find()) {
      return BigDecimal.ZERO;
    }

    return new BigDecimal(matcher.group()).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal quarter(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return value.multiply(FOUR)
        .setScale(0, RoundingMode.HALF_UP)
        .divide(FOUR, 2, RoundingMode.HALF_UP);
  }

  public static boolean isQuarterStep(BigDecimal value) {
    if (value == null) {
      return false;
    }
    return value.multiply(FOUR).stripTrailingZeros().scale() <= 0;
  }

  public static Instant instant(String value) {
    String text = clean(value);
    if (text.isEmpty()) {
      return null;
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  public static String monthYear(Object value) {
    String text = clean(value);
    if (text.isEmpty()) {
      return "";
    }

    Instant instant = instant(text);
    if (instant != null) {
      YearMonth ym = YearMonth.from(instant.atZone(ZoneOffset.UTC));
      return ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.US) + " " + ym.getYear();
    }

    try {
      var formatter = new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .appendPattern("MMM uuuu")
          .toFormatter(Locale.US);
      YearMonth ym = YearMonth.parse(text, formatter);
      return ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.US) + " " + ym.getYear();
    } catch (DateTimeParseException ignored) {
      return text;
    }
  }

  public static String iso(Instant instant) {
    return instant == null ? "" : DateTimeFormatter.ISO_INSTANT.format(instant);
  }
}
