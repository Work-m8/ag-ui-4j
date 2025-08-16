package io.workm8.agui4j.example.config;

import io.workm8.agui4j.core.exception.AGUIException;
import io.workm8.agui4j.core.state.State;
import io.workm8.agui4j.spring.ai.SpringAIAgent;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Configuration
public class AgUiConfig {

    @Bean
    public SpringAIAgent agent() throws AGUIException {
        var chatModel = OllamaChatModel.builder()
            .defaultOptions(OllamaOptions.builder().model("llama3.2").build())
            .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
            .build();
/*
        ToolCallback toolCallback = FunctionToolCallback
            .builder("currentDateTime", new CurrentDateTime())
            .description("Get the current date")
            .inputType(Void.class)
            .build();
*/
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(10)
            .build();

        var state = new State();
        state.set("Language", "nl");

        // Only useful during startup
        state.set("Current Date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        state.set("Current Time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        return SpringAIAgent.builder()
            .agentId("1")
            .chatMemory(chatMemory)
            .chatModel(chatModel)
            .systemMessage("You are a helpful AI assistant, called Moira.")
            .state(state)
            .build();
    }
}
