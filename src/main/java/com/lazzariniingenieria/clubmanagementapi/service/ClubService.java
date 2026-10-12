package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.ClubResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateClubMonthlyFeeRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.ClubMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClubService {

    private static final int AMOUNT_SCALE = 2;

    private final ClubRepository clubRepository;
    private final ClubMapper clubMapper;

    public ClubResponse getClub(Long clubId) {
        Club club = findClubOrThrow(clubId);

        return clubMapper.toResponse(club);
    }

    @Transactional
    public ClubResponse updateMonthlyFee(AuthenticatedUser currentUser, UpdateClubMonthlyFeeRequest request) {
        Club club = findClubOrThrow(currentUser.clubId());
        BigDecimal previousAmount = club.getMonthlyFeeAmount();
        BigDecimal newAmount = normalizeAmount(request.monthlyFeeAmount());

        club.setMonthlyFeeAmount(newAmount);
        Club savedClub = clubRepository.save(club);
        log.info("Monthly fee of clubId={} changed from {} to {} by userAccountId={}", club.getId(), previousAmount, newAmount,
                currentUser.userAccountId());

        return clubMapper.toResponse(savedClub);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    private Club findClubOrThrow(Long clubId) {
        return clubRepository
                .findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException(clubId));
    }
}
