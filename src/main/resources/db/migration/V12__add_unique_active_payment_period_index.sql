CREATE UNIQUE INDEX uq_payment_member_id_period_covered_active ON payment(member_id, period_covered) WHERE voided_at IS NULL;
