package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.MemberAccountResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MemberAccountMapper {

    MemberAccountResponse toResponse(UserAccount userAccount);
}
