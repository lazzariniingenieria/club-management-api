package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    ReservationResponse toResponse(Reservation reservation, String description);
}
