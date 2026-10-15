ALTER TABLE payment
    ADD COLUMN voided_at TIMESTAMPTZ,
    ADD COLUMN voided_by_user_id BIGINT,
    ADD COLUMN void_reason VARCHAR(150),
    ADD CONSTRAINT fk_payment_voided_by_user FOREIGN KEY (voided_by_user_id) REFERENCES user_account(id),
    ADD CONSTRAINT ck_payment_void_fields_together CHECK ((voided_at IS NULL) = (voided_by_user_id IS NULL)),
    ADD CONSTRAINT ck_payment_void_reason_requires_void CHECK (void_reason IS NULL OR voided_at IS NOT NULL);

CREATE INDEX idx_payment_voided_by_user_id ON payment(voided_by_user_id);
