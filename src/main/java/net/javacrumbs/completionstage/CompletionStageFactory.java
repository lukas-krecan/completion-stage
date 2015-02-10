/**
 * Copyright 2009-2014 the original author or authors.
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

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Factory for {@link java.util.concurrent.CompletionStage} implementation.
 */
public class CompletionStageFactory {
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
        return new SimpleCompletionStage<>(defaultAsyncExecutor);
    }
    
    public final <T> CompletionStage<T> completedFuture(T value) {
        CompletableCompletionStage<T> result = createCompletionStage();
        result.complete(value);
        return result;
    }

    public final <U> CompletionStage<U> supplyAsync(Supplier<U> supplier) {
        Objects.requireNonNull(supplier, "supplier");

        return completedFuture(null).thenApplyAsync((ignored) -> supplier.get());
    }

    public final <U> CompletionStage<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(executor, "executor");

        return completedFuture(null).thenApplyAsync((ignored) -> supplier.get(), executor);
    }

    public final CompletionStage<Void> runAsync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");

        return completedFuture(null).thenRunAsync(runnable);
    }

    public final CompletionStage<Void> runAsync(Runnable runnable, Executor executor) {
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(executor, "executor");

        return completedFuture(null).thenRunAsync(runnable, executor);
    }
}
