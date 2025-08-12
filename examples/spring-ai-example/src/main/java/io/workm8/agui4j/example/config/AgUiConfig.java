package io.workm8.agui4j.example.config;

import io.workm8.agui4j.server.streamer.AgentStreamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgUiConfig {

    @Bean
    public AgentStreamer agentStreamer() {
        return new AgentStreamer();
    }
}
