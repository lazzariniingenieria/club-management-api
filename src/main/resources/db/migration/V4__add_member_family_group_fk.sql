ALTER TABLE member
    ADD CONSTRAINT fk_member_family_group FOREIGN KEY (family_group_id) REFERENCES family_group(id);
