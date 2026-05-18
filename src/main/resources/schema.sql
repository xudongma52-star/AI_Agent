CREATE TABLE IF NOT EXISTS chat_memory (
    id              BIGSERIAL       PRIMARY KEY,
    conversation_id VARCHAR(64)     NOT NULL,
    message_type    VARCHAR(16)     NOT NULL,
    content         TEXT            NOT NULL,
    message_order   INT             NOT NULL DEFAULT 0,
    created_time    TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_conv_order
    ON chat_memory (conversation_id, message_order);
