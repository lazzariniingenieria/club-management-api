-- member_id was created in V1 without a foreign key because the member table did not exist yet.
-- Any value that no longer resolves to a member is meaningless, so it is cleared before the constraint is added.
UPDATE user_account
SET member_id = NULL
WHERE member_id IS NOT NULL
  AND member_id NOT IN (SELECT id FROM member);

ALTER TABLE user_account
    ADD CONSTRAINT fk_user_account_member FOREIGN KEY (member_id) REFERENCES member(id);
