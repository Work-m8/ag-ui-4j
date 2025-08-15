package io.workm8.agui4j.spring.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workm8.agui4j.core.agent.*;
import io.workm8.agui4j.core.event.*;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.state.State;
import io.workm8.agui4j.server.LocalAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.*;

import static io.workm8.agui4j.server.EventFactory.*;

/**
 * Agent implementation that integrates with Spring AI's ChatClient for AI conversations.
 * <p>
 * SpringAIAgent provides a bridge between the agui4j agent framework and Spring AI's
 * chat functionality. It handles the conversion between agui4j's message and tool formats
 * and Spring AI's native formats, enabling seamless integration with various LLM providers
 * supported by Spring AI.
 * <p>
 * Key features:
 * <ul>
 * <li>Streaming chat responses with real-time event emission</li>
 * <li>Tool callback support with deferred event processing</li>
 * <li>Message format conversion between agui4j and Spring AI</li>
 * <li>Asynchronous execution with reactive stream handling</li>
 * <li>Mutable state and conversation history management</li>
 * <li>JSON schema generation for tool definitions</li>
 * </ul>
 * <p>
 * Unlike immutable agent implementations, SpringAIAgent allows for dynamic updates
 * to thread ID, state, and message history through setter methods, making it suitable
 * for long-lived conversations and stateful interactions.
 * <p>
 * The agent uses Spring AI's reactive streaming capabilities to process responses
 * and emits appropriate events through the subscriber interface. Tool calls are
 * handled through deferred event processing to maintain proper event ordering.
 * <p>
 * Example usage:
 * <pre>{@code
 * ChatClient chatClient = ChatClient.builder(chatModel)
 *     .build();
 *
 * SpringAIAgent agent = new SpringAIAgent(
 *     "thread-123",
 *     chatClient,
 *     initialMessages
 * );
 *
 * agent.setState(new State());
 * agent.runAgent(parameters, subscriber);
 * }</pre>
 *
 * @author Pascal Wilbrink
 */
public class SpringAIAgent extends LocalAgent {

    private final ChatClient chatClient;

    private final MessageMapper messageMapper;
    private final ToolMapper toolMapper;
    private final ObjectMapper objectMapper;

    private final List<Advisor> advisors;

    private final List<ToolCallback> toolCallbacks;

    /**
     * Constructs a new SpringAIAgent with the specified configuration.
     * <p>
     * This constructor initializes the agent with a Spring AI ChatClient and
     * initial conversation history. The message mapper and object mapper are
     * automatically created to handle format conversions and JSON serialization.
     *
     */
    protected SpringAIAgent(
        Builder builder
    ) {
        super(builder.agentId, builder.threadId, builder.instructions, builder.messages);

        this.chatClient = builder.chatClient;

        this.messageMapper = new MessageMapper();
        this.objectMapper = new ObjectMapper();

        this.toolMapper = new ToolMapper(this.objectMapper);

        this.advisors = builder.advisors;
        this.toolCallbacks = builder.toolCallbacks;
    }

    /**
     * Executes the core agent logic by interacting with the Spring AI ChatClient.
     * <p>
     * This private method handles the main execution flow:
     * <ul>
     * <li>Converts agui4j messages to Spring AI format using MessageMapper</li>
     * <li>Creates ToolCallback implementations for each agui4j tool</li>
     * <li>Generates JSON schemas for tool definitions using ObjectMapper</li>
     * <li>Uses Spring AI's reactive streaming to process chat responses</li>
     * <li>Handles deferred tool call events to maintain proper event ordering</li>
     * <li>Emits appropriate events for text generation and completion</li>
     * </ul>
     * <p>
     * The method uses Spring AI's reactive streaming approach, subscribing to
     * chat responses and emitting events in real-time. Tool call events are
     * collected during tool execution and emitted after message completion
     * to ensure proper event sequencing.
     *
     * @param input      the complete input parameters including messages, tools, and context
     * @param subscriber the subscriber to receive events and lifecycle notifications
     */
    protected void run(RunAgentInput input, AgentSubscriber subscriber) {
        var messageId = UUID.randomUUID().toString();

        var messages = input.messages()
            .stream().map(this.messageMapper::toSpringMessage)
            .toList();

        final List<BaseEvent> deferredToolCallEvents = new ArrayList<>();

        this.emitEvent(
            runStartedEvent(input.runId(), input.threadId()),
            subscriber
        );

        this.emitEvent(
            textMessageStartEvent(messageId, "assistant"),
            subscriber
        );

        ChatClient.ChatClientRequestSpec chatRequest = this.chatClient.prompt(
            Prompt
                .builder()
                .messages(messages.toArray(new AbstractMessage[0]))
                .build()
            );

        if (!input.tools().isEmpty()) {
            List<ToolCallback> toolCallbacks = input.tools()
                    .stream()
                    .map((tool) -> this.toolMapper.toSpringTool(
                            tool,
                            messageId,
                            deferredToolCallEvents::add
                    )).toList();
            chatRequest = chatRequest.toolCallbacks(toolCallbacks);
        }

        if (!this.toolCallbacks.isEmpty()) {
            chatRequest = chatRequest.toolCallbacks(this.toolCallbacks);
        }

        if (!this.advisors.isEmpty()) {
            chatRequest = chatRequest.advisors(this.advisors);
        }

        chatRequest.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, this.threadId));

        if (StringUtils.hasText(this.instructions)) {
            chatRequest = chatRequest.system(this.instructions);
        }

        chatRequest.stream()
            .chatResponse()
            .subscribe(
                evt -> {
                    if (StringUtils.hasText(evt.getResult().getOutput().getText())) {
                        this.emitEvent(
                            textMessageContentEvent(messageId, evt.getResult().getOutput().getText()),
                            subscriber
                        );
                    }
                },
                err -> {
                    var runErrorEvent = new RunErrorEvent();
                    runErrorEvent.setError(err.getMessage());

                    this.emitEvent(runErrorEvent, subscriber);
                },
                () -> {
                    this.emitEvent(textMessageEndEvent(messageId), subscriber);

                    deferredToolCallEvents.forEach(deferredToolCallEvent ->
                        this.emitEvent(deferredToolCallEvent, subscriber)
                    );

                    this.emitEvent(runFinishedEvent(input.threadId(), input.runId()), subscriber);

                    subscriber.onRunFinalized(new AgentSubscriberParams(this.messages, state, this, input));
                }
            );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<Advisor> advisors = new ArrayList<>();

        private final List<BaseMessage> messages = new ArrayList<>();

        private final List<ToolCallback> toolCallbacks = new ArrayList<>();

        private String agentId;
        private String threadId;
        private String instructions;
        private State state;

        private ChatClient chatClient;

        public Builder advisors(final List<Advisor> advisors) {
            this.advisors.addAll(advisors);

            return this;
        }

        public Builder advisor(final Advisor advisor) {
            this.advisors.add(advisor);

            return this;
        }

        public Builder message(final BaseMessage message) {
            this.messages.add(message);

            return this;
        }

        public Builder messages(final List<BaseMessage> messages) {
            this.messages.addAll(messages);

            return this;
        }

        public Builder agentId(final String agentId) {
            this.agentId = agentId;

            return this;
        }

        public Builder threadId(final String threadId) {
            this.threadId = threadId;

            return this;
        }

        public Builder instructions(final String instructions) {
            this.instructions = instructions;

            return this;
        }

        public Builder chatClient(final ChatClient chatClient) {
            this.chatClient = chatClient;

            return this;
        }

        public Builder state(final State state) {
            this.state = state;

            return this;
        }

        public Builder toolCallbacks(final List<ToolCallback> toolCallbacks) {
            this.toolCallbacks.addAll(toolCallbacks);

            return this;
        }

        public Builder toolCallback(final ToolCallback toolCallback) {
            this.toolCallbacks.add(toolCallback);

            return this;
        }





        public SpringAIAgent build() {
            return new SpringAIAgent(this);
        }
    }

}