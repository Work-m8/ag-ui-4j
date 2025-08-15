package io.workm8.agui4j.example;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.workm8.agui4j.langchain4j.Langchain4jAgent;
import io.workm8.agui4j.langchain4j.LangchainAgent;
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

import java.util.List;

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

        var store = new InMemoryChatMemoryStore();

        var chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryStore(store)
                .build();

        var transport = new StdioMcpTransport.Builder()
                .command(List.of("docker", "run", "--rm", "-i", "mcp/sequentialthinking"))
                .logEvents(true)
                .build();

        var mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();

        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();

        var agent = LangchainAgent.builder()
            .agentId(agentId)
            .threadId(agUiParameters.getThreadId())
            .instructions("You are an AI Agent called 'LangChain'.")
            .streamingChatModel(chatModel)
            .chatModel(OllamaChatModel.builder()
                    .baseUrl("http://localhost:11434")
                    .modelName("llama3.2")
                    .build()
            )
            .messages(agUiParameters.getMessages())
            .chatMemory(chatMemory)
            .tool(new DateTimeTool())
            .toolProvider(toolProvider)
            .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name())
            )
            .build();


        SseEmitter emitter = agUiService.runAgent(agent, agUiParameters);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .body(emitter);
    }

}
