ALTER TABLE applications
    MODIFY COLUMN position ENUM('GK','CB','LB','RB','CDM','CM','CAM','LW','RW','ST') NOT NULL DEFAULT 'GK';
