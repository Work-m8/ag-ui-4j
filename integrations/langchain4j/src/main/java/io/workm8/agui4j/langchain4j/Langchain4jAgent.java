package io.workm8.agui4j.langchain4j;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.workm8.agui4j.core.agent.*;
import io.workm8.agui4j.core.event.*;
import io.workm8.agui4j.core.message.AssistantMessage;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.state.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent implementation that integrates with LangChain4j's StreamingChatModel for AI conversations.
 * <p>
 * Langchain4jAgent provides a bridge between the agui4j agent framework and LangChain4j's
 * streaming chat models. It handles the conversion between agui4j's message and tool formats
 * and LangChain4j's native formats, enabling seamless integration with various LLM providers
 * supported by LangChain4j.
 * <p>
 * Key features:
 * <ul>
 * <li>Streaming chat responses with real-time event emission</li>
 * <li>Tool call support with proper event lifecycle management</li>
 * <li>Message format conversion between agui4j and LangChain4j</li>
 * <li>Asynchronous execution with proper error handling</li>
 * <li>State and conversation history management</li>
 * </ul>
 * <p>
 * The agent maintains conversation state and history, converting agui4j messages to
 * LangChain4j format for model consumption and handling streaming responses by emitting
 * appropriate events through the subscriber interface.
 * <p>
 * Example usage:
 * <pre>{@code
 * StreamingChatModel model = OpenAiStreamingChatModel.builder()
 *     .apiKey("your-api-key")
 *     .build();
 *
 * Langchain4jAgent agent = new Langchain4jAgent(
 *     "thread-123",
 *     new State(),
 *     model,
 *     initialMessages
 * );
 * }</pre>
 *
 * @author Pascal Wilbrink
 */
public class Langchain4jAgent implements Agent {

    private final StreamingChatModel chatModel;

    private final List<BaseMessage> messages;

    private final String threadId;
    private final State state;

    private final MessageMapper messageMapper;
    private final ToolMapper toolMapper;

    /**
     * Constructs a new Langchain4jAgent with the specified configuration.
     * <p>
     * This constructor initializes the agent with a LangChain4j streaming chat model,
     * conversation history, and state management. The message and tool mappers are
     * automatically created to handle format conversions.
     *
     * @param threadId   unique identifier for the conversation thread
     * @param state      the initial agent state for maintaining context
     * @param chatModel  the LangChain4j StreamingChatModel for AI communication
     * @param messages   the initial conversation history as agui4j BaseMessage objects
     */
    public Langchain4jAgent(
            final String threadId,
            final State state,
            final StreamingChatModel chatModel,
            final List<BaseMessage> messages
    ) {
        this.threadId = threadId;
        this.state = state;
        this.chatModel = chatModel;
        this.messages = messages;

        this.messageMapper = new MessageMapper();
        this.toolMapper = new ToolMapper();
    }

    /**
     * Executes the agent asynchronously with the specified parameters and subscriber.
     * <p>
     * This method implements the Agent interface by creating a RunAgentInput from
     * the provided parameters and initiating asynchronous execution. The agent will
     * use the LangChain4j streaming chat model to process the conversation and emit
     * events through the provided subscriber.
     * <p>
     * The execution includes:
     * <ul>
     * <li>Message format conversion from agui4j to LangChain4j</li>
     * <li>Tool specification mapping for function calling</li>
     * <li>Streaming response handling with real-time event emission</li>
     * <li>Tool call processing and result handling</li>
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
     * Executes the core agent logic by interacting with the LangChain4j chat model.
     * <p>
     * This protected method handles the main execution flow:
     * <ul>
     * <li>Converts agui4j messages to LangChain4j format using MessageMapper</li>
     * <li>Maps agui4j tools to LangChain4j ToolSpecifications using ToolMapper</li>
     * <li>Creates and sends a ChatRequest to the streaming chat model</li>
     * <li>Handles streaming responses through a custom StreamingChatResponseHandler</li>
     * <li>Emits appropriate events for text generation and tool calls</li>
     * <li>Manages conversation state and message history updates</li>
     * </ul>
     * <p>
     * The method uses a streaming approach, emitting events in real-time as the
     * LangChain4j model generates responses, ensuring responsive user interfaces
     * and real-time feedback.
     *
     * @param input      the complete input parameters including messages, tools, and context
     * @param subscriber the subscriber to receive events and lifecycle notifications
     */
    protected void run(RunAgentInput input, AgentSubscriber subscriber) {
        var messageId = UUID.randomUUID().toString();

        var langchainMessages = this.messages.stream().map(this.messageMapper::toLangchainMessage).toList();

        var request = ChatRequest.builder()
                .messages(langchainMessages)
                .toolSpecifications(
                        input.tools().stream().map(this.toolMapper::toLangchainTool).toList()
                )
                .build();

        var agent = this;

        var runStartedEvent = new RunStartedEvent();
        runStartedEvent.setRunId(input.runId());
        runStartedEvent.setThreadId(input.threadId());

        this.emitEvent(runStartedEvent, subscriber);

        var messageStartEvent = new TextMessageStartEvent();
        messageStartEvent.setMessageId(messageId);
        messageStartEvent.setRole("assistant");

        this.emitEvent(messageStartEvent, subscriber);

        chatModel.chat(
                request,
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String res) {
                        var messageContentEvent = new TextMessageContentEvent();
                        messageContentEvent.setMessageId(messageId);
                        messageContentEvent.setDelta(res);

                        agent.emitEvent(messageContentEvent, subscriber);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse chatResponse) {
                        var messageEndEvent = new TextMessageEndEvent();
                        messageEndEvent.setMessageId(messageId);

                        agent.emitEvent(messageEndEvent, subscriber);

                        if (chatResponse.aiMessage().hasToolExecutionRequests()) {
                            chatResponse.aiMessage().toolExecutionRequests()
                                    .forEach(toolExecutionRequest -> {
                                        var toolCallId = UUID.randomUUID().toString();

                                        var toolCallStartEvent = new ToolCallStartEvent();
                                        toolCallStartEvent.setParentMessageId(messageId);
                                        toolCallStartEvent.setToolCallName(toolExecutionRequest.name());
                                        toolCallStartEvent.setToolCallId(toolCallId);

                                        agent.emitEvent(toolCallStartEvent, subscriber);

                                        var toolCallArgsEvent = new ToolCallArgsEvent();
                                        toolCallArgsEvent.setDelta(toolExecutionRequest.arguments());
                                        toolCallArgsEvent.setToolCallId(toolCallId);

                                        agent.emitEvent(toolCallArgsEvent, subscriber);

                                        var toolCallEndEvent = new ToolCallEndEvent();
                                        toolCallEndEvent.setToolCallId(toolCallId);

                                        agent.emitEvent(toolCallEndEvent, subscriber);
                                    });
                        }
                        var runFinishedEvent = new RunFinishedEvent();
                        runFinishedEvent.setRunId(input.runId());
                        runFinishedEvent.setThreadId(threadId);

                        agent.emitEvent(runFinishedEvent, subscriber);

                        var assistantMessage = new AssistantMessage();
                        assistantMessage.setId(messageId);
                        assistantMessage.setName("assistant");
                        assistantMessage.setToolCalls(new ArrayList<>());

                        final List<BaseMessage> messages = input.messages();

                        messages.add(assistantMessage);

                        subscriber.onRunFinalized(new AgentSubscriberParams(messages, state, agent, input));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        var runErrorEvent = new RunErrorEvent();
                        runErrorEvent.setError(throwable.getMessage());

                        agent.emitEvent(runErrorEvent, subscriber);
                    }

                    @Override
                    public void onPartialToolCall(PartialToolCall partialToolCall) {
                    }

                    @Override
                    public void onCompleteToolCall(CompleteToolCall completeToolCall) {

                    }
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