package io.workm8.agui4j.core.pipeline;

/**
 * A functional interface for components that receive data from pipeline emitters.
 * <p>
 * This interface defines a contract for objects that can consume data of type T
 * in a pipeline architecture. It follows the observer pattern, providing
 * methods to handle data items, errors, and completion signals from emitters.
 * </p>
 * <p>
 * As a functional interface, this can be implemented using lambda expressions
 * or method references for the primary {@link #onNext(Object)} method, while
 * providing sensible defaults for error handling and completion.
 * </p>
 *
 * @param <T> the type of data that this subscriber consumes
 * @author pascalwilbrink
 * @see PipelineEmitter
 * @see FunctionalInterface
 */
@FunctionalInterface
public interface PipelineSubscriber<T> {

    /**
     * Called when a new data item is available from the emitter.
     * <p>
     * This is the primary method that subscribers must implement to process
     * incoming data items from the pipeline.
     * </p>
     *
     * @param item the data item to process. May be null depending on emitter implementation.
     */
    void onNext(T item);

    /**
     * Called when an error occurs in the pipeline.
     * <p>
     * The default implementation wraps the error in a {@link RuntimeException}
     * and throws it. Implementations can override this method to provide
     * custom error handling behavior.
     * </p>
     *
     * @param error the error that occurred. Should not be null.
     * @throws RuntimeException by default, wrapping the original error
     */
    default void onError(Throwable error) {
        throw new RuntimeException("Unhandled pipeline error", error);
    }

    /**
     * Called when the emitter has finished sending data and will send no more items.
     * <p>
     * The default implementation does nothing. Implementations can override
     * this method to perform cleanup or finalization tasks when the data
     * stream completes.
     * </p>
     */
    default void onComplete() {
    }
}