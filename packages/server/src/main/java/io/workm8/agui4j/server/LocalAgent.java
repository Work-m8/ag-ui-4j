package io.workm8.agui4j.server;

import io.workm8.agui4j.core.agent.Agent;
import io.workm8.agui4j.core.agent.AgentSubscriber;
import io.workm8.agui4j.core.agent.RunAgentInput;
import io.workm8.agui4j.core.agent.RunAgentParameters;
import io.workm8.agui4j.core.event.*;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.state.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class LocalAgent implements Agent {

    protected String threadId;
    protected State state;
    protected List<BaseMessage> messages = new ArrayList<>();

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

        CompletableFuture.runAsync(() -> this.run(input, subscriber));

        return future;
    }

    protected abstract void run(RunAgentInput input, AgentSubscriber subscriber);

    /**
     * Emits an event to the subscriber using the appropriate event-specific method.
     * <p>
     * This protected utility method handles the dual emission pattern used throughout
     * the ag-ui-4j framework:
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
