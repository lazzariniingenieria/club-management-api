package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.FamilyGroupResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.FamilyGroup;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FamilyGroupMapper {

    FamilyGroupResponse toResponse(FamilyGroup familyGroup);

    List<FamilyGroupResponse> toResponseList(List<FamilyGroup> familyGroups);
}
