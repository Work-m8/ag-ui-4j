package io.workm8.agui4j.core.context;

import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 * @author pascalwilbrink
 *
 * Represents a context entry containing a description and associated value.
 *
 * @param description a human-readable description of what this context represents.
 *                   Cannot be null.
 * @param value      the actual value or data associated with this context.
 *                   Cannot be null.
 * @throws NullPointerException if description or value is null
 */
public record Context(@NotNull String description, @NotNull String value) {
    public Context {
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
    }
}