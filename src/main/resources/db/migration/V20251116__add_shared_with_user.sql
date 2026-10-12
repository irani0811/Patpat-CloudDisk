-- Migration: add shared_with_user_id column and foreign key to file_shares
-- Compatible with H2/MySQL; adjust types if needed

ALTER TABLE file_shares
    ADD COLUMN IF NOT EXISTS shared_with_user_id BIGINT;

ALTER TABLE file_shares
    ADD CONSTRAINT IF NOT EXISTS fk_file_shares_shared_with
        FOREIGN KEY (shared_with_user_id)
        REFERENCES users (id);
