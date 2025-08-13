package io.workm8.agui4j.spring.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workm8.agui4j.core.agent.*;
import io.workm8.agui4j.core.event.*;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.state.State;
import io.workm8.agui4j.server.LocalAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
public class SpringAIAgent extends LocalAgent {

    private final ChatClient chatClient;

    private final MessageMapper messageMapper;
    private final ToolMapper toolMapper;
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
        this.toolMapper = new ToolMapper(this.objectMapper);
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
            .map((tool) -> this.toolMapper.toSpringTool(
                tool,
                messageId,
                deferredToolCallEvents::add
            )).toList();

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
                evt -> {
                    if (StringUtils.hasText(evt.getResult().getOutput().getText())) {
                        var messageContentEvent = new TextMessageContentEvent();
                        messageContentEvent.setMessageId(messageId);
                        messageContentEvent.setDelta(evt.getResult().getOutput().getText());
                        this.emitEvent(messageContentEvent, subscriber);
                    }
                },
                err -> {
                    var runErrorEvent = new RunErrorEvent();
                    runErrorEvent.setError(err.getMessage());

                    this.emitEvent(runErrorEvent, subscriber);
                },
                () -> {
                    var messageEndEvent = new TextMessageEndEvent();
                    messageEndEvent.setMessageId(messageId);
                    this.emitEvent(messageEndEvent, subscriber);

                    deferredToolCallEvents.forEach(deferredToolCallEvent ->
                        this.emitEvent(deferredToolCallEvent, subscriber)
                    );

                    var runFinishedEvent = new RunFinishedEvent();
                    runFinishedEvent.setThreadId(input.threadId());
                    runFinishedEvent.setRunId(input.runId());
                    this.emitEvent(runFinishedEvent, subscriber);

                    subscriber.onRunFinalized(new AgentSubscriberParams(this.messages, state, this, input));
                }
            );
    }

}