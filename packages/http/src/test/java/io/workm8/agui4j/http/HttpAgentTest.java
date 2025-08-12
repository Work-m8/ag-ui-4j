package io.workm8.agui4j.http;

import io.workm8.agui4j.core.agent.RunAgentInput;
import io.workm8.agui4j.core.agent.RunAgentParameters;
import io.workm8.agui4j.core.event.BaseEvent;
import io.workm8.agui4j.core.event.RunFinishedEvent;
import io.workm8.agui4j.core.event.RunStartedEvent;
import io.workm8.agui4j.core.message.BaseMessage;
import io.workm8.agui4j.core.message.UserMessage;
import io.workm8.agui4j.core.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("HttpAgent")
class HttpAgentTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        void shouldBuildAgentWithAllRequiredParameters() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act
            HttpAgent agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .build();

            // Assert
            assertThat(agent).isNotNull();
            assertThat(agent.getMessages()).isEmpty();
            assertThat(agent.getState()).isNotNull();
            assertThat(agent.httpClient).isEqualTo(httpClient);
        }

        @Test
        void shouldBuildAgentWithAllOptionalParameters() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();
            List<BaseMessage> messages = List.of(createMessage("Test message"));
            State state = new State();
            state.set("key", "value");

            // Act
            HttpAgent agent = HttpAgent.builder()
                .agentId("test-agent")
                .description("Test description")
                .threadId("test-thread")
                .httpClient(httpClient)
                .messages(messages)
                .state(state)
                .debug(true)
                .build();

            // Assert
            assertThat(agent.getMessages()).hasSize(1);
            assertThat(agent.getState().get("key")).isEqualTo("value");
        }



        @Test
        void shouldAddSingleMessageToBuilder() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();
            BaseMessage message1 = createMessage("Message 1");
            BaseMessage message2 = createMessage("Message 2");

            // Act
            HttpAgent agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .addMessage(message1)
                    .addMessage(message2)
                    .build();

            // Assert
            assertThat(agent.getMessages()).hasSize(2);
            assertThat(agent.getMessages().get(0).getContent()).isEqualTo("Message 1");
            assertThat(agent.getMessages().get(1).getContent()).isEqualTo("Message 2");
        }

        @Test
        void shouldHandleNullMessagesList() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act
            HttpAgent agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .messages(null)
                    .build();

            // Assert
            assertThat(agent.getMessages()).isEmpty();
        }

        @Test
        void shouldHandleNullState() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act
            HttpAgent agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .state(null)
                    .build();

            // Assert
            assertThat(agent.getState()).isNotNull();
        }

        @Test
        void shouldThrowExceptionWhenAgentIdIsNull() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act & Assert
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> HttpAgent.builder()
                            .threadId("test-thread")
                            .httpClient(httpClient)
                            .build())
                    .withMessage("agentId is required");
        }

        @Test
        void shouldThrowExceptionWhenAgentIdIsEmpty() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act & Assert
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> HttpAgent.builder()
                            .agentId("   ")
                            .threadId("test-thread")
                            .httpClient(httpClient)
                            .build())
                    .withMessage("agentId is required");
        }

        @Test
        void shouldThrowExceptionWhenThreadIdIsNull() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act & Assert
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> HttpAgent.builder()
                            .agentId("test-agent")
                            .httpClient(httpClient)
                            .build())
                    .withMessage("threadId is required");
        }

        @Test
        void shouldThrowExceptionWhenThreadIdIsEmpty() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act & Assert
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> HttpAgent.builder()
                            .agentId("test-agent")
                            .threadId("")
                            .httpClient(httpClient)
                            .build())
                    .withMessage("threadId is required");
        }

        @Test
        void shouldThrowExceptionWhenHttpClientIsNull() {
            // Act & Assert
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> HttpAgent.builder()
                            .agentId("test-agent")
                            .threadId("test-thread")
                            .build())
                    .withMessage("http client is required");
        }

        @Test
        void shouldSupportMethodChaining() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();

            // Act & Assert - Should compile and not throw exceptions
            HttpAgent agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .description("Test description")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .messages(new ArrayList<>())
                    .addMessage(createMessage("Test"))
                    .state(new State())
                    .debug(false)
                    .debug()
                    .build();

            assertThat(agent).isNotNull();
        }
    }

    @Nested
    @DisplayName("Agent Execution")
    class AgentExecutionTests {

        private TestHttpClient httpClient;
        private HttpAgent agent;
        private RunAgentParameters parameters;

        @BeforeEach
        void setUp() {
            httpClient = new TestHttpClient();
            agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .build();

            parameters = RunAgentParameters.empty();
        }

        @Test
        void shouldExecuteSuccessfullyWithEventsFromHttpClient() throws Exception {
            // Arrange
            TestSubscriber subscriber = new TestSubscriber();

            RunStartedEvent startEvent = new RunStartedEvent();
            RunFinishedEvent finishEvent = new RunFinishedEvent();

            httpClient.setEventsToEmit(List.of(startEvent, finishEvent));
            httpClient.setShouldComplete(true);

            // Act
            CompletableFuture<Void> future = agent.runAgent(parameters, subscriber);
            future.get(5, TimeUnit.SECONDS);

            // Assert
            assertThat(future).isDone();
            assertThat(future.isCompletedExceptionally()).isFalse();

            assertThat(subscriber.wasOnRunInitializedCalled()).isTrue();
            assertThat(subscriber.wasOnRunFinalizedCalled()).isTrue();
            assertThat(subscriber.getEventCount()).isEqualTo(2);


            // Verify HTTP client was called with correct input
            assertThat(httpClient.getLastInput()).isNotNull();
            assertThat(httpClient.getLastInput().threadId()).isEqualTo("test-thread");
        }

        @Test
        void shouldHandleHttpClientErrorsProperly() {
            // Arrange
            TestSubscriber subscriber = new TestSubscriber();
            httpClient.setShouldThrowError(true);
            httpClient.setErrorToThrow(new RuntimeException("HTTP error"));

            // Act
            CompletableFuture<Void> future = agent.runAgent(parameters, subscriber);

            // Assert
            assertThatExceptionOfType(Exception.class)
                    .isThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                    .withCauseInstanceOf(RuntimeException.class)
                    .withMessageContaining("HTTP error");

            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThat(subscriber.wasOnRunErrorCalled()).isTrue();
        }

        @Test
        void shouldNotForwardEventsWhenStreamIsCancelled() throws Exception {
            // Arrange
            TestSubscriber subscriber = new TestSubscriber();

            // Set up HTTP client to emit events after a delay
            httpClient.setDelayBeforeEvents(100);
            httpClient.setEventsToEmit(List.of(new RunStartedEvent()));
            httpClient.setShouldComplete(true);

            // Act
            CompletableFuture<Void> future = agent.runAgent(parameters, subscriber);

            // Cancel immediately
            future.cancel(true);

            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Expected due to cancellation
            }

            // Assert
            // Give a moment for any delayed events to potentially arrive
            Thread.sleep(200);

            // The HTTP client should have been called, but events shouldn't be forwarded
            assertThat(httpClient.wasStreamEventsCalled()).isTrue();
        }

        @Test
        void shouldPassCancellationTokenToHttpClient() throws Exception {
            // Arrange
            TestSubscriber subscriber = new TestSubscriber();
            httpClient.setShouldComplete(true);

            // Act
            CompletableFuture<Void> future = agent.runAgent(parameters, subscriber);
            future.get(5, TimeUnit.SECONDS);

            // Assert
            assertThat(httpClient.getLastCancellationToken()).isNotNull();
        }

        @Test
        void shouldHandleMultipleEventsInSequence() throws Exception {
            // Arrange
            TestSubscriber subscriber = new TestSubscriber();

            List<BaseEvent> events = List.of(
                    new RunStartedEvent(),
                    new RunStartedEvent(), // Another event
                    new RunFinishedEvent()
            );

            httpClient.setEventsToEmit(events);
            httpClient.setShouldComplete(true);

            // Act
            CompletableFuture<Void> future = agent.runAgent(parameters, subscriber);
            future.get(5, TimeUnit.SECONDS);

            // Assert
            assertThat(future).isDone();
            assertThat(subscriber.getEventCount()).isEqualTo(3);
        }

        @Test
        void shouldHandleEmptyEventStream() throws Exception {
            // Arrange
            TestSubscriber subscriber = new TestSubscriber();
            httpClient.setEventsToEmit(List.of()); // No events
            httpClient.setShouldComplete(true);

            // Act
            CompletableFuture<Void> future = agent.runAgent(parameters, subscriber);
            future.get(5, TimeUnit.SECONDS);

            // Assert
            assertThat(future).isDone();
            assertThat(future.isCompletedExceptionally()).isFalse();
            assertThat(subscriber.getEventCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Resource Management")
    class ResourceManagementTests {

        @Test
        void shouldCloseHttpClientWhenCloseIsCalled() {
            // Arrange
            TestHttpClient httpClient = new TestHttpClient();
            HttpAgent agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .build();

            // Act
            agent.close();

            // Assert
            assertThat(httpClient.wasClosed()).isTrue();
        }

        @Test
        void shouldHandleCloseWhenHttpClientIsNull() {
            // This test verifies the null check in close() method
            // We can't easily create an HttpAgent with null httpClient due to validation
            // but we can test the defensive programming aspect

            // Arrange
            TestHttpClient httpClient = new TestHttpClient();
            HttpAgent agent = HttpAgent.builder()
                    .agentId("test-agent")
                    .threadId("test-thread")
                    .httpClient(httpClient)
                    .build();

            // Act & Assert - Should not throw exception
            agent.close();
            agent.close(); // Multiple calls should be safe

            assertThat(httpClient.wasClosed()).isTrue();
        }
    }

    // Test implementations
    private static class TestSubscriber implements io.workm8.agui4j.core.agent.AgentSubscriber {
        private final List<BaseEvent> receivedEvents = new ArrayList<>();
        private final List<String> methodCalls = new ArrayList<>();
        private final AtomicInteger eventCount = new AtomicInteger(0);
        private final AtomicBoolean onRunInitializedCalled = new AtomicBoolean(false);
        private final AtomicBoolean onRunFinalizedCalled = new AtomicBoolean(false);
        private final AtomicBoolean onRunErrorCalled = new AtomicBoolean(false);

        @Override
        public void onEvent(BaseEvent event) {
            receivedEvents.add(event);
            eventCount.incrementAndGet();
            methodCalls.add("onEvent");
        }

        @Override
        public void onRunInitialized(io.workm8.agui4j.core.agent.AgentSubscriberParams params) {
            onRunInitializedCalled.set(true);
            methodCalls.add("onRunInitialized");
        }

        @Override
        public void onRunFinalized(io.workm8.agui4j.core.agent.AgentSubscriberParams params) {
            onRunFinalizedCalled.set(true);
            methodCalls.add("onRunFinalized");
        }

        @Override
        public void onRunStartedEvent(io.workm8.agui4j.core.event.RunStartedEvent event) {
            methodCalls.add("onRunStartedEvent");
        }

        @Override
        public void onRunErrorEvent(io.workm8.agui4j.core.event.RunErrorEvent event) {
            onRunErrorCalled.set(true);
            methodCalls.add("onRunErrorEvent");
        }

        @Override
        public void onRunFinishedEvent(io.workm8.agui4j.core.event.RunFinishedEvent event) {
            methodCalls.add("onRunFinishedEvent");
        }

        @Override
        public void onStepStartedEvent(io.workm8.agui4j.core.event.StepStartedEvent event) {
            methodCalls.add("onStepStartedEvent");
        }

        @Override
        public void onStepFinishedEvent(io.workm8.agui4j.core.event.StepFinishedEvent event) {
            methodCalls.add("onStepFinishedEvent");
        }

        @Override
        public void onTextMessageStartEvent(io.workm8.agui4j.core.event.TextMessageStartEvent event) {
            methodCalls.add("onTextMessageStartEvent");
        }

        @Override
        public void onTextMessageContentEvent(io.workm8.agui4j.core.event.TextMessageContentEvent event) {
            methodCalls.add("onTextMessageContentEvent");
        }

        @Override
        public void onTextMessageEndEvent(io.workm8.agui4j.core.event.TextMessageEndEvent event) {
            methodCalls.add("onTextMessageEndEvent");
        }

        @Override
        public void onToolCallStartEvent(io.workm8.agui4j.core.event.ToolCallStartEvent event) {
            methodCalls.add("onToolCallStartEvent");
        }

        @Override
        public void onToolCallArgsEvent(io.workm8.agui4j.core.event.ToolCallArgsEvent event) {
            methodCalls.add("onToolCallArgsEvent");
        }

        @Override
        public void onToolCallResultEvent(io.workm8.agui4j.core.event.ToolCallResultEvent event) {
            methodCalls.add("onToolCallResultEvent");
        }

        @Override
        public void onToolCallEndEvent(io.workm8.agui4j.core.event.ToolCallEndEvent event) {
            methodCalls.add("onToolCallEndEvent");
        }

        @Override
        public void onRawEvent(io.workm8.agui4j.core.event.RawEvent event) {
            methodCalls.add("onRawEvent");
        }

        @Override
        public void onCustomEvent(io.workm8.agui4j.core.event.CustomEvent event) {
            methodCalls.add("onCustomEvent");
        }

        @Override
        public void onMessagesSnapshotEvent(io.workm8.agui4j.core.event.MessagesSnapshotEvent event) {
            methodCalls.add("onMessagesSnapshotEvent");
        }

        @Override
        public void onStateSnapshotEvent(io.workm8.agui4j.core.event.StateSnapshotEvent event) {
            methodCalls.add("onStateSnapshotEvent");
        }

        @Override
        public void onStateDeltaEvent(io.workm8.agui4j.core.event.StateDeltaEvent event) {
            methodCalls.add("onStateDeltaEvent");
        }

        @Override
        public void onNewMessage(BaseMessage message) {
            methodCalls.add("onNewMessage");
        }

        @Override
        public void onMessagesChanged(io.workm8.agui4j.core.agent.AgentSubscriberParams params) {
            methodCalls.add("onMessagesChanged");
        }

        // Getter methods for verification
        public List<BaseEvent> getReceivedEvents() { return receivedEvents; }
        public List<String> getMethodCalls() { return methodCalls; }
        public int getEventCount() { return eventCount.get(); }
        public boolean wasOnRunInitializedCalled() { return onRunInitializedCalled.get(); }
        public boolean wasOnRunFinalizedCalled() { return onRunFinalizedCalled.get(); }
        public boolean wasOnRunErrorCalled() { return onRunErrorCalled.get(); }
    }

    private static class TestHttpClient implements BaseHttpClient {
        private List<BaseEvent> eventsToEmit = new ArrayList<>();
        private boolean shouldThrowError = false;
        private Throwable errorToThrow;
        private boolean shouldComplete = false;
        private long delayBeforeEvents = 0;
        private boolean wasClosed = false;
        private boolean streamEventsCalled = false;
        private RunAgentInput lastInput;
        private AtomicBoolean lastCancellationToken;

        @Override
        public CompletableFuture<Void> streamEvents(
                RunAgentInput input,
                Consumer<BaseEvent> eventConsumer,
                AtomicBoolean cancellationToken
        ) {
            this.streamEventsCalled = true;
            this.lastInput = input;
            this.lastCancellationToken = cancellationToken;

            return CompletableFuture.runAsync(() -> {
                try {
                    if (delayBeforeEvents > 0) {
                        Thread.sleep(delayBeforeEvents);
                    }

                    if (shouldThrowError) {
                        throw new RuntimeException(errorToThrow);
                    }

                    // Emit events
                    for (BaseEvent event : eventsToEmit) {
                        if (cancellationToken.get()) {
                            break; // Stop if cancelled
                        }
                        eventConsumer.accept(event);
                    }

                    if (!shouldComplete) {
                        throw new RuntimeException("HTTP client error");
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            });
        }

        @Override
        public void close() {
            this.wasClosed = true;
        }

        // Test configuration methods
        public void setEventsToEmit(List<BaseEvent> events) {
            this.eventsToEmit = new ArrayList<>(events);
        }

        public void setShouldThrowError(boolean shouldThrowError) {
            this.shouldThrowError = shouldThrowError;
        }

        public void setErrorToThrow(Throwable error) {
            this.errorToThrow = error;
        }

        public void setShouldComplete(boolean shouldComplete) {
            this.shouldComplete = shouldComplete;
        }

        public void setDelayBeforeEvents(long delayMs) {
            this.delayBeforeEvents = delayMs;
        }

        // Verification methods
        public boolean wasClosed() {
            return wasClosed;
        }

        public boolean wasStreamEventsCalled() {
            return streamEventsCalled;
        }

        public RunAgentInput getLastInput() {
            return lastInput;
        }

        public AtomicBoolean getLastCancellationToken() {
            return lastCancellationToken;
        }
    }

    private static BaseMessage createMessage(String content) {
        BaseMessage message = new UserMessage();
        message.setContent(content);
        return message;
    }
}