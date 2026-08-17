package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.AdminResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminMapper {

    AdminResponse toResponse(UserAccount userAccount);
}
