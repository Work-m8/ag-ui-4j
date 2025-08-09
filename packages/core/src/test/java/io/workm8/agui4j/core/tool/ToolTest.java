package io.workm8.agui4j.core.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("Tool")
class ToolTest {

    @Test
    void shouldThrowNullPointerExceptionOnNullName() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new Tool(null, "description", "params"))
            .withMessage("name cannot be null");
    }
}