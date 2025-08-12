package io.workm8.agui4j.spring.ai;

import org.springframework.ai.chat.messages.*;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * Utility class for converting agui4j message formats to Spring AI message formats.
 * <p>
 * MessageMapper provides comprehensive conversion between the agui4j message hierarchy
 * and Spring AI's message system, enabling seamless integration between the two frameworks.
 * The mapper handles different message types including user messages, assistant messages,
 * system messages, developer messages, and tool messages while preserving all relevant
 * metadata and content.
 * <p>
 * Supported message type mappings:
 * <ul>
 * <li>UserMessage → Spring AI UserMessage with metadata</li>
 * <li>AssistantMessage → Spring AI AssistantMessage with tool calls</li>
 * <li>SystemMessage → Spring AI SystemMessage with metadata</li>
 * <li>DeveloperMessage → Spring AI UserMessage with metadata</li>
 * <li>ToolMessage → Spring AI ToolResponseMessage with tool responses</li>
 * </ul>
 * <p>
 * The mapper preserves important message attributes and automatically handles:
 * <ul>
 * <li>Message IDs (generating UUIDs when missing)</li>
 * <li>Message names and roles</li>
 * <li>Tool call information and execution details</li>
 * <li>Error handling for tool responses</li>
 * <li>Metadata preservation in Spring AI format</li>
 * </ul>
 * <p>
 * This class is stateless and thread-safe, making it suitable for use in
 * concurrent environments and as a singleton or utility class.
 *
 * @author Pascal Wilbrink
 */
public class MessageMapper {

    /**
     * Converts an agui4j AssistantMessage to a Spring AI AssistantMessage.
     * <p>
     * This method creates a Spring AI AssistantMessage with support for tool calls,
     * preserving the assistant's response content and any function calls that were
     * made during the interaction. Tool calls are converted to Spring AI's ToolCall
     * format with proper ID, type, function name, and arguments mapping.
     * <p>
     * The conversion process:
     * <ul>
     * <li>Maps message content directly</li>
     * <li>Preserves message ID (generates UUID if missing)</li>
     * <li>Converts agui4j tool calls to Spring AI ToolCall objects</li>
     * <li>Ensures tool call IDs are present (generates if missing)</li>
     * <li>Includes metadata with ID and name information</li>
     * </ul>
     *
     * @param message the agui4j AssistantMessage to convert
     * @return a Spring AI AssistantMessage with content, metadata, and tool calls
     */
    public org.springframework.ai.chat.messages.AssistantMessage toSpringMessage(final io.workm8.agui4j.core.message.AssistantMessage message) {
        return new org.springframework.ai.chat.messages.AssistantMessage(
                message.getContent(),
                Map.of(
                        "id",
                        Objects.nonNull(message.getId()) ? message.getId() : UUID.randomUUID().toString(),
                        "name",
                        Objects.nonNull(message.getName()) ? message.getName() : ""
                ),
                Objects.isNull(message.getToolCalls())
                        ? emptyList()
                        : message.getToolCalls().stream().map(toolCall ->
                        new AssistantMessage.ToolCall(
                                Objects.nonNull(toolCall.id())
                                        ? toolCall.id()
                                        : UUID.randomUUID().toString(),
                                toolCall.type(),
                                toolCall.function().name(),
                                toolCall.function().arguments()
                        )
                ).collect(Collectors.toList())
        );
    }

    /**
     * Converts an agui4j SystemMessage to a Spring AI SystemMessage.
     * <p>
     * This method creates a Spring AI SystemMessage that contains system-level
     * instructions or context. System messages are typically used to set the
     * behavior, role, or context for the AI assistant at the beginning of
     * a conversation.
     * <p>
     * The conversion preserves the message content and includes metadata
     * with the message ID and name, ensuring proper tracking and identification
     * within the Spring AI framework.
     *
     * @param message the agui4j SystemMessage containing system instructions
     * @return a Spring AI SystemMessage with text content and metadata
     */
    public org.springframework.ai.chat.messages.SystemMessage toSpringMessage(final io.workm8.agui4j.core.message.SystemMessage message) {
        return SystemMessage.builder()
                .text(message.getContent())
                .metadata(
                        Map.of(
                                "id",
                                Objects.nonNull(message.getId()) ? message.getId() : UUID.randomUUID().toString(),
                                "name",
                                Objects.nonNull(message.getName()) ? message.getName() : ""
                        )
                ).build();
    }

    /**
     * Converts an agui4j DeveloperMessage to a Spring AI UserMessage.
     * <p>
     * This method maps developer messages (typically containing development-related
     * instructions or context) to Spring AI's UserMessage format. Developer messages
     * are treated as user input in the Spring AI context, allowing for seamless
     * integration of developer-specific communication within the conversation flow.
     * <p>
     * The conversion preserves the message content and metadata, ensuring that
     * developer context is properly maintained and trackable within Spring AI.
     *
     * @param message the agui4j DeveloperMessage containing developer instructions or context
     * @return a Spring AI UserMessage with developer content and metadata
     */
    public org.springframework.ai.chat.messages.UserMessage toSpringMessage(final io.workm8.agui4j.core.message.DeveloperMessage message) {
        return UserMessage.builder()
                .text(message.getContent())
                .metadata(
                        Map.of(
                                "id",
                                Objects.nonNull(message.getId()) ? message.getId() : UUID.randomUUID().toString(),
                                "name",
                                Objects.nonNull(message.getName()) ? message.getName() : ""
                        )
                ).build();
    }

    /**
     * Converts an agui4j ToolMessage to a Spring AI ToolResponseMessage.
     * <p>
     * This method creates a Spring AI ToolResponseMessage that represents the
     * result of a tool execution. It handles both successful tool execution
     * results and error conditions, prioritizing error information when present.
     * <p>
     * The conversion process:
     * <ul>
     * <li>Creates a ToolResponse with the tool call ID and name</li>
     * <li>Uses error content if present, otherwise uses regular content</li>
     * <li>Wraps the response in a list as required by Spring AI</li>
     * <li>Includes metadata with message ID and name</li>
     * </ul>
     * <p>
     * This approach ensures that both successful tool executions and error
     * conditions are properly communicated to the Spring AI framework.
     *
     * @param message the agui4j ToolMessage containing tool execution result or error
     * @return a Spring AI ToolResponseMessage with tool response and metadata
     */
    public org.springframework.ai.chat.messages.ToolResponseMessage toSpringMessage(final io.workm8.agui4j.core.message.ToolMessage message) {
        return new ToolResponseMessage(
                asList(
                        new ToolResponseMessage.ToolResponse(
                                message.getToolCallId(),
                                message.getName(),
                                Objects.nonNull(message.getError())
                                        ? message.getError()
                                        : message.getContent()
                        )
                ),
                Map.of(
                        "id",
                        Objects.nonNull(message.getId()) ? message.getId() : UUID.randomUUID().toString(),
                        "name",
                        Objects.nonNull(message.getName()) ? message.getName() : ""
                )
        );
    }

    /**
     * Converts an agui4j UserMessage to a Spring AI UserMessage.
     * <p>
     * This method creates a Spring AI UserMessage that represents input from
     * a user in the conversation. It preserves the user's message content
     * and maintains user identity information through metadata.
     * <p>
     * The conversion handles both named and anonymous user messages, ensuring
     * that user context and identity are properly maintained within the
     * Spring AI conversation flow.
     *
     * @param message the agui4j UserMessage to convert
     * @return a Spring AI UserMessage with user content and metadata
     */
    public org.springframework.ai.chat.messages.UserMessage toSpringMessage(final io.workm8.agui4j.core.message.UserMessage message) {
        return UserMessage.builder()
                .text(message.getContent())
                .metadata(
                        Map.of(
                                "id",
                                Objects.nonNull(message.getId()) ? message.getId() : UUID.randomUUID().toString(),
                                "name",
                                Objects.nonNull(message.getName()) ? message.getName() : ""
                        )
                ).build();
    }

    /**
     * Converts any agui4j BaseMessage to the appropriate Spring AI AbstractMessage.
     * <p>
     * This method acts as a dispatcher, examining the message role to determine
     * the appropriate conversion method. It handles all supported message types
     * and delegates to specific conversion methods based on the message role.
     * <p>
     * Supported roles and their mappings:
     * <ul>
     * <li>"assistant" → AssistantMessage via {@link #toSpringMessage(io.workm8.agui4j.core.message.AssistantMessage)}</li>
     * <li>"system" → SystemMessage via {@link #toSpringMessage(io.workm8.agui4j.core.message.SystemMessage)}</li>
     * <li>"developer" → UserMessage via {@link #toSpringMessage(io.workm8.agui4j.core.message.DeveloperMessage)}</li>
     * <li>"tool" → ToolResponseMessage via {@link #toSpringMessage(io.workm8.agui4j.core.message.ToolMessage)}</li>
     * <li>Default/Other → UserMessage via {@link #toSpringMessage(io.workm8.agui4j.core.message.UserMessage)}</li>
     * </ul>
     * <p>
     * This method provides a convenient single entry point for message conversion
     * when the specific message type is not known at compile time.
     *
     * @param baseMessage the agui4j BaseMessage to convert
     * @return the appropriate Spring AI AbstractMessage subclass
     * @throws ClassCastException if the message cannot be cast to the expected type
     *                           based on its role
     */
    public org.springframework.ai.chat.messages.AbstractMessage toSpringMessage(final io.workm8.agui4j.core.message.BaseMessage baseMessage) {
        switch (baseMessage.getRole()) {
            case "assistant" -> {
                return this.toSpringMessage((io.workm8.agui4j.core.message.AssistantMessage) baseMessage);
            }
            case "system" -> {
                return this.toSpringMessage((io.workm8.agui4j.core.message.SystemMessage) baseMessage);
            }
            case "developer" -> {
                return this.toSpringMessage((io.workm8.agui4j.core.message.DeveloperMessage) baseMessage);
            }
            case "tool" -> {
                return this.toSpringMessage((io.workm8.agui4j.core.message.ToolMessage) baseMessage);
            }
            default -> {
                return this.toSpringMessage((io.workm8.agui4j.core.message.UserMessage) baseMessage);
            }
        }
    }
}