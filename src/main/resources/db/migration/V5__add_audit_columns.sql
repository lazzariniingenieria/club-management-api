ALTER TABLE user_account
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN created_by_user_id BIGINT,
    ADD COLUMN updated_by_user_id BIGINT;

ALTER TABLE member
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN created_by_user_id BIGINT,
    ADD COLUMN updated_by_user_id BIGINT;

ALTER TABLE family_group
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN created_by_user_id BIGINT,
    ADD COLUMN updated_by_user_id BIGINT;

ALTER TABLE user_account
    ADD CONSTRAINT fk_user_account_created_by FOREIGN KEY (created_by_user_id) REFERENCES user_account(id),
    ADD CONSTRAINT fk_user_account_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES user_account(id);

ALTER TABLE member
    ADD CONSTRAINT fk_member_created_by FOREIGN KEY (created_by_user_id) REFERENCES user_account(id),
    ADD CONSTRAINT fk_member_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES user_account(id);

ALTER TABLE family_group
    ADD CONSTRAINT fk_family_group_created_by FOREIGN KEY (created_by_user_id) REFERENCES user_account(id),
    ADD CONSTRAINT fk_family_group_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES user_account(id);

CREATE INDEX idx_user_account_created_by_user_id ON user_account(created_by_user_id);
CREATE INDEX idx_user_account_updated_by_user_id ON user_account(updated_by_user_id);
CREATE INDEX idx_member_created_by_user_id ON member(created_by_user_id);
CREATE INDEX idx_member_updated_by_user_id ON member(updated_by_user_id);
CREATE INDEX idx_family_group_created_by_user_id ON family_group(created_by_user_id);
CREATE INDEX idx_family_group_updated_by_user_id ON family_group(updated_by_user_id);
