package io.workm8.agui4j;

import io.workm8.agui4j.http.HttpAgent;
import io.workm8.agui4j.spring.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class AgUiConfig {

    @Bean
    public HttpAgent agent() {
        return HttpAgent.builder()
            .httpClient(new HttpClient("http://localhost:8080/sse/1"))
            .agentId("1")
            .threadId(UUID.randomUUID().toString())
            .build();
    }
}
