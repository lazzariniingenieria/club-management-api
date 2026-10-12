package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.dto.PaymentSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
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
public class PaymentSearchSpecification implements Specification<Payment> {

    private final Long clubId;
    private final PaymentSearchRequest filter;

    @Override
    public Predicate toPredicate(Root<Payment> payment, CriteriaQuery<?> query, CriteriaBuilder builder) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(belongsToClub(payment, query, builder));
        addStatusAndMemberFilters(payment, builder, predicates);
        addPeriodAndMethodFilters(payment, builder, predicates);
        Predicate[] predicateArray = predicates.toArray(new Predicate[0]);

        return builder.and(predicateArray);
    }

    private Predicate belongsToClub(Root<Payment> payment, CriteriaQuery<?> query, CriteriaBuilder builder) {
        Subquery<Long> clubMemberIds = query.subquery(Long.class);
        Root<Member> member = clubMemberIds.from(Member.class);
        clubMemberIds.select(member.get("id"));
        clubMemberIds.where(builder.equal(member.get("clubId"), clubId));

        return payment.get("memberId").in(clubMemberIds);
    }

    private void addStatusAndMemberFilters(Root<Payment> payment, CriteriaBuilder builder, List<Predicate> predicates) {
        if (!filter.includesVoided()) {
            predicates.add(builder.isNull(payment.get("voidedAt")));
        }

        if (filter.memberId() != null) {
            predicates.add(builder.equal(payment.get("memberId"), filter.memberId()));
        }
    }

    private void addPeriodAndMethodFilters(Root<Payment> payment, CriteriaBuilder builder, List<Predicate> predicates) {
        if (filter.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(payment.<LocalDate>get("periodCovered"), filter.from()));
        }

        if (filter.to() != null) {
            predicates.add(builder.lessThanOrEqualTo(payment.<LocalDate>get("periodCovered"), filter.to()));
        }

        if (filter.paymentMethod() != null) {
            predicates.add(builder.equal(payment.get("paymentMethod"), filter.paymentMethod()));
        }
    }
}
