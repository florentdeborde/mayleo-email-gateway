-- Mock table for ShedLock (Integrated Tests only)
-- Used to prevent execution gaps in EmailRequestWorkerIT due to distributed locking

-- Need spring.sql.init.mode of application-it.yml

DROP TABLE IF EXISTS shedlock;

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
