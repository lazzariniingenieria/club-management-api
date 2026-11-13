package com.lazzariniingenieria.clubmanagementapi.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

class IsoDayOfWeekConverterTest {

    private final IsoDayOfWeekConverter converter = new IsoDayOfWeekConverter();

    @Test
    void shouldStoreMondayAsOne() {
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.MONDAY)).isEqualTo((short) 1);
    }

    @Test
    void shouldStoreSundayAsSeven() {
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.SUNDAY)).isEqualTo((short) 7);
    }

    @Test
    void shouldReadTheIsoNumberBackAsTheSameDay() {
        assertThat(converter.convertToEntityAttribute((short) 2)).isEqualTo(DayOfWeek.TUESDAY);
    }

    @Test
    void shouldKeepNullAsNullBothWays() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
