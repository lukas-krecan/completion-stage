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

import java.util.Objects;
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
class SimpleCompletionStage<T> extends CompletionStageAdapter<T> implements CompletableCompletionStage<T> {

    private final CallbackRegistry<T> callbackRegistry = new CallbackRegistry<>();

    /**
     * Creates SimpleCompletionStage.
     *
     * @param defaultExecutor executor to be used for all async method without executor parameter.
     */
    public SimpleCompletionStage(Executor defaultExecutor) {
        super(defaultExecutor);
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
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenApplyAsync(convertConsumerToFunction(action), executor);
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenApplyAsync(convertRunnableToFunction(action), executor);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn,
            Executor executor) {
        return thenCompose(result1 -> other.thenApplyAsync(result2 -> fn.apply(result1, result2), executor));
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
    public <U> CompletionStage<U> applyToEitherAsync(
            CompletionStage<? extends T> other,
            Function<? super T, U> fn,
            Executor executor) {
        return doApplyToEitherAsync(this, other, fn, executor);
    }

    /**
     * This method exists just to reconcile generics when called from {@link #runAfterEitherAsync}
     * which has unexpected type of parameter "other". The alternative is to ignore compiler warning.
     */
    private <R, U> CompletionStage<U> doApplyToEitherAsync(
            CompletionStage<? extends R> first,
            CompletionStage<? extends R> second,
            Function<? super R, U> fn,
            Executor executor) {
        SimpleCompletionStage<R> nextStage = newSimpleCompletionStage();

        // only the first result is accepted by completion stage,
        // the other one is ignored
        BiConsumer<R, Throwable> action = nextStage.completeHandler();
        first.whenComplete(action);
        second.whenComplete(action);

        return nextStage.thenApplyAsync(fn, executor);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        return applyToEitherAsync(other, convertConsumerToFunction(action), executor);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return doApplyToEitherAsync(this, other, convertRunnableToFunction(action), executor);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        SimpleCompletionStage<U> nextStage = newSimpleCompletionStage();
        addCallbacks(
                result1 -> {
                    try {
                        fn.apply(result1).whenComplete(nextStage.completeHandler());
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

    private <R> Function<R, Void> convertRunnableToFunction(Runnable action) {
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
     * Handler that can be used in whenComplete method.
     *
     * @return BiConsumer that passes values to this CompletionStage.
     */
    private BiConsumer<T, Throwable> completeHandler() {
        return (result, failure) -> {
            if (failure == null) {
                complete(result);
            } else {
                handleFailure(failure);
            }
        };
    }

    /**
     * Wraps exception completes exceptionally.
     *
     * @param e the exception
     */
    private void handleFailure(Throwable e) {
        completeExceptionally(wrapException(e));
    }

    /**
     * Wraps exception to a {@link java.util.concurrent.CompletionException} if needed.
     *
     * @param e exception to be wrapped
     * @return CompletionException
     */
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
