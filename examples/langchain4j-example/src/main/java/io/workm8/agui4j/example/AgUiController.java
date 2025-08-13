package io.workm8.agui4j.example;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.workm8.agui4j.langchain4j.Langchain4jAgent;
import io.workm8.agui4j.server.spring.AgUiParameters;
import io.workm8.agui4j.server.spring.AgUiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
        StreamingChatModel chatModel = OllamaStreamingChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("llama3.2")
            .build();

        var agent =  new Langchain4jAgent(
            agUiParameters.getThreadId(),
            agUiParameters.getState(),
            chatModel,
            agUiParameters.getMessages()
        );

        SseEmitter emitter = agUiService.runAgent(agent, agUiParameters);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .body(emitter);
    }

}
