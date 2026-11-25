-- Ride alerts: "tell me when someone posts a ride on my route". Matches are computed from live trips, not stored.
CREATE TABLE ride_alert (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL,
    source_city      VARCHAR(80)  NOT NULL,
    destination_city VARCHAR(80)  NOT NULL,
    travel_date      DATE         NOT NULL,
    flex_days        INTEGER      NOT NULL DEFAULT 1,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ride_alert_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_ride_alert_user ON ride_alert (user_id);
CREATE INDEX idx_ride_alert_route ON ride_alert (destination_city, source_city, travel_date);

-- Family link: an unguessable read-only page about one trip, in the family member's language.
CREATE TABLE family_link (
    id                UUID PRIMARY KEY,
    trip_id           UUID         NOT NULL,
    owner_id          UUID         NOT NULL,
    token             VARCHAR(64)  NOT NULL,
    language          VARCHAR(10)  NOT NULL DEFAULT 'en',
    family_name       VARCHAR(60),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    status_updated_at TIMESTAMP WITH TIME ZONE,
    message           VARCHAR(200),
    revoked           BOOLEAN      NOT NULL DEFAULT FALSE,
    views             INTEGER      NOT NULL DEFAULT 0,
    last_viewed_at    TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_family_link_token UNIQUE (token),
    CONSTRAINT fk_family_link_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_family_link_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_family_link_trip ON family_link (trip_id);
