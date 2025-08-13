package io.workm8.agui4j.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workm8.agui4j.server.spring.AgUiService;
import io.workm8.agui4j.server.streamer.AgentStreamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgUiConfig {

    @Bean
    public AgentStreamer agentStreamer() {
        return new AgentStreamer();
    }

    @Bean
    public AgUiService agUiService(ObjectMapper objectMapper) {
        return new AgUiService(agentStreamer(), objectMapper);
    }
}
