-- GharPahucho initial schema. Portable between PostgreSQL (default) and H2 (PostgreSQL mode, used by the
-- "local" and "test" profiles). JSON payloads are stored as TEXT to keep the schema portable.

CREATE TABLE app_user (
    id                 UUID PRIMARY KEY,
    email              VARCHAR(190)  NOT NULL,
    password_hash      VARCHAR(100)  NOT NULL,
    display_name       VARCHAR(80)   NOT NULL,
    home_city          VARCHAR(80),
    home_locality      VARCHAR(80),
    preferred_language VARCHAR(10)   NOT NULL DEFAULT 'en',
    onboarded          BOOLEAN       NOT NULL DEFAULT FALSE,
    beta_tester        BOOLEAN       NOT NULL DEFAULT FALSE,
    demo_account       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE user_preference (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL,
    preferred_mode     VARCHAR(20)  NOT NULL DEFAULT 'ANY',
    priority           VARCHAR(20)  NOT NULL DEFAULT 'CHEAPEST',
    default_max_budget INTEGER,
    willing_to_share   BOOLEAN      NOT NULL DEFAULT TRUE,
    first_name_only    BOOLEAN      NOT NULL DEFAULT TRUE,
    discoverable       BOOLEAN      NOT NULL DEFAULT TRUE,
    notes              VARCHAR(400),
    CONSTRAINT uq_user_preference_user UNIQUE (user_id),
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_preference_budget CHECK (default_max_budget IS NULL OR default_max_budget > 0)
);

CREATE TABLE trip (
    id                    UUID PRIMARY KEY,
    owner_id              UUID         NOT NULL,
    source_city           VARCHAR(80)  NOT NULL,
    source_locality       VARCHAR(80),
    source_lat            DOUBLE PRECISION NOT NULL,
    source_lon            DOUBLE PRECISION NOT NULL,
    destination_city      VARCHAR(80)  NOT NULL,
    destination_lat       DOUBLE PRECISION NOT NULL,
    destination_lon       DOUBLE PRECISION NOT NULL,
    travel_date           DATE         NOT NULL,
    flex_days             INTEGER      NOT NULL DEFAULT 0,
    return_date           DATE,
    travellers            INTEGER      NOT NULL DEFAULT 1,
    max_budget            INTEGER,
    preferred_mode        VARCHAR(20)  NOT NULL DEFAULT 'ANY',
    priority              VARCHAR(20)  NOT NULL DEFAULT 'CHEAPEST',
    arrive_by             TIME,
    departure_time        TIME,
    willing_to_share      BOOLEAN      NOT NULL DEFAULT FALSE,
    seats_available       INTEGER      NOT NULL DEFAULT 0,
    cost_per_seat         INTEGER,
    notes                 VARCHAR(400),
    visibility            VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE',
    status                VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    data_origin           VARCHAR(20)  NOT NULL DEFAULT 'USER_CREATED',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_trip_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_trip_travellers CHECK (travellers BETWEEN 1 AND 12),
    CONSTRAINT ck_trip_flex CHECK (flex_days BETWEEN 0 AND 7),
    CONSTRAINT ck_trip_seats CHECK (seats_available BETWEEN 0 AND 12),
    CONSTRAINT ck_trip_budget CHECK (max_budget IS NULL OR max_budget > 0),
    CONSTRAINT ck_trip_return CHECK (return_date IS NULL OR return_date >= travel_date)
);
CREATE INDEX idx_trip_owner ON trip (owner_id, travel_date);
CREATE INDEX idx_trip_discovery ON trip (visibility, status, destination_city, travel_date);
CREATE INDEX idx_trip_route ON trip (source_city, destination_city, travel_date);

-- Simulated transport inventory. Every row is DEMONSTRATION data and is labelled as such in every API response.
CREATE TABLE travel_option (
    id                UUID PRIMARY KEY,
    mode              VARCHAR(20)  NOT NULL,
    operator_name     VARCHAR(80)  NOT NULL,
    service_name      VARCHAR(120) NOT NULL,
    origin_city       VARCHAR(80)  NOT NULL,
    destination_city  VARCHAR(80)  NOT NULL,
    departure_time    TIME         NOT NULL,
    duration_minutes  INTEGER      NOT NULL,
    base_fare         INTEGER      NOT NULL,
    comfort_score     INTEGER      NOT NULL DEFAULT 3,
    stops_text        VARCHAR(300),
    data_source       VARCHAR(30)  NOT NULL DEFAULT 'SIMULATED_INVENTORY',
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_travel_option_comfort CHECK (comfort_score BETWEEN 1 AND 5),
    CONSTRAINT ck_travel_option_fare CHECK (base_fare > 0),
    CONSTRAINT ck_travel_option_duration CHECK (duration_minutes > 0)
);
CREATE INDEX idx_travel_option_route ON travel_option (origin_city, destination_city, mode);

CREATE TABLE travel_match (
    id                UUID PRIMARY KEY,
    trip_id           UUID         NOT NULL,
    candidate_trip_id UUID         NOT NULL,
    score             INTEGER      NOT NULL,
    breakdown_json    TEXT         NOT NULL,
    reasons_json      TEXT         NOT NULL,
    algorithm_version VARCHAR(20)  NOT NULL,
    computed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_travel_match UNIQUE (trip_id, candidate_trip_id),
    CONSTRAINT fk_travel_match_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_match_candidate FOREIGN KEY (candidate_trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT ck_travel_match_score CHECK (score BETWEEN 0 AND 100)
);
CREATE INDEX idx_travel_match_trip ON travel_match (trip_id, score DESC);

CREATE TABLE travel_interest (
    id            UUID PRIMARY KEY,
    from_trip_id  UUID         NOT NULL,
    to_trip_id    UUID         NOT NULL,
    from_user_id  UUID         NOT NULL,
    to_user_id    UUID         NOT NULL,
    message       VARCHAR(300),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at  TIMESTAMP WITH TIME ZONE,
    group_id      UUID,
    CONSTRAINT uq_travel_interest UNIQUE (from_trip_id, to_trip_id),
    CONSTRAINT fk_interest_from_trip FOREIGN KEY (from_trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_interest_to_trip FOREIGN KEY (to_trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_interest_from_user FOREIGN KEY (from_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_interest_to_user FOREIGN KEY (to_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_interest_self CHECK (from_user_id <> to_user_id)
);
CREATE INDEX idx_interest_to_user ON travel_interest (to_user_id, status);
CREATE INDEX idx_interest_from_user ON travel_interest (from_user_id, status);

CREATE TABLE travel_group (
    id                      UUID PRIMARY KEY,
    name                    VARCHAR(120) NOT NULL,
    owner_id                UUID         NOT NULL,
    source_city             VARCHAR(80)  NOT NULL,
    destination_city        VARCHAR(80)  NOT NULL,
    travel_date             DATE         NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'FORMING',
    plan_mode               VARCHAR(20)  NOT NULL DEFAULT 'CAB',
    plan_notes              VARCHAR(600),
    meeting_area            VARCHAR(120),
    planned_departure_time  TIME,
    max_members             INTEGER      NOT NULL DEFAULT 4,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_group_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_group_max CHECK (max_members BETWEEN 2 AND 12)
);
CREATE INDEX idx_group_route_date ON travel_group (source_city, destination_city, travel_date);

CREATE TABLE group_member (
    id         UUID PRIMARY KEY,
    group_id   UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status     VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    invited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    joined_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES travel_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_group_member_user ON group_member (user_id, status);

CREATE TABLE group_message (
    id         UUID PRIMARY KEY,
    group_id   UUID          NOT NULL,
    sender_id  UUID          NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_group_message_group FOREIGN KEY (group_id) REFERENCES travel_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_message_sender FOREIGN KEY (sender_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_group_message_group ON group_message (group_id, created_at);

CREATE TABLE saved_trip (
    id                   UUID PRIMARY KEY,
    user_id              UUID         NOT NULL,
    trip_id              UUID,
    title                VARCHAR(160) NOT NULL,
    source_city          VARCHAR(80)  NOT NULL,
    destination_city     VARCHAR(80)  NOT NULL,
    travel_date          DATE         NOT NULL,
    option_snapshot_json TEXT         NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_saved_trip_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_trip_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE SET NULL
);
CREATE INDEX idx_saved_trip_user ON saved_trip (user_id, created_at DESC);

CREATE TABLE activity_event (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    message    VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_activity_user ON activity_event (user_id, created_at DESC);
