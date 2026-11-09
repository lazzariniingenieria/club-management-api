package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    ReservationResponse toResponse(Reservation reservation);

    List<ReservationResponse> toResponseList(List<Reservation> reservations);
}
