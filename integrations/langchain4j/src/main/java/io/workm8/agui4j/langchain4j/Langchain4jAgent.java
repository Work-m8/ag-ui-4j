package io.workm8.agui4j.langchain4j;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.workm8.agui4j.core.agent.*;
import io.workm8.agui4j.core.message.AssistantMessage;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.state.State;
import io.workm8.agui4j.server.LocalAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.workm8.agui4j.server.EventFactory.*;

/**
 * Agent implementation that integrates with LangChain4j's StreamingChatModel for AI conversations.
 * <p>
 * Langchain4jAgent provides a bridge between the ag-ui-4j agent framework and LangChain4j's
 * streaming chat models. It handles the conversion between ag-ui-4j's message and tool formats
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
 * The agent maintains conversation state and history, converting ag-ui-4j messages to
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
public class Langchain4jAgent extends LocalAgent {

    private final StreamingChatModel chatModel;

    private final MessageMapper messageMapper;
    private final ToolMapper toolMapper;

    /**
     * Constructs a new Langchain4jAgent with the specified configuration.
     * <p>
     * This constructor initializes the agent with a LangChain4j streaming chat model,
     * conversation history, and state management. The message and tool mappers are
     * automatically created to handle format conversions.
     *
     */
    private Langchain4jAgent(
        final Builder builder
    ) {
        super(builder.agentId, builder.threadId, builder.instructions, builder.messages);

        this.chatModel = builder.chatModel;
        this.state = builder.state;

        this.messageMapper = new MessageMapper();
        this.toolMapper = new ToolMapper();
    }

    /**
     * Executes the core agent logic by interacting with the LangChain4j chat model.
     * <p>
     * This protected method handles the main execution flow:
     * <ul>
     * <li>Converts ag-ui-4j messages to LangChain4j format using MessageMapper</li>
     * <li>Maps ag-ui-4j tools to LangChain4j ToolSpecifications using ToolMapper</li>
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

        var langchainMessages = this.messages
            .stream()
            .map(this.messageMapper::toLangchainMessage)
            .toList();

        var request = ChatRequest.builder()
            .messages(langchainMessages)
            .toolSpecifications(
                input.tools()
                    .stream()
                    .map(this.toolMapper::toLangchainTool)
                    .toList()
            )
            .build();

        var agent = this;

        this.emitEvent(runStartedEvent(input.threadId(), input.runId()), subscriber);

        this.emitEvent(textMessageStartEvent(messageId, "assistant"), subscriber);

        chatModel.chat(
            request,
            new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String res) {
                    agent.emitEvent(textMessageContentEvent(messageId, res), subscriber);
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    agent.emitEvent(textMessageEndEvent(messageId), subscriber);

                    if (chatResponse.aiMessage().hasToolExecutionRequests()) {
                        chatResponse.aiMessage().toolExecutionRequests()
                            .forEach(toolExecutionRequest -> {
                                var toolCallId = UUID.randomUUID().toString();

                                agent.emitEvent(toolCallStartEvent(messageId, toolExecutionRequest.name(), toolCallId), subscriber);
                                agent.emitEvent(toolCallArgsEvent(toolExecutionRequest.arguments(), toolCallId), subscriber);
                                agent.emitEvent(toolCallEndEvent(toolCallId), subscriber);
                            });
                    }

                    agent.emitEvent(runFinishedEvent(input.threadId(), input.runId()), subscriber);

                    var assistantMessage = new AssistantMessage();
                    assistantMessage.setId(messageId);
                    assistantMessage.setName("assistant");
                    assistantMessage.setToolCalls(new ArrayList<>());

                    final List<BaseMessage> inputMessages = input.messages();

                    inputMessages.add(assistantMessage);

                    subscriber.onRunFinalized(new AgentSubscriberParams(inputMessages, state, agent, input));
                }

                @Override
                public void onError(Throwable throwable) {
                    agent.emitEvent(runErrorEvent(throwable.getMessage()), subscriber);
                }
            }
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String agentId;
        private String instructions;
        private State state;
        private String threadId;
        private StreamingChatModel chatModel;
        private List<BaseMessage> messages = new ArrayList<>();

        public Builder agentId(final String agentId) {
            this.agentId = agentId;

            return this;
        }

        public Builder instructions(final String instructions) {
            this.instructions = instructions;

            return this;
        }

        public Builder state(final State state) {
            this.state = state;

            return this;
        }

        public Builder streamingChatModel(final StreamingChatModel chatModel) {
            this.chatModel = chatModel;

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

        public Builder threadId(final String threadId) {
            this.threadId = threadId;

            return this;
        }


        public Langchain4jAgent build() {
            return new Langchain4jAgent(this);
        }
    }
}