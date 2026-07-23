-- Schema for real MySQL + Redis search latency integration tests.
CREATE TABLE IF NOT EXISTS items (
    item_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    address VARCHAR(255),
    url VARCHAR(255),
    seller VARCHAR(255),
    description TEXT,
    source_type VARCHAR(32) NOT NULL DEFAULT 'innova_catalog',
    PRIMARY KEY (item_id)
);

CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS keywords (
    item_id VARCHAR(255) NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    PRIMARY KEY (item_id, keyword),
    FOREIGN KEY (item_id) REFERENCES items(item_id)
);

CREATE TABLE IF NOT EXISTS history (
    user_id VARCHAR(255) NOT NULL,
    item_id VARCHAR(255) NOT NULL,
    last_favor_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, item_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (item_id) REFERENCES items(item_id)
);

CREATE TABLE IF NOT EXISTS keyword_stats (
    keyword VARCHAR(255) NOT NULL,
    document_frequency INT NOT NULL DEFAULT 0,
    PRIMARY KEY (keyword)
);

CREATE TABLE IF NOT EXISTS corpus_stats (
    stat_key VARCHAR(64) NOT NULL,
    stat_value BIGINT NOT NULL,
    PRIMARY KEY (stat_key)
);
