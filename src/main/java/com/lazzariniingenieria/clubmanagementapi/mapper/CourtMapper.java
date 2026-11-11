package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CourtMapper {

    CourtResponse toResponse(Court court);

    List<CourtResponse> toResponseList(List<Court> courts);
}
