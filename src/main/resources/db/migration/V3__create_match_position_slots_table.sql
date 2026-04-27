CREATE TABLE match_position_slots (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    match_id BIGINT,
    position ENUM('GK','CB','LB','RB','CDM','CM','CAM','LW','RW','ST') NOT NULL,
    required INT         NOT NULL,
    filled   INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_match_position_slots_match
        FOREIGN KEY (match_id) REFERENCES matches (match_id)
);
