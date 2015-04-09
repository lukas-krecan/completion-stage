/**
 * Copyright 2009-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.completionstage;

import java.util.concurrent.CompletionStage;

/**
 * Completion stage you can complete. On  top of standard {@link java.util.concurrent.CompletionStage} methods
 * provides {@link net.javacrumbs.completionstage.CompletableCompletionStage#complete(Object)} and
 * {@link net.javacrumbs.completionstage.CompletableCompletionStage#completeExceptionally(Throwable)}.
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

    /**
     * Sets this CompletionStage as success with provided value
     * if it hasn't been already completed. Same as {@link #complete(Object) complete(T)}, the only difference
     * is the return type which makes this method more suitable to be used as method reference.
     *
     * @param result the success value. May be null.
     */
    public default void doComplete(T result) {
        complete(result);
    }

    /**
     * Accepts a value and a throwable to complete this CompletionStage
     * if it hasn't been already completed. If throwable is null, completes normally, if
     * throwable is not null, completes exceptionally.
     *
     * @param result    the success value. May be null.
     *                  Completes this computation as a success with this value only if throwable is null.
     * @param throwable the failure value.
     *                  If not null, completes this computation as a failure with this value.
     */
    public default void doComplete(T result, Throwable throwable) {
        if (throwable == null) {
            complete(result);
        } else {
            completeExceptionally(throwable);
        }
    }
}
