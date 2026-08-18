CREATE TABLE user_account (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id BIGINT NOT NULL,
    member_id BIGINT,
    dni VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    email VARCHAR(150),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_account_club_dni UNIQUE (club_id, dni),
    CONSTRAINT uk_user_account_member_id UNIQUE (member_id),
    CONSTRAINT ck_user_account_role CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_user_account_super_admin_no_member CHECK (role <> 'SUPER_ADMIN' OR member_id IS NULL)
);
