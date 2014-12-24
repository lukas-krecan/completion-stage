package net.javacrumbs.completionstage;

import java.util.concurrent.CompletionStage;

/**
 * Completion stage you can complete. On  top of standard {@link java.util.concurrent.CompletionStage} methods
 * provides {@link #complete(T)} and {@link #completeExceptionally(Throwable)}.
 *
 * @param <T>
 */
public interface CompletableCompletionStage<T> extends CompletionStage<T> {

    /**
     * Call this if you want to start processing of the result..
     *
     * @param result the result value.
     * @return {@code true} if this invocation caused this CompletionStage
     * to transition to a completed state, else {@code false}
     */
    public boolean complete(T result);

    /**
     * Call this if you want to start processing of failure.
     *
     * @param ex the exception
     * @return {@code true} if this invocation caused this CompletionStage
     * to transition to a completed state, else {@code false}
     */
    public boolean completeExceptionally(Throwable ex);
}
