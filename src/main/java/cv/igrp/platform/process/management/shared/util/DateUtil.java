package cv.igrp.platform.process.management.shared.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

public class DateUtil { // todo class to be removed

  private DateUtil (){}

  private static final String HH_MM_PATTERN = "HH:mm";

  private static final String DD_MM_YYYY_PATTERN = "dd-MM-yyyy";

  private static final String DD_MM_YYYY_HH_MM_PATTERN = "dd-MM-yyyy - HH:mm";

  public static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern(HH_MM_PATTERN);

  public static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern(DD_MM_YYYY_PATTERN);

  public static final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern(DD_MM_YYYY_HH_MM_PATTERN);

  public static final Function<LocalTime, String> localTimeToString = obj -> ofNullable(obj).map(HH_MM::format).orElse("");

  public static final Function<String, LocalTime> stringToLocalTime = obj -> ofNullable(obj).filter(s->!s.isBlank()).map(t->LocalTime.parse(t,HH_MM)).orElse(null);

  public static final Function<String, LocalDate> stringToLocalDate = obj -> ofNullable(obj).filter(s->!s.isBlank()).map(t->LocalDate.parse(t,DD_MM_YYYY)).orElse(null);

  public static final Function<String, LocalDateTime> stringToLocalDateTime = obj -> ofNullable(obj).filter(s->!s.isBlank()).map(t->LocalDateTime.parse(t,DD_MM_YYYY_HH_MM)).orElse(null);

  public static final Function<LocalDate, String> localDateToString = obj -> ofNullable(obj).map(DD_MM_YYYY::format).orElse("");

  public static final Function<LocalDate, String> dateToFullString = date -> ofNullable(date).map(d->d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(new Locale("pt", "PT")))).orElse("");

  public static final Function<LocalDateTime, String> localDateTimeToString = obj -> ofNullable(obj).map(DD_MM_YYYY_HH_MM::format).orElse("");

  public static final Function<Date, LocalDateTime> utilDateToLocalDateTime = date -> ofNullable(date).map(d->d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()).orElse(null);

  public static final BiFunction<LocalDateTime, DateTimeFormatter, String> biLocalDateTimeToString = (obj, formatter) -> ofNullable(obj).map(formatter::format).orElse("");

  public static final BiFunction<LocalDate, DateTimeFormatter, String> biLocalDateToString = (obj, formatter) -> ofNullable(obj).map(formatter::format).orElse("");
  /** The zone the process timeline and the failed-jobs console show times in. */
  public static final ZoneId CAPE_VERDE = ZoneId.of("Atlantic/Cape_Verde");

  public static LocalDateTime toCapeVerdeTime(java.time.Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, CAPE_VERDE);
  }

}
