package io.workm8.agui4j.example;

import io.workm8.agui4j.server.spring.AgUiParameters;
import io.workm8.agui4j.server.spring.AgUiService;
import io.workm8.agui4j.spring.ai.SpringAIAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;

@Controller
public class AgUiController {

    private final AgUiService agUiService;

    @Autowired
    public AgUiController(
        final AgUiService agUiService
    ) {
        this.agUiService = agUiService;
    }

    @PostMapping(value = "/sse/{agentId}")
    public ResponseEntity<SseEmitter> streamData(@PathVariable("agentId") final String agentId, @RequestBody() final AgUiParameters agUiParameters) {
        var chatModel = OllamaChatModel.builder()
            .defaultOptions(OllamaOptions.builder().model("llama3.2").build())
            .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
            .build();

        SpringAIAgent agent = new SpringAIAgent(
            agUiParameters.getThreadId(),
            ChatClient.builder(chatModel).build(),
            new ArrayList<>()
        );

        agent.setMessages(agUiParameters.getMessages());

        SseEmitter emitter = this.agUiService.runAgent(agent, agUiParameters);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .body(emitter);
    }

}
