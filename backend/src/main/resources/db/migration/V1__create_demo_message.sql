-- Технічна таблиця для демонстрації ланцюга REST -> Spring Data JDBC -> PostgreSQL.
-- Не є доменною сутністю CRM; може бути видалена окремою міграцією.
CREATE TABLE demo_message (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author     VARCHAR(50)  NOT NULL,
    text       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO demo_message (author, text)
VALUES ('system', 'Mini CRM: з''єднання з PostgreSQL працює.');
