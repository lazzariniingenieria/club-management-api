ALTER TABLE member
    ADD CONSTRAINT fk_member_club FOREIGN KEY (club_id) REFERENCES club(id);

ALTER TABLE user_account
    ADD CONSTRAINT fk_user_account_club FOREIGN KEY (club_id) REFERENCES club(id);
