package com.ron.ronaiagent.config;

import com.ron.ronaiagent.agent.AgentExecutionListener;
import com.ron.ronaiagent.agent.JdbcAgentExecutionListener;
import com.ron.ronaiagent.persistence.AgentExecutionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class PersistenceConfig {

    @Bean
    @Profile("prod")
    public AgentExecutionRepository agentExecutionRepository(DataSource dataSource) {
        return new AgentExecutionRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    @Profile("prod")
    public AgentExecutionListener agentExecutionListener(AgentExecutionRepository repository) {
        return new JdbcAgentExecutionListener(repository);
    }
}
