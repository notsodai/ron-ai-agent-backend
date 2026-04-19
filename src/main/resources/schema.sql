CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_conv_id ON chat_memory(conversation_id);
CREATE INDEX IF NOT EXISTS idx_chat_memory_created_at ON chat_memory(created_at);

CREATE TABLE IF NOT EXISTS agent_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_name VARCHAR(100) NOT NULL,
    prompt TEXT,
    status VARCHAR(20) NOT NULL,
    current_step INT DEFAULT 0,
    max_steps INT DEFAULT 10,
    result TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_exec_status ON agent_execution(status);
CREATE INDEX IF NOT EXISTS idx_agent_exec_name ON agent_execution(agent_name);
