package io.workm8.agui4j.example.tools;

import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.util.function.Function;

public class CurrentDateTime implements Function<Void, String> {
    @Override
    public String apply(Void unused) {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
