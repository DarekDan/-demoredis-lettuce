-- This schema will be picked up by spring.r2dbc.initialization-mode=always
-- It's compatible with both H2 and PostgreSQL

CREATE TABLE IF NOT EXISTS item (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000)
);

-- Insert a default item for testing
INSERT INTO item (name, description) VALUES ('Default Item', 'An item pre-populated from schema.sql')
ON CONFLICT (id) DO NOTHING;
