ALTER TABLE member
    ADD CONSTRAINT fk_member_family_group FOREIGN KEY (family_group_id) REFERENCES family_group(id);

CREATE INDEX idx_member_family_group_id ON member(family_group_id);
