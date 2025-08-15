package io.workm8.agui4j.server.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workm8.agui4j.core.agent.RunAgentParameters;
import io.workm8.agui4j.core.event.BaseEvent;
import io.workm8.agui4j.core.stream.EventStream;
import io.workm8.agui4j.json.ObjectMapperFactory;
import io.workm8.agui4j.server.LocalAgent;
import io.workm8.agui4j.server.streamer.AgentStreamer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public class AgUiService {

    private final AgentStreamer agentStreamer;

    private final ObjectMapper objectMapper;

    public AgUiService(
        final AgentStreamer agentStreamer,
        final ObjectMapper objectMapper
    ) {
        this.agentStreamer = agentStreamer;

        this.objectMapper = objectMapper;
        ObjectMapperFactory.addMixins(this.objectMapper);
    }

    public SseEmitter runAgent(final LocalAgent agent, final AgUiParameters agUiParameters) {
        var parameters = RunAgentParameters.builder()
            .runId(agUiParameters.getRunId())
            .tools(agUiParameters.getTools())
            .context(agUiParameters.getContext())
            .forwardedProps(agUiParameters.getForwardedProps())
            .build();

        agent.setThreadId(agUiParameters.getThreadId());
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

        this.agentStreamer.streamEvents(agent, parameters, eventStream);

        return emitter;
    }
}
