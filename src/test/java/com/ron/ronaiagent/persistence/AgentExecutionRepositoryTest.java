package com.ron.ronaiagent.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentExecutionRepositoryTest {

    private AgentExecutionRepository repository;

    @BeforeEach
    void setUp() {
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        repository = new AgentExecutionRepository(new JdbcTemplate(ds));
    }

    @Test
    void save_andFindById_shouldRoundTrip() {
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .agentName("RonManus")
                .prompt("What is Java?")
                .status("RUNNING")
                .currentStep(3)
                .maxSteps(10)
                .startTime(LocalDateTime.now())
                .build();

        Long id = repository.save(record);
        assertNotNull(id);

        AgentExecutionRecord loaded = repository.findById(id);
        assertNotNull(loaded);
        assertEquals("RonManus", loaded.getAgentName());
        assertEquals(3, loaded.getCurrentStep());
    }

    @Test
    void findByStatus_shouldReturnMatchingRecords() {
        repository.save(AgentExecutionRecord.builder()
                .agentName("test").prompt("p").status("RUNNING")
                .currentStep(1).maxSteps(10).startTime(LocalDateTime.now()).build());
        repository.save(AgentExecutionRecord.builder()
                .agentName("test2").prompt("p2").status("FINISHED")
                .currentStep(5).maxSteps(10).startTime(LocalDateTime.now()).build());

        List<AgentExecutionRecord> running = repository.findByStatus("RUNNING");
        assertEquals(1, running.size());
        assertEquals("test", running.getFirst().getAgentName());
    }

    @Test
    void updateStatus_shouldModifyRecord() {
        Long id = repository.save(AgentExecutionRecord.builder()
                .agentName("test").prompt("p").status("RUNNING")
                .currentStep(1).maxSteps(10).startTime(LocalDateTime.now()).build());

        repository.updateStatus(id, "FINISHED", 5, "Task completed successfully");
        AgentExecutionRecord updated = repository.findById(id);
        assertEquals("FINISHED", updated.getStatus());
        assertEquals(5, updated.getCurrentStep());
    }

    @Test
    void findByStatus_nonExistent_shouldReturnEmpty() {
        List<AgentExecutionRecord> records = repository.findByStatus("UNKNOWN");
        assertTrue(records.isEmpty());
    }
}
