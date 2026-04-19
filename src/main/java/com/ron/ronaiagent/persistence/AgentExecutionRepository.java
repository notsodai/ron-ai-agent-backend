package com.ron.ronaiagent.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

public class AgentExecutionRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<AgentExecutionRecord> ROW_MAPPER = (rs, rowNum) ->
            AgentExecutionRecord.builder()
                    .id(rs.getLong("id"))
                    .agentName(rs.getString("agent_name"))
                    .prompt(rs.getString("prompt"))
                    .status(rs.getString("status"))
                    .currentStep(rs.getInt("current_step"))
                    .maxSteps(rs.getInt("max_steps"))
                    .result(rs.getString("result"))
                    .startTime(rs.getTimestamp("start_time") != null ?
                            rs.getTimestamp("start_time").toLocalDateTime() : null)
                    .endTime(rs.getTimestamp("end_time") != null ?
                            rs.getTimestamp("end_time").toLocalDateTime() : null)
                    .durationMs(rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null)
                    .errorMessage(rs.getString("error_message"))
                    .build();

    public AgentExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(AgentExecutionRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO agent_execution (agent_name, prompt, status, current_step, max_steps, start_time) VALUES (?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, record.getAgentName());
            ps.setString(2, record.getPrompt());
            ps.setString(3, record.getStatus());
            ps.setInt(4, record.getCurrentStep());
            ps.setInt(5, record.getMaxSteps());
            ps.setTimestamp(6, record.getStartTime() != null ?
                    java.sql.Timestamp.valueOf(record.getStartTime()) : null);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public AgentExecutionRecord findById(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM agent_execution WHERE id = ?", ROW_MAPPER, id);
    }

    public List<AgentExecutionRecord> findByStatus(String status) {
        return jdbcTemplate.query(
                "SELECT * FROM agent_execution WHERE status = ? ORDER BY created_at DESC",
                ROW_MAPPER, status);
    }

    public void updateStatus(Long id, String status, int currentStep, String result) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "UPDATE agent_execution SET status = ?, current_step = ?, result = ?, end_time = ? WHERE id = ?",
                status, currentStep, result, now, id);
    }
}
