package io.workm8.agui4j.core.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("ToolCall")
class ToolCallTest {

    @Test
    void shouldThrowNullPointerExceptionOnNullId() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new ToolCall(null, "type", null))
            .withMessage("id cannot be null");
    }

    @Test
    void shouldThrowNullPointerExceptionOnNullType() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new ToolCall("id", null, null))
            .withMessage("type cannot be null");
    }
}