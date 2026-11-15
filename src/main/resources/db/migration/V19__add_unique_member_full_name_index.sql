CREATE UNIQUE INDEX uk_member_club_full_name ON member (club_id, LOWER(first_name), LOWER(last_name));
