package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@Getter
@RequiredArgsConstructor
public class CourtBlockSearchSpecification implements Specification<CourtBlock> {

    private final Long courtId;
    private final CourtBlockSearchRequest filter;

    @Override
    public Predicate toPredicate(Root<CourtBlock> block, CriteriaQuery<?> query, CriteriaBuilder builder) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(block.get("courtId"), courtId));
        addRangeFilters(block, builder, predicates);
        Predicate[] predicateArray = predicates.toArray(new Predicate[0]);

        return builder.and(predicateArray);
    }

    private void addRangeFilters(Root<CourtBlock> block, CriteriaBuilder builder, List<Predicate> predicates) {
        if (filter.from() != null) {
            LocalDateTime startOfFrom = filter.from().atStartOfDay();
            predicates.add(builder.greaterThan(block.<LocalDateTime>get("endsAt"), startOfFrom));
        }

        if (filter.to() != null) {
            LocalDateTime startOfDayAfterTo = filter.to().plusDays(1).atStartOfDay();
            predicates.add(builder.lessThan(block.<LocalDateTime>get("startsAt"), startOfDayAfterTo));
        }
    }
}
