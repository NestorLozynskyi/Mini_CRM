-- Mini CRM MVP schema. Every business row belongs to exactly one account (owner_id) and is removed together with it.
DROP TABLE demo_message;

CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254),
    password_hash VARCHAR(255),
    demo          BOOLEAN     NOT NULL DEFAULT FALSE,
    currency      CHAR(3)     NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ,
    CONSTRAINT app_user_kind CHECK (
        (demo AND email IS NULL AND password_hash IS NULL AND expires_at IS NOT NULL)
        OR (NOT demo AND email IS NOT NULL AND password_hash IS NOT NULL AND expires_at IS NULL)
    )
);
CREATE UNIQUE INDEX app_user_email_uq ON app_user (lower(email));
CREATE INDEX app_user_demo_expires_idx ON app_user (expires_at) WHERE demo;

-- Opaque session tokens (only SHA-256 hash is stored). Demo sessions outlive their deleted account
-- (user_id -> NULL) for a while so the client can be told "demo expired" instead of a generic 401.
CREATE TABLE user_session (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash CHAR(64)    NOT NULL UNIQUE,
    user_id    BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    demo       BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX user_session_user_idx ON user_session (user_id);
CREATE INDEX user_session_expires_idx ON user_session (expires_at);

CREATE TABLE tag (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name     VARCHAR(40) NOT NULL,
    color    VARCHAR(7)  NOT NULL
);
CREATE UNIQUE INDEX tag_owner_name_uq ON tag (owner_id, lower(name));

CREATE TABLE client (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('COMPANY', 'PERSON')),
    name       VARCHAR(200) NOT NULL,
    phone      VARCHAR(50),
    email      VARCHAR(254),
    address    VARCHAR(300),
    website    VARCHAR(200),
    notes      VARCHAR(5000),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX client_owner_idx ON client (owner_id);

CREATE TABLE client_tag (
    client_id BIGINT NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    tag_id    BIGINT NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (client_id, tag_id)
);
CREATE INDEX client_tag_tag_idx ON client_tag (tag_id);

CREATE TABLE contact (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    client_id  BIGINT       NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    phone      VARCHAR(50),
    email      VARCHAR(254),
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX contact_client_idx ON contact (client_id);

CREATE TABLE deal (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id         BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    client_id        BIGINT         NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    title            VARCHAR(200)   NOT NULL,
    amount           BIGINT         NOT NULL CHECK (amount >= 0),
    stage            VARCHAR(20)    NOT NULL CHECK (stage IN ('LEAD', 'CONTACTED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    expected_close   DATE,
    lost_reason      VARCHAR(500),
    archived         BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    stage_changed_at TIMESTAMPTZ    NOT NULL
);
CREATE INDEX deal_owner_idx ON deal (owner_id, archived);
CREATE INDEX deal_client_idx ON deal (client_id);

-- client_id is always set (for deal-linked rows it equals the deal's client) so a client's history,
-- including records of its deals, is one indexed lookup and cascades with the client.
CREATE TABLE interaction (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id    BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    client_id   BIGINT        NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    deal_id     BIGINT REFERENCES deal (id) ON DELETE CASCADE,
    type        VARCHAR(10)   NOT NULL CHECK (type IN ('CALL', 'MEETING', 'EMAIL', 'NOTE')),
    occurred_on DATE          NOT NULL,
    description VARCHAR(5000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX interaction_client_idx ON interaction (client_id);
CREATE INDEX interaction_deal_idx ON interaction (deal_id);
CREATE INDEX interaction_owner_idx ON interaction (owner_id, occurred_on DESC);

CREATE TABLE task (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    client_id    BIGINT       NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    deal_id      BIGINT REFERENCES deal (id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    due_date     DATE         NOT NULL,
    done         BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX task_owner_idx ON task (owner_id, done, due_date);
CREATE INDEX task_client_idx ON task (client_id);
CREATE INDEX task_deal_idx ON task (deal_id);
