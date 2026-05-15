package qa.cinepolis.backend.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record LogEvent(String level, String message, String time) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static LogEvent of(String level, String message) {
        return new LogEvent(level, message, LocalTime.now().format(FMT));
    }
}
