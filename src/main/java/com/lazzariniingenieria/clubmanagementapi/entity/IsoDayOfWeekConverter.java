package com.lazzariniingenieria.clubmanagementapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

@Converter
public class IsoDayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            return null;
        }

        return (short) dayOfWeek.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short isoDayNumber) {
        if (isoDayNumber == null) {
            return null;
        }

        return DayOfWeek.of(isoDayNumber);
    }
}
