package io.workm8.agui4j.server;

import io.workm8.agui4j.core.agent.AgentSubscriber;
import io.workm8.agui4j.core.agent.RunAgentInput;
import io.workm8.agui4j.core.agent.RunAgentParameters;
import io.workm8.agui4j.core.event.*;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.message.UserMessage;
import io.workm8.agui4j.core.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("LocalAgent")
class LocalAgentTest {

    private TestLocalAgent agent;
    private AgentSubscriber subscriber;

    @BeforeEach
    void setUp() {
        var messages = List.of(new UserMessage("1", "Hello", "user"));
        agent = new TestLocalAgent("agent-1", "thread-1", "You are helpful", messages);
        subscriber = mock(AgentSubscriber.class);
    }

    @Test
    void shouldInitializeWithParameters() {
        assertThat(agent.getAgentId()).isEqualTo("agent-1");
        assertThat(agent.threadId).isEqualTo("thread-1");
        assertThat(agent.instructions).isEqualTo("You are helpful");
        assertThat(agent.messages).hasSize(1);
        assertThat(agent.messages.get(0).getContent()).isEqualTo("Hello");
    }

    @Test
    void shouldSetThreadId() {
        agent.setThreadId("new-thread");
        
        assertThat(agent.threadId).isEqualTo("new-thread");
    }

    @Test
    void shouldSetState() {
        var state = new State(Map.of("key", "value"));
        
        agent.setState(state);
        
        assertThat(agent.state).isEqualTo(state);
    }

    @Test
    void shouldSetMessages() {
        var newMessages = List.of(
            new UserMessage("2", "Hi", "user"),
            new UserMessage("3", "How are you?", "user")
        );
        
        agent.setMessages(newMessages);
        
        assertThat(agent.messages).hasSize(2);
        assertThat(agent.messages.get(0).getContent()).isEqualTo("Hi");
        assertThat(agent.messages.get(1).getContent()).isEqualTo("How are you?");
    }

    @Test
    void shouldAddMessage() {
        var newMessage = new UserMessage("2", "Hi there", "user");
        
        agent.addMessage(newMessage);
        
        assertThat(agent.messages).hasSize(2);
        assertThat(agent.messages.get(1).getContent()).isEqualTo("Hi there");
    }

    @Test
    void shouldAddMessageToEmptyList() {
        agent.setMessages(null);
        var newMessage = new UserMessage("1", "First message", "user");
        
        agent.addMessage(newMessage);
        
        assertThat(agent.messages).hasSize(1);
        assertThat(agent.messages.get(0).getContent()).isEqualTo("First message");
    }

    @Test
    void shouldRunAgent() {
        var parameters = new RunAgentParameters("run-123");
        
        var future = agent.runAgent(parameters, subscriber);
        
        assertThat(future).isNotNull();
        assertThat(agent.lastRunInput).isNotNull();
        assertThat(agent.lastRunInput.threadId()).isEqualTo("thread-1");
        assertThat(agent.lastRunInput.runId()).isEqualTo("run-123");
        assertThat(agent.lastRunInput.messages()).hasSize(1);
    }

    @Test
    void shouldGenerateRunIdIfNotProvided() {
        var parameters = new RunAgentParameters(null);
        
        agent.runAgent(parameters, subscriber);
        
        assertThat(agent.lastRunInput.runId()).isNotNull();
        assertThat(agent.lastRunInput.runId()).isNotEmpty();
    }

    @Test
    void shouldEmitRunStartedEvent() {
        var event = new RunStartedEvent();
        event.setThreadId("thread-1");
        event.setRunId("run-123");
        
        agent.emitEvent(event, subscriber);
        
        verify(subscriber).onEvent(event);
        verify(subscriber).onRunStartedEvent(event);
    }

    @Test
    void shouldEmitTextMessageStartEvent() {
        var event = new TextMessageStartEvent();
        event.setMessageId("msg-1");
        
        agent.emitEvent(event, subscriber);
        
        verify(subscriber).onEvent(event);
        verify(subscriber).onTextMessageStartEvent(event);
    }

    @Test
    void shouldEmitTextMessageContentEvent() {
        var event = new TextMessageContentEvent();
        event.setDelta("Hello");
        
        agent.emitEvent(event, subscriber);
        
        verify(subscriber).onEvent(event);
        verify(subscriber).onTextMessageContentEvent(event);
    }

    @Test
    void shouldConvertTextMessageChunkToContentEvent() {
        var chunkEvent = new TextMessageChunkEvent();
        chunkEvent.setMessageId("msg-1");
        chunkEvent.setDelta("Hello");
        chunkEvent.setTimestamp(12345L);
        chunkEvent.setRawEvent("raw");
        
        agent.emitEvent(chunkEvent, subscriber);
        
        verify(subscriber).onEvent(chunkEvent);
        verify(subscriber).onTextMessageContentEvent(argThat(event -> 
            event.getMessageId().equals("msg-1") &&
            event.getDelta().equals("Hello") &&
            event.getTimestamp() == 12345L &&
            event.getRawEvent().equals("raw")
        ));
    }

    @Test
    void shouldEmitToolCallEvents() {
        var startEvent = new ToolCallStartEvent();
        var argsEvent = new ToolCallArgsEvent();
        var endEvent = new ToolCallEndEvent();
        var resultEvent = new ToolCallResultEvent();
        
        agent.emitEvent(startEvent, subscriber);
        agent.emitEvent(argsEvent, subscriber);
        agent.emitEvent(endEvent, subscriber);
        agent.emitEvent(resultEvent, subscriber);
        
        verify(subscriber).onToolCallStartEvent(startEvent);
        verify(subscriber).onToolCallArgsEvent(argsEvent);
        verify(subscriber).onToolCallEndEvent(endEvent);
        verify(subscriber).onToolCallResultEvent(resultEvent);
    }

    @Test
    void shouldEmitCustomAndRawEvents() {
        var customEvent = new CustomEvent();
        var rawEvent = new RawEvent();
        
        agent.emitEvent(customEvent, subscriber);
        agent.emitEvent(rawEvent, subscriber);
        
        verify(subscriber).onCustomEvent(customEvent);
        verify(subscriber).onRawEvent(rawEvent);
    }

    static class TestLocalAgent extends LocalAgent {
        RunAgentInput lastRunInput;

        public TestLocalAgent(String agentId, String threadId, String instructions, List<BaseMessage> messages) {
            super(agentId, threadId, instructions, messages);
        }

        @Override
        protected void run(RunAgentInput input, AgentSubscriber subscriber) {
            this.lastRunInput = input;
            // Test implementation - just store the input for verification
        }
    }
}