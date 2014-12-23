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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

class SimpleCompletionStage<T> implements CompletionStage<T> {
    static final Executor SAME_THREAD_EXECUTOR = Runnable::run;

    private final ListenableCallbackRegistry<T> callbackRegistry = new ListenableCallbackRegistry<>();
    private final Executor defaultExecutor;

    /**
     * Creates SimpleCompletionStage.
     * @param defaultExecutor executor to be used for all async method without executor parameter.
     */
    public SimpleCompletionStage(Executor defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }

    /**
     * Notifies all callbacks about the result.
     * @param result result of the previous stage.
     */
    public void complete(T result) {
        callbackRegistry.success(result);
    }

    /**
     * Notifies all callbacks about the failure.
     * @param e exception thrown from the previous stage.
     */
    public void completeExceptionally(Throwable e) {
        callbackRegistry.failure(e);
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
    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        SimpleCompletionStage<U> newCompletionStage = newSimpleCompletionStage();
        callbackRegistry.addCallbacks(
                result -> transformResultAndSendItToNextStage(fn, newCompletionStage, result),
                e -> handleFailure(newCompletionStage, e),
                executor
        );
        return newCompletionStage;
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
    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        SimpleCompletionStage<V> newCompletionStage = newSimpleCompletionStage();
        callbackRegistry.addCallbacks(
                result1 -> {
                    try {
                        other.thenAccept(result2 -> newCompletionStage.complete(fn.apply(result1, result2)));
                    } catch (Throwable e) {
                        handleFailure(newCompletionStage, e);
                    }
                },
                e -> handleFailure(newCompletionStage, e),
                executor
        );
        return newCompletionStage;
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
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        SimpleCompletionStage<U> newCompletionStage = newSimpleCompletionStage();
        AtomicBoolean processed = new AtomicBoolean(false);
        callbackRegistry.addCallbacks(
                result -> {
                    if (not(processed)) {
                        transformResultAndSendItToNextStage(fn, newCompletionStage, result);
                    }
                },
                e -> {
                    if (not(processed)) {
                        handleFailure(newCompletionStage, e);
                    }
                },
                executor
        );
        other.whenCompleteAsync((result, failure) -> {
            if (not(processed)) {
                if (failure == null) {
                    transformResultAndSendItToNextStage(fn, newCompletionStage, result);
                } else {
                    handleFailure(newCompletionStage, failure);
                }
            }
        }, executor);
        return newCompletionStage;
    }

    private boolean not(AtomicBoolean processed) {
        return processed.compareAndSet(false, true);
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
        return thenComposeAsync(fn ,defaultExecutor);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        SimpleCompletionStage<U> newCompletionStage = newSimpleCompletionStage();
        callbackRegistry.addCallbacks(
                result -> {
                    try {
                        fn.apply(result).thenAccept(newCompletionStage::complete);
                    } catch (Throwable e) {
                        handleFailure(newCompletionStage, e);
                    }
                },
                e -> handleFailure(newCompletionStage, e),
                executor
        );
        return newCompletionStage;
    }

    @Override
    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
        SimpleCompletionStage<T> newCompletionStage = newSimpleCompletionStage();
        callbackRegistry.addCallbacks(
                newCompletionStage::complete,
                // null in the following line is ignored. e is used instead
                e -> transformResultAndSendItToNextStage(x -> fn.apply(e), newCompletionStage, null),
                SAME_THREAD_EXECUTOR
        );
        return newCompletionStage;
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
        SimpleCompletionStage<T> newCompletionStage = newSimpleCompletionStage();
        callbackRegistry.addCallbacks(
                result -> transformResultAndSendItToNextStage(
                        r -> {
                            action.accept(r, null);
                            return null;
                        },
                        newCompletionStage,
                        result
                ),
                failure -> {
                    try {
                        action.accept(null, failure);
                        handleFailure(newCompletionStage, failure);
                    } catch (Throwable e) {
                        handleFailure(newCompletionStage, e);
                    }
                }, executor
        );
        return newCompletionStage;
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
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        SimpleCompletionStage<U> newCompletionStage = newSimpleCompletionStage();
        callbackRegistry.addCallbacks(
                result -> transformResultAndSendItToNextStage(t -> fn.apply(t, null), newCompletionStage, result),
                // exceptions are treated as success
                e -> transformResultAndSendItToNextStage(x -> fn.apply(null, e), newCompletionStage, null),
                executor
        );
        return newCompletionStage;
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        callbackRegistry.addCallbacks(
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
     * Transforms the result using fn and sends it to nextStage. Exceptions are handled using handleFailure
     * @param fn transformation function
     * @param nextStage stage to send the result to
     * @param result the result
     * @param <U> return type
     */
    private <U> void transformResultAndSendItToNextStage(Function<? super T, ? extends U> fn, SimpleCompletionStage<U> nextStage, T result) {
        try {
            nextStage.complete(fn.apply(result));
        } catch (Throwable e) {
            handleFailure(nextStage, e);
        }
    }

    /**
     * Wraps exception and sends it to next execution stage.
     * @param nextStage stage to send the exception to
     * @param e the exception
     */
    private void handleFailure(SimpleCompletionStage<?> nextStage, Throwable e) {
        nextStage.completeExceptionally(wrapException(e));
    }

    private Throwable wrapException(Throwable e) {
        if (e instanceof CompletionException) {
            return e;
        } else {
            return new CompletionException(e);
        }
    }


}
