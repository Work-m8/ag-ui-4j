package io.workm8.agui4j.spring.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workm8.agui4j.core.agent.*;
import io.workm8.agui4j.core.event.*;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.state.State;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

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
public class SpringAIAgent implements Agent {

    private final ChatClient chatClient;

    private final MessageMapper messageMapper;

    private String threadId;

    private State state;

    private List<BaseMessage> messages;

    private final ObjectMapper objectMapper;

    /**
     * Constructs a new SpringAIAgent with the specified configuration.
     * <p>
     * This constructor initializes the agent with a Spring AI ChatClient and
     * initial conversation history. The message mapper and object mapper are
     * automatically created to handle format conversions and JSON serialization.
     *
     * @param threadId    unique identifier for the conversation thread
     * @param chatClient  the Spring AI ChatClient for AI communication
     * @param messages    the initial conversation history as agui4j BaseMessage objects
     */
    public SpringAIAgent(
            final String threadId,
            final ChatClient chatClient,
            final List<BaseMessage> messages
    ) {
        this.threadId = threadId;

        this.chatClient = chatClient;

        this.messageMapper = new MessageMapper();

        this.messages = messages;

        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sets the thread identifier for this agent.
     * <p>
     * This method allows for dynamic updates to the conversation thread,
     * enabling the agent to be reused across different conversation contexts.
     *
     * @param threadId the new thread identifier
     */
    public void setThreadId(final String threadId) {
        this.threadId = threadId;
    }

    /**
     * Sets the agent's internal state.
     * <p>
     * This method allows for dynamic state updates, enabling the agent to
     * maintain and update context information across interactions.
     *
     * @param state the new agent state
     */
    public void setState(final State state) {
        this.state = state;
    }

    /**
     * Replaces the entire conversation history with new messages.
     * <p>
     * This method allows for complete replacement of the conversation context,
     * useful for resetting conversations or loading different conversation histories.
     *
     * @param messages the new conversation history
     */
    public void setMessages(final List<BaseMessage> messages) {
        this.messages = messages;
    }

    /**
     * Adds a single message to the conversation history.
     * <p>
     * This method appends a new message to the existing conversation, creating
     * the message list if it doesn't exist. This is useful for incrementally
     * building conversation history.
     *
     * @param message the message to add to the conversation
     */
    public void addMessage(final BaseMessage message) {
        if (Objects.isNull(this.messages)) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    /**
     * Executes the agent asynchronously with the specified parameters and subscriber.
     * <p>
     * This method implements the Agent interface by creating a RunAgentInput from
     * the provided parameters and initiating asynchronous execution. The agent will
     * use Spring AI's ChatClient to process the conversation and emit events through
     * the provided subscriber.
     * <p>
     * The execution includes:
     * <ul>
     * <li>Message format conversion from agui4j to Spring AI</li>
     * <li>Tool definition creation with JSON schema generation</li>
     * <li>Reactive streaming response handling with real-time event emission</li>
     * <li>Deferred tool call event processing for proper ordering</li>
     * </ul>
     *
     * @param parameters the configuration parameters for this agent execution,
     *                  including tools, context, and other execution settings
     * @param subscriber the callback interface for receiving real-time updates
     *                  about agent execution progress, events, and state changes
     * @return a CompletableFuture that completes when the agent execution finishes
     */
    @Override
    public CompletableFuture<Void> runAgent(RunAgentParameters parameters, AgentSubscriber subscriber) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        var input = new RunAgentInput(
                this.threadId,
                Objects.isNull(parameters.getRunId())
                        ? UUID.randomUUID().toString()
                        : parameters.getRunId(),
                this.state,
                this.messages,
                parameters.getTools(),
                parameters.getContext(),
                parameters.getForwardedProps()
        );

        CompletableFuture.runAsync(() -> {
            this.run(input, subscriber);
        });
        return future;
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
    private void run(RunAgentInput input, AgentSubscriber subscriber) {
        var messages = input.messages()
                .stream().map(this.messageMapper::toSpringMessage)
                .toList();

        final List<BaseEvent> deferredToolCallEvents = new ArrayList<>();

        var runStartedEvent = new RunStartedEvent();
        runStartedEvent.setRunId(input.runId());
        runStartedEvent.setThreadId(input.threadId());

        this.emitEvent(runStartedEvent, subscriber);

        var messageId = UUID.randomUUID().toString();

        var messageStartEvent = new TextMessageStartEvent();
        messageStartEvent.setMessageId(messageId);
        messageStartEvent.setRole("assistant");

        this.emitEvent(messageStartEvent, subscriber);

        List<ToolCallback> toolCallbacks = input.tools()
                .stream()
                .map((tool) -> new ToolCallback() {
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

                        deferredToolCallEvents.add(toolCallStartEvent);

                        var toolCallArgsEvent = new ToolCallArgsEvent();
                        toolCallArgsEvent.setDelta(toolInput);
                        toolCallArgsEvent.setToolCallId(toolCallId);

                        deferredToolCallEvents.add(toolCallArgsEvent);

                        var toolCallEndEvent = new ToolCallEndEvent();
                        toolCallEndEvent.setToolCallId(toolCallId);

                        deferredToolCallEvents.add(toolCallEndEvent);

                        return "";
                    }
                }).collect(toList());


        this.chatClient.prompt(
                        Prompt
                                .builder()
                                .messages(messages.toArray(new AbstractMessage[0]))
                                .build()
                )
                .toolCallbacks(toolCallbacks)
                .stream()
                .chatResponse()
                .subscribe(
                        (evt) -> {
                            if (StringUtils.hasText(evt.getResult().getOutput().getText())) {
                                var messageContentEvent = new TextMessageContentEvent();
                                messageContentEvent.setMessageId(messageId);
                                messageContentEvent.setDelta(evt.getResult().getOutput().getText());
                                this.emitEvent(messageContentEvent, subscriber);
                            }
                        },
                        (err) -> {
                            var runErrorEvent = new RunErrorEvent();
                            runErrorEvent.setError(err.getMessage());

                            this.emitEvent(runErrorEvent, subscriber);
                        },
                        () -> {
                            var messageEndEvent = new TextMessageEndEvent();
                            messageEndEvent.setMessageId(messageId);
                            this.emitEvent(messageEndEvent, subscriber);

                            deferredToolCallEvents.forEach(deferredToolCallEvent -> {
                                this.emitEvent(deferredToolCallEvent, subscriber);
                            });

                            var runFinishedEvent = new RunFinishedEvent();
                            runFinishedEvent.setThreadId(input.threadId());
                            runFinishedEvent.setRunId(input.runId());
                            this.emitEvent(runFinishedEvent, subscriber);

                            subscriber.onRunFinalized(new AgentSubscriberParams(this.messages, state, this, input));
                        }
                );


    }

    /**
     * Emits an event to the subscriber using the appropriate event-specific method.
     * <p>
     * This protected utility method handles the dual emission pattern used throughout
     * the agui4j framework:
     * <ul>
     * <li>First calls the generic onEvent method for universal event handling</li>
     * <li>Then calls the specific event type method for targeted processing</li>
     * </ul>
     * <p>
     * The method includes special handling for TEXT_MESSAGE_CHUNK events, converting
     * them to TEXT_MESSAGE_CONTENT events for compatibility with the subscriber interface.
     * <p>
     * This centralized emission method ensures consistent event handling and makes it
     * easier to maintain the event dispatching logic.
     *
     * @param event      the event to emit to the subscriber
     * @param subscriber the subscriber that should receive the event
     */
    protected void emitEvent(final BaseEvent event, final AgentSubscriber subscriber) {
        subscriber.onEvent(event);

        switch (event.getType()) {
            case RAW -> subscriber.onRawEvent((RawEvent) event);
            case CUSTOM -> subscriber.onCustomEvent((CustomEvent) event);
            case RUN_STARTED -> subscriber.onRunStartedEvent((RunStartedEvent) event);
            case RUN_ERROR -> subscriber.onRunErrorEvent((RunErrorEvent) event);
            case RUN_FINISHED -> subscriber.onRunFinishedEvent((RunFinishedEvent) event);
            case STEP_STARTED -> subscriber.onStepStartedEvent((StepStartedEvent) event);
            case STEP_FINISHED -> subscriber.onStepFinishedEvent((StepFinishedEvent) event);
            case TEXT_MESSAGE_START -> subscriber.onTextMessageStartEvent((TextMessageStartEvent) event);
            case TEXT_MESSAGE_CHUNK -> {
                var chunkEvent = (TextMessageChunkEvent)event;
                var textMessageContentEvent = new TextMessageContentEvent();
                textMessageContentEvent.setDelta(chunkEvent.getDelta());
                textMessageContentEvent.setMessageId(chunkEvent.getMessageId());
                textMessageContentEvent.setTimestamp(chunkEvent.getTimestamp());
                textMessageContentEvent.setRawEvent(chunkEvent.getRawEvent());
                subscriber.onTextMessageContentEvent(textMessageContentEvent);
            }
            case TEXT_MESSAGE_CONTENT -> subscriber.onTextMessageContentEvent((TextMessageContentEvent) event);
            case TEXT_MESSAGE_END -> subscriber.onTextMessageEndEvent((TextMessageEndEvent) event);
            case TOOL_CALL_START -> subscriber.onToolCallStartEvent((ToolCallStartEvent) event);
            case TOOL_CALL_ARGS -> subscriber.onToolCallArgsEvent((ToolCallArgsEvent) event);
            case TOOL_CALL_RESULT -> subscriber.onToolCallResultEvent((ToolCallResultEvent) event);
            case TOOL_CALL_END -> subscriber.onToolCallEndEvent((ToolCallEndEvent) event);
        }
    }
}