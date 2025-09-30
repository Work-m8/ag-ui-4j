package com.agui;

import com.agui.http.HttpAgent;
import com.agui.spring.HttpClient;
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
