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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link java.util.concurrent.CompletionStage} implementation.
 * Keeps the result or callbacks in {@link net.javacrumbs.completionstage.CallbackRegistry}
 */
class SimpleCompletionStage<T> implements CompletableCompletionStage<T> {
    static final Executor SAME_THREAD_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
        @Override
        public String toString() {
            return "SAME_THREAD_EXECUTOR";
        }
    };

    private final CallbackRegistry<T> callbackRegistry = new CallbackRegistry<>();
    /**
     * Default executor to be used for Async methods.
     */
    private final Executor defaultExecutor;

    /**
     * Creates SimpleCompletionStage.
     *
     * @param defaultExecutor executor to be used for all async method without executor parameter.
     */
    public SimpleCompletionStage(Executor defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }

    /**
     * Notifies all callbacks about the result.
     *
     * @param result result of the previous stage.
     */
    @Override
    public boolean complete(T result) {
        return callbackRegistry.success(result);
    }

    /**
     * Notifies all callbacks about the failure.
     *
     * @param ex exception thrown from the previous stage.
     */
    @Override
    public boolean completeExceptionally(Throwable ex) {
        return callbackRegistry.failure(ex);
    }

    @Override
    public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, SAME_THREAD_EXECUTOR);
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultExecutor);
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(
            Function<? super T, ? extends U> fn,
            Executor executor
    ) {
        SimpleCompletionStage<U> nextStage = newSimpleCompletionStage();
        addCallbacks(
                result -> nextStage.acceptResult(() -> fn.apply(result)),
                nextStage::handleFailure,
                executor
        );
        return nextStage;
    }

    @Override
    public CompletionStage<Void> thenAccept(Consumer<? super T> action) {
        return thenAcceptAsync(action, SAME_THREAD_EXECUTOR);
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor);
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenApplyAsync(convertConsumerToFunction(action), executor);
    }

    @Override
    public CompletionStage<Void> thenRun(Runnable action) {
        return thenRunAsync(action, SAME_THREAD_EXECUTOR);
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor);
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenApplyAsync(convertRunnableToFunction(action), executor);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, SAME_THREAD_EXECUTOR);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultExecutor);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn,
            Executor executor) {
        return thenCompose(result1 -> other.thenApplyAsync(result2 -> fn.apply(result1, result2), executor));
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, SAME_THREAD_EXECUTOR);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        return thenCombineAsync(
                other,
                // transform BiConsumer to BiFunction
                (t, u) -> {
                    action.accept(t, u);
                    return null;
                },
                executor
        );
    }

    @Override
    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, SAME_THREAD_EXECUTOR);
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor);
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return thenCombineAsync(
                other,
                // transform Runnable to BiFunction
                (t, r) -> {
                    action.run();
                    return null;
                },
                executor
        );
    }

    @Override
    public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, SAME_THREAD_EXECUTOR);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultExecutor);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(
            CompletionStage<? extends T> other,
            Function<? super T, U> fn,
            Executor executor) {
        SimpleCompletionStage<T> nextStage = newSimpleCompletionStage();

        BiConsumer<T, Throwable> action = (result, failure) -> {
            if (failure == null) {
                nextStage.complete(result);
            } else {
                nextStage.completeExceptionally(failure);
            }
        };

        // only the first result is accepted by completion stage,
        // the other one is ignored
        this.whenComplete(action);
        other.whenComplete(action);

        return nextStage.thenApplyAsync(fn, executor);
    }

    @Override
    public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, SAME_THREAD_EXECUTOR);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        return applyToEitherAsync(other, convertConsumerToFunction(action), executor);
    }

    @Override
    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, SAME_THREAD_EXECUTOR);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor);
    }

    @Override
    @SuppressWarnings("unchecked") //nasty
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return applyToEitherAsync((CompletionStage<T>) other, convertRunnableToFunction(action), executor);
    }

    @Override
    public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, SAME_THREAD_EXECUTOR);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultExecutor);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        SimpleCompletionStage<U> nextStage = newSimpleCompletionStage();
        addCallbacks(
                result1 -> {
                    try {
                        fn.apply(result1).whenComplete((result2, failure) -> {
                            if (failure == null) {
                                nextStage.complete(result2);
                            } else {
                                nextStage.handleFailure(failure);
                            }
                        });
                    } catch (Throwable e) {
                        nextStage.handleFailure(e);
                    }
                },
                nextStage::handleFailure,
                executor
        );
        return nextStage;
    }

    @Override
    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
        SimpleCompletionStage<T> nextStage = newSimpleCompletionStage();
        addCallbacks(
                nextStage::complete,
                e -> nextStage.acceptResult(() -> fn.apply(e)),
                SAME_THREAD_EXECUTOR
        );
        return nextStage;
    }

    @Override
    public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, SAME_THREAD_EXECUTOR);
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultExecutor);
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        SimpleCompletionStage<T> nextStage = newSimpleCompletionStage();
        addCallbacks(
                result -> nextStage.acceptResult(
                        () -> {
                            action.accept(result, null);
                            return result;
                        }
                ),
                failure -> {
                    try {
                        action.accept(null, failure);
                        nextStage.handleFailure(failure);
                    } catch (Throwable e) {
                        nextStage.handleFailure(e);
                    }
                }, executor
        );
        return nextStage;
    }

    @Override
    public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, SAME_THREAD_EXECUTOR);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultExecutor);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn,
            Executor executor) {
        SimpleCompletionStage<U> nextStage = newSimpleCompletionStage();
        addCallbacks(
                result -> nextStage.acceptResult(() -> fn.apply(result, null)),
                // exceptions are treated as success
                e -> nextStage.acceptResult(() -> fn.apply(null, e)),
                executor
        );
        return nextStage;
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        addCallbacks(
                completableFuture::complete,
                completableFuture::completeExceptionally,
                SAME_THREAD_EXECUTOR
        );
        return completableFuture;
    }


    private <R> SimpleCompletionStage<R> newSimpleCompletionStage() {
        return new SimpleCompletionStage<>(defaultExecutor);
    }


    private Function<T, Void> convertConsumerToFunction(Consumer<? super T> action) {
        return result -> {
            action.accept(result);
            return null;
        };
    }

    private Function<T, Void> convertRunnableToFunction(Runnable action) {
        return result -> {
            action.run();
            return null;
        };
    }

    /**
     * Accepts result provided by the Supplier. If an exception is thrown by the supplier, completes exceptionally.
     *
     * @param supplier generates result
     */
    private void acceptResult(Supplier<? extends T> supplier) {
        try {
            // exception can be thrown only by supplier. All callbacks are generated by us and they do not throw any exceptions
            complete(supplier.get());
        } catch (Throwable e) {
            handleFailure(e);
        }
    }

    /**
     * Wraps exception completes exceptionally.
     *
     * @param e the exception
     */
    private void handleFailure(Throwable e) {
        completeExceptionally(wrapException(e));
    }

    private Throwable wrapException(Throwable e) {
        if (e instanceof CompletionException) {
            return e;
        } else {
            return new CompletionException(e);
        }
    }

    private void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
        callbackRegistry.addCallbacks(successCallback, failureCallback, executor);
    }
}
