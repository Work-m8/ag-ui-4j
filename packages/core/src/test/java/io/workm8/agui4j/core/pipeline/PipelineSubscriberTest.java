package io.workm8.agui4j.core.pipeline;

import io.workm8.agui4j.core.exception.AGUIException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("PipelineSubscriber")
class PipelineSubscriberTest {

    @Test
    void shouldThrowRuntimeExceptionWhenOnErrorNotImplemented() {
        var subscriber = new PipelineSubscriber<String>() {
            @Override
            public void onNext(String item) {

            }
        };

        assertThatExceptionOfType(RuntimeException.class)
            .isThrownBy(() -> subscriber.onError(new AGUIException("EXCEPTION")))
            .withMessage("Unhandled pipeline error");
    }
}