package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.UserSummaryDto;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAccountMapper {

    UserSummaryDto toSummaryDto(UserAccount userAccount);
}
