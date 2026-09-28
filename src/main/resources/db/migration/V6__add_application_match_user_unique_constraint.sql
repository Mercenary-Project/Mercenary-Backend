ALTER TABLE applications
    ADD CONSTRAINT uk_applications_match_user UNIQUE (match_id, user_id);
