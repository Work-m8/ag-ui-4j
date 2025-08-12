package io.workm8.agui4j.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

import io.workm8.agui4j.core.agent.RunAgentInput;
import io.workm8.agui4j.core.agent.RunAgentParameters;
import io.workm8.agui4j.core.event.BaseEvent;
import io.workm8.agui4j.core.stream.EventStream;
import io.workm8.agui4j.json.ObjectMapperFactory;
import io.workm8.agui4j.langchain4j.Langchain4jAgent;
import io.workm8.agui4j.server.streamer.AgentStreamer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class AgUiService {

    private final ObjectMapper objectMapper;

    private final AgentStreamer agentStreamer;

    public AgUiService() {
        this.objectMapper = new ObjectMapper();
        ObjectMapperFactory.addMixins(objectMapper);

        this.agentStreamer = new AgentStreamer();
    }

    public SseEmitter streamEvents(final RunAgentParameters parameters, final RunAgentInput input) {
        StreamingChatModel chatModel = OllamaStreamingChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("llama3.2")
            .build();

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        var eventStream = new EventStream<BaseEvent>(
            event -> {
                try {
                    emitter.send(SseEmitter.event().data(" " + objectMapper.writeValueAsString(event)).build());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            emitter::completeWithError,
            emitter::complete
        );

        agentStreamer.streamEvents(
            new Langchain4jAgent(
                input.threadId(),
                input.state(),
                chatModel,
                input.messages()
            ),
            parameters,
            eventStream
        );

        return emitter;
    }

}
