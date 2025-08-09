package io.workm8.agui4j.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Context")
public class ContextTest {

    @Test()
    public void itShouldThrowNullPointerExceptionOnNullDescription() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new Context(null, "value"))
            .withMessage("description cannot be null");
    }

    @Test()
    public void itShouldThrowNullPointerExceptionOnNullValue() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new Context("description", null))
            .withMessage("value cannot be null");
    }

    @Test
    public void itShouldCreateContext() {
        var description = "description";
        var value = "value";

        var context = new Context(description, value);

        assertThat(context.description()).isEqualTo(description);
        assertThat(context.value()).isEqualTo(value);
    }

}