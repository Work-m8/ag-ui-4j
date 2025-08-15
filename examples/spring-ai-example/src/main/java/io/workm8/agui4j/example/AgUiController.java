package io.workm8.agui4j.example;

import io.workm8.agui4j.example.tools.CurrentDateTime;
import io.workm8.agui4j.server.spring.AgUiParameters;
import io.workm8.agui4j.server.spring.AgUiService;
import io.workm8.agui4j.spring.ai.SpringAIAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

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

        ToolCallback toolCallback = FunctionToolCallback
            .builder("currentDateTime", new CurrentDateTime())
            .description("Get the current date")
            .inputType(Void.class)
            .build();

        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(10)
            .build();

        chatMemory.add(agUiParameters.getThreadId(), new UserMessage("My name is john"));
        chatMemory.add(UUID.randomUUID().toString(), new UserMessage("My name is Jake"));

        var chatMemoryAdvisor = PromptChatMemoryAdvisor.builder(chatMemory)
                .build();

        SpringAIAgent agent = SpringAIAgent.builder()
            .agentId(agentId)
            .instructions("You are an AI agent called 'Spring'.")
            .chatClient(ChatClient.builder(chatModel).build())
            .toolCallback(toolCallback)
            .messages(agUiParameters.getMessages())
            .advisor(chatMemoryAdvisor)
            .build();

        SseEmitter emitter = this.agUiService.runAgent(agent, agUiParameters);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .body(emitter);
    }

}
