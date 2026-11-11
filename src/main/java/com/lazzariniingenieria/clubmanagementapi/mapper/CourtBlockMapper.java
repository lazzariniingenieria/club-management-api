package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CourtBlockMapper {

    CourtBlockResponse toResponse(CourtBlock courtBlock);

    List<CourtBlockResponse> toResponseList(List<CourtBlock> courtBlocks);
}
