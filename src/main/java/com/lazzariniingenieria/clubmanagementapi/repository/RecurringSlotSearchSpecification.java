package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@Getter
@RequiredArgsConstructor
public class RecurringSlotSearchSpecification implements Specification<RecurringSlot> {

    private final Long clubId;
    private final RecurringSlotSearchRequest filter;
    private final LocalDate today;

    @Override
    public Predicate toPredicate(Root<RecurringSlot> slot, CriteriaQuery<?> query, CriteriaBuilder builder) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(slot.get("courtId").in(courtIdsOfClub(query, builder)));
        addOwnerFilters(slot, builder, predicates);

        if (filter.state() != null) {
            predicates.add(stateMatches(slot, builder, filter.state()));
        }

        Predicate[] predicateArray = predicates.toArray(new Predicate[0]);

        return builder.and(predicateArray);
    }

    private Subquery<Long> courtIdsOfClub(CriteriaQuery<?> query, CriteriaBuilder builder) {
        Subquery<Long> courtIds = query.subquery(Long.class);
        Root<Court> court = courtIds.from(Court.class);
        courtIds.select(court.get("id"));
        courtIds.where(builder.equal(court.get("clubId"), clubId));

        return courtIds;
    }

    private void addOwnerFilters(Root<RecurringSlot> slot, CriteriaBuilder builder, List<Predicate> predicates) {
        if (filter.courtId() != null) {
            predicates.add(builder.equal(slot.get("courtId"), filter.courtId()));
        }

        if (filter.memberId() != null) {
            predicates.add(builder.equal(slot.get("memberId"), filter.memberId()));
        }
    }

    private Predicate stateMatches(Root<RecurringSlot> slot, CriteriaBuilder builder, RecurringSlotState state) {
        Predicate active = builder.isTrue(slot.get("active"));

        return switch (state) {
            case CURRENT -> builder.and(active, builder.greaterThanOrEqualTo(slot.<LocalDate>get("validUntil"), today));
            case EXPIRED -> builder.and(active, builder.lessThan(slot.<LocalDate>get("validUntil"), today));
            case INACTIVE -> builder.isFalse(slot.get("active"));
        };
    }
}
