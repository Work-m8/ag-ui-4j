package io.workm8.agui4j.spring.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workm8.agui4j.core.event.BaseEvent;
import io.workm8.agui4j.core.event.ToolCallArgsEvent;
import io.workm8.agui4j.core.event.ToolCallEndEvent;
import io.workm8.agui4j.core.event.ToolCallStartEvent;
import io.workm8.agui4j.core.tool.Tool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ToolMapper {

    private final ObjectMapper objectMapper;

    public ToolMapper(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ToolCallback toSpringTool(final Tool tool, final String messageId, final Consumer<BaseEvent> eventConsumer) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return new ToolDefinition() {
                    @Override
                    public String name() {
                        return tool.name();
                    }

                    @Override
                    public String description() {
                        return tool.description();
                    }

                    @Override
                    public String inputSchema() {
                        try {
                            return objectMapper.writeValueAsString(tool.parameters());
                        } catch (JsonProcessingException e) {
                            return "";
                        }
                    }
                };
            }

            @Override
            public String call(String toolInput) {
                var toolCallId = UUID.randomUUID().toString();

                var toolCallStartEvent = new ToolCallStartEvent();
                toolCallStartEvent.setParentMessageId(messageId);
                toolCallStartEvent.setToolCallName(tool.name());
                toolCallStartEvent.setToolCallId(toolCallId);

                eventConsumer.accept(toolCallStartEvent);

                var toolCallArgsEvent = new ToolCallArgsEvent();
                toolCallArgsEvent.setDelta(toolInput);
                toolCallArgsEvent.setToolCallId(toolCallId);

                eventConsumer.accept(toolCallArgsEvent);

                var toolCallEndEvent = new ToolCallEndEvent();
                toolCallEndEvent.setToolCallId(toolCallId);

                eventConsumer.accept(toolCallEndEvent);

                return "";
            }
        };
    }
}
