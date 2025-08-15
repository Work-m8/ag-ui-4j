package io.workm8.agui4j.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolProvider;
import io.workm8.agui4j.core.agent.AgentSubscriber;
import io.workm8.agui4j.core.agent.AgentSubscriberParams;
import io.workm8.agui4j.core.agent.RunAgentInput;
import io.workm8.agui4j.core.event.ToolCallResultEvent;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.server.EventFactory;
import io.workm8.agui4j.server.LocalAgent;

import java.util.*;
import java.util.function.Function;

import static io.workm8.agui4j.server.EventFactory.*;

/**
 * Advanced agent implementation that integrates with LangChain4j's AiServices framework.
 * <p>
 * LangchainAgent provides a more sophisticated integration with LangChain4j compared to the
 * basic Langchain4jAgent. It leverages LangChain4j's AiServices builder pattern to create
 * a fully-featured assistant with comprehensive tool support, memory management, and flexible
 * model configuration options.
 * <p>
 * Key features:
 * <ul>
 * <li>Support for both streaming and non-streaming chat models</li>
 * <li>Built-in chat memory management for conversation persistence</li>
 * <li>Comprehensive tool integration with automatic execution and result handling</li>
 * <li>Custom tool provider support for dynamic tool discovery</li>
 * <li>Hallucinated tool name strategy for error handling</li>
 * <li>Real-time event streaming with detailed tool call lifecycle tracking</li>
 * </ul>
 * <p>
 * The agent uses LangChain4j's AiServices framework to build a complete AI assistant
 * that can handle complex conversational flows, tool interactions, and state management.
 * It automatically manages the conversation history, executes tool calls, and emits
 * detailed events for monitoring and user interface updates.
 * <p>
 * Architecture highlights:
 * <ul>
 * <li>Utilizes LangChain4j's service abstraction for clean separation of concerns</li>
 * <li>Supports flexible tool configuration through both direct tools and tool providers</li>
 * <li>Implements proper error handling for tool execution failures</li>
 * <li>Provides real-time progress updates through event emission</li>
 * <li>Maintains conversation context across multiple interactions</li>
 * </ul>
 * <p>
 * This agent is ideal for complex conversational AI scenarios that require:
 * <ul>
 * <li>Persistent conversation memory</li>
 * <li>Extensive tool integration capabilities</li>
 * <li>Robust error handling and recovery</li>
 * <li>Real-time monitoring and feedback</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
 * StreamingChatModel model = OpenAiStreamingChatModel.builder()
 *     .apiKey("your-api-key")
 *     .build();
 *
 * LangchainAgent agent = LangchainAgent.builder()
 *     .agentId("agent-123")
 *     .threadId("thread-456")
 *     .instructions("You are a helpful assistant with access to tools.")
 *     .streamingChatModel(model)
 *     .chatMemory(memory)
 *     .tools(List.of(calculatorTool, weatherTool))
 *     .build();
 * }</pre>
 *
 * @author Pascal Wilbrink
 */
public class LangchainAgent extends LocalAgent {

    private StreamingChatModel streamingChatModel;
    private ChatModel chatModel;

    private ChatMemory chatMemory;

    private final MessageMapper messageMapper;

    private List<Object> tools;

    private ToolProvider toolProvider;

    private Function<ToolExecutionRequest, ToolExecutionResultMessage>  hallucinatedToolNameStrategy;

    private LangchainAgent(final Builder builder) {
        super(builder.agentId, builder.threadId, builder.instructions, builder.messages);

        this.streamingChatModel = builder.streamingChatModel;
        this.chatModel = builder.chatModel;

        this.chatMemory = builder.chatMemory;

        this.messageMapper = new MessageMapper();

        this.tools = builder.tools;
        this.toolProvider = builder.toolProvider;
    }

    @Override
    protected void run(RunAgentInput input, AgentSubscriber subscriber) {
        AiServices<Assistant> builder = AiServices.builder(Assistant.class);

        if (Objects.nonNull(this.streamingChatModel)) {
            builder = builder.streamingChatModel(this.streamingChatModel);
        }

        if (Objects.nonNull(this.chatModel)) {
            builder = builder.chatModel(this.chatModel);
        }

        if (Objects.nonNull(this.chatMemory)) {
            builder = builder.chatMemory(chatMemory);
        }

        if (Objects.nonNull(this.instructions)) {
            builder = builder.systemMessageProvider(chatMemoryId -> instructions);
        }

        if (Objects.nonNull(this.tools) && !this.tools.isEmpty()) {
            builder = builder.tools(this.tools);
        }

        if (Objects.nonNull(this.toolProvider)) {
            builder = builder.toolProvider(this.toolProvider);
        }

        if (Objects.nonNull(this.hallucinatedToolNameStrategy)) {
            builder = builder.hallucinatedToolNameStrategy(this.hallucinatedToolNameStrategy);
        }

        var assistant = builder.build();

        var messages = input.messages().stream().map(this.messageMapper::toLangchainMessage).toList();

        UserMessage lastUserMessage = (UserMessage) messages.stream()
            .filter(m -> m.type().equals(ChatMessageType.USER))
            .reduce((first, second) -> second)
            .orElse(null);

        var otherMessages = messages.stream().filter(m -> m != lastUserMessage).toList();

        chatMemory.add(otherMessages);

        var messageId = UUID.randomUUID().toString();

        var runId = input.runId();

        this.emitEvent(runStartedEvent(threadId, runId), subscriber);

        var deferredToolCalls = new ArrayList<ToolExecution>();

        if (Objects.nonNull(lastUserMessage)) {
            this.emitEvent(textMessageStartEvent(messageId, "assistant"), subscriber);
            try {
                assistant.chat(lastUserMessage.singleText())
                        .onToolExecuted(deferredToolCalls::add)
                        .onCompleteResponse((res) -> {
                            this.emitEvent(textMessageEndEvent(messageId), subscriber);

                            deferredToolCalls.forEach(toolCall -> {
                                var toolCallId = UUID.randomUUID().toString();
                                this.emitEvent(EventFactory.toolCallStartEvent(messageId, toolCall.request().name(), toolCallId), subscriber);
                                this.emitEvent(EventFactory.toolCallArgsEvent(toolCall.request().arguments(), toolCallId), subscriber);
                                this.emitEvent(EventFactory.toolCallEndEvent(toolCallId), subscriber);

                                var result = toolCall.result();

                                var event = new ToolCallResultEvent();
                                event.setToolCallId(toolCallId);
                                event.setRole("tool");
                                event.setMessageId(messageId);
                                event.setContent(result);

                                this.emitEvent(event, subscriber);
                            });
                            this.emitEvent(runFinishedEvent(threadId, runId), subscriber);

                            subscriber.onRunFinalized(new AgentSubscriberParams(input.messages(), state, this, input));
                        })
                        .onError((err) -> this.emitEvent(runErrorEvent(err.getMessage()), subscriber))
                        .onPartialResponse((res) -> this.emitEvent(textMessageContentEvent(messageId, res), subscriber))
                        .start();
            } catch (Exception e) {
                this.emitEvent(runErrorEvent(e.getMessage()), subscriber);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing LangchainAgent instances with flexible configuration.
     * <p>
     * The Builder provides a fluent interface for configuring all aspects of a LangchainAgent,
     * including model selection, memory management, tool configuration, and conversation settings.
     * It supports method chaining for convenient and readable agent construction.
     * <p>
     * Configuration options include:
     * <ul>
     * <li>Agent identification and threading</li>
     * <li>Chat model selection (streaming or non-streaming)</li>
     * <li>Memory management for conversation persistence</li>
     * <li>Tool integration (direct tools or tool providers)</li>
     * <li>Error handling strategies for tool execution</li>
     * <li>Initial conversation messages and system instructions</li>
     * </ul>
     */
    public static class Builder {

        private String threadId;

        private String agentId;
        private String instructions;
        private StreamingChatModel streamingChatModel;
        private ChatModel chatModel;
        private Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy;

        private ChatMemory chatMemory;

        private ToolProvider toolProvider;

        private final List<BaseMessage> messages = new ArrayList<>();

        private final List<Object> tools = new ArrayList<>();

        public Builder threadId(final String threadId) {
            this.threadId = threadId;

            return this;
        }

        public Builder agentId(final String agentId) {
            this.agentId = agentId;

            return this;
        }

        public Builder instructions(final String instructions) {
            this.instructions = instructions;

            return this;
        }

        public Builder chatModel(final ChatModel chatModel) {
            this.chatModel = chatModel;

            return this;
        }

        public Builder streamingChatModel(final StreamingChatModel streamingChatModel) {
            this.streamingChatModel = streamingChatModel;

            return this;
        }

        public Builder hallucinatedToolNameStrategy(Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy) {
            this.hallucinatedToolNameStrategy = hallucinatedToolNameStrategy;

            return this;
        }

        public Builder chatMemory(final ChatMemory chatMemory) {
            this.chatMemory = chatMemory;

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

        public Builder tools(final List<Object> tools) {
            this.tools.addAll(tools);

            return this;
        }

        public Builder tool(final Object tool) {
            this.tools.add(tool);

            return this;
        }

        public Builder toolProvider(final ToolProvider toolProvider) {
            this.toolProvider = toolProvider;

            return this;
        }


        public LangchainAgent build() {
            return new LangchainAgent(this);
        }
    }

    /**
     * Internal interface for LangChain4j AI service integration.
     * <p>
     * This interface defines the contract for the AI assistant service created through
     * LangChain4j's AiServices framework. It provides both streaming and direct chat
     * capabilities, allowing for flexible interaction patterns based on use case requirements.
     * <p>
     * The interface is used internally by the agent to interact with the configured
     * LangChain4j models and tools, abstracting the complexity of the AiServices
     * configuration and providing a clean interface for conversation management.
     */
    interface Assistant {

        /**
         * Initiates a streaming chat conversation with the given message.
         * <p>
         * This method returns a TokenStream that provides real-time access to the
         * AI model's response as it generates tokens. The stream supports event
         * callbacks for partial responses, tool executions, completion, and error handling.
         *
         * @param message the user message to send to the AI assistant
         * @return a TokenStream for receiving streaming response tokens and events
         */
        TokenStream chat(final String message);

        /**
         * Performs a direct (non-streaming) chat interaction with the given message.
         * <p>
         * This method provides a synchronous chat interaction that returns the complete
         * response after the AI model finishes generating it. Useful for scenarios where
         * streaming is not required or where a complete response is needed before proceeding.
         *
         * @param message the user message to send to the AI assistant
         * @return a Response containing the complete AI assistant response
         */
        Response<String> directChat(final String message);
    }
}
