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

    interface Assistant {

        TokenStream chat(final String message);

        Response<String> directChat(final String message);
    }
}
