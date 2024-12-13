package ru.aasmc.ratelimiterdemo.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimestampRange {

    private OffsetDateTime start;
    private OffsetDateTime end;

    @Override
    public String toString() {
        return "[" + start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) +
                "," + end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + ")";
    }

}
