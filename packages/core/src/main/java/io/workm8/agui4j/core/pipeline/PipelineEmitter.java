package io.workm8.agui4j.core.pipeline;

/**
 * A functional interface for components that emit data to pipeline subscribers.
 * <p>
 * This interface defines a contract for objects that can emit data of type T
 * to registered subscribers in a pipeline architecture. It follows the
 * publisher-subscriber pattern, allowing emitters to notify multiple
 * subscribers when data becomes available.
 * </p>
 * <p>
 * As a functional interface, this can be implemented using lambda expressions
 * or method references, making it convenient for creating lightweight
 * emitters in pipeline processing scenarios.
 * </p>
 *
 * @param <T> the type of data that this emitter produces
 * @author pascalwilbrink
 * @see PipelineSubscriber
 * @see FunctionalInterface
 */
@FunctionalInterface
public interface PipelineEmitter<T> {

    /**
     * Subscribes a subscriber to receive data from this emitter.
     * <p>
     * When this method is called, the emitter should register the subscriber
     * to receive future data emissions. The specific behavior of when and how
     * data is emitted depends on the implementation.
     * </p>
     *
     * @param subscriber the subscriber that will receive emitted data.
     *                   Must not be null.
     */
    void subscribe(PipelineSubscriber<T> subscriber);
}