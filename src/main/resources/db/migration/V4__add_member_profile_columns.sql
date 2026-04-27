ALTER TABLE members
    ADD COLUMN preferred_position ENUM('GK','CB','LB','RB','CDM','CM','CAM','LW','RW','ST') NULL,
    ADD COLUMN skill_level        ENUM('BEGINNER','AMATEUR','SEMI_PRO')                    NULL,
    ADD COLUMN manner_score       DOUBLE                                                   NOT NULL DEFAULT 0;
