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

import net.javacrumbs.completionstage.spi.CompletableCompletionStageFactory;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Factory for {@link java.util.concurrent.CompletionStage} implementation.
 */
public class CompletionStageFactory implements CompletableCompletionStageFactory {
    private final Executor defaultAsyncExecutor;

    /**
     * Creates factory.
     * @param defaultAsyncExecutor executor to be used for async methods without executor parameter
     */
    public CompletionStageFactory(Executor defaultAsyncExecutor) {
        this.defaultAsyncExecutor = defaultAsyncExecutor;
    }

    /**
     * Creates completion stage.
     * @param <T> type of the CompletionStage
     * @return CompletionStage
     */
    public <T> CompletableCompletionStage<T> createCompletionStage() {
        return new SimpleCompletionStage<>(defaultAsyncExecutor, this);
    }

    /**
     * Returns a new CompletionStage that is already completed with
     * the given value.
     *
     * @param value the value
     * @param <T> the type of the value
     * @return the completed CompletionStage
     */
    public final <T> CompletionStage<T> completedStage(T value) {
        CompletableCompletionStage<T> result = createCompletionStage();
        result.complete(value);
        return result;
    }

    /**
     * Returns a new CompletionStage that is asynchronously completed
     * by a task running in the defaultAsyncExecutor with
     * the value obtained by calling the given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletionStage
     * @param <U> the function's return type
     * @return the new CompletionStage
     */
    public final <U> CompletionStage<U> supplyAsync(Supplier<U> supplier) {
        return supplyAsync(supplier, defaultAsyncExecutor);
    }

    /**
     * Returns a new CompletionStage that is asynchronously completed
     * by a task running in the given executor with the value obtained
     * by calling the given Supplier. Subsequent completion stages will
     * use defaultAsyncExecutor as their default executor.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletionStage
     * @param executor the executor to use for asynchronous execution
     * @param <U> the function's return type
     * @return the new CompletionStage
     */
    public final <U> CompletionStage<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return completedStage(null).thenApplyAsync((ignored) -> supplier.get(), executor);
    }

    /**
     * Returns a new CompletionStage that is asynchronously completed
     * by a task running in the defaultAsyncExecutor after
     * it runs the given action.
     *
     * @param runnable the action to run before completing the
     * returned CompletionStage
     * @return the new CompletionStage
     */
    public final CompletionStage<Void> runAsync(Runnable runnable) {
        return runAsync(runnable, defaultAsyncExecutor);
    }

    /**
     * Returns a new CompletionStage that is asynchronously completed
     * by a task running in the given executor after it runs the given
     * action. Subsequent completion stages will
     * use defaultAsyncExecutor as their default executor.
     *
     * @param runnable the action to run before completing the
     * returned CompletionStage
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletionStage
     */
    public final CompletionStage<Void> runAsync(Runnable runnable, Executor executor) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return completedStage(null).thenRunAsync(runnable, executor);
    }

    protected final Executor getDefaultAsyncExecutor() {
        return defaultAsyncExecutor;
    }
}
