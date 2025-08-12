package io.workm8.agui4j.core.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolMessage")
class ToolMessageTest {

    @Test
    void shouldSetRole() {
        var message = new ToolMessage();

        assertThat(message.getRole()).isEqualTo("tool");
    }
}