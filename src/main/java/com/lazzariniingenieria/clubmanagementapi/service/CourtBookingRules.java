package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidReservationException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourtBookingRules {

    private final ClubRepository clubRepository;
    private final CourtRepository courtRepository;
    private final MemberRepository memberRepository;

    public void validateCourtsEnabled(Long clubId) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new ClubNotFoundException(clubId));

        if (!club.isCourtsEnabled()) {
            throw new InvalidReservationException("courts are not enabled for club " + clubId);
        }
    }

    public Court lockCourtOrThrow(Long clubId, Long courtId) {
        return courtRepository.findByIdAndClubIdForUpdate(courtId, clubId).orElseThrow(() -> new CourtNotFoundException(courtId));
    }

    public Court lockActiveCourtOrThrow(Long clubId, Long courtId) {
        Court court = lockCourtOrThrow(clubId, courtId);

        if (!court.isActive()) {
            throw new InvalidReservationException("court " + courtId + " is not active");
        }

        return court;
    }

    public void validateMemberIsActive(Long clubId, Long memberId) {
        Member member = memberRepository.findByIdAndClubId(memberId, clubId).orElseThrow(() -> new MemberNotFoundException(memberId));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new InvalidReservationException("member " + memberId + " is not active");
        }
    }

    public void validateFitsCourtHours(Court court, LocalTime startTime, LocalTime endTime) {
        boolean endsAfterStarts = endTime.isAfter(startTime);
        boolean withinCourtHours = !startTime.isBefore(court.getOpeningTime()) && !endTime.isAfter(court.getClosingTime());

        if (!endsAfterStarts || !withinCourtHours) {
            throw new InvalidReservationException("reservation must end after it starts and fit the hours of court " + court.getId());
        }
    }
}
