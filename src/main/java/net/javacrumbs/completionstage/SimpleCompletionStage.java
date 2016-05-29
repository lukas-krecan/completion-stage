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
 * Please do not use this class directly, use {@link CompletionStageFactory} to create instances.
 * {@link java.util.concurrent.CompletionStage} implementation that is built on top of standard executors.
 */
public class SimpleCompletionStage<T> extends CompletionStageAdapter<T> implements CompletableCompletionStage<T> {

    private final CallbackRegistry<T> callbackRegistry = new CallbackRegistry<>();
    private final CompletableCompletionStageFactory completionStageFactory;

    /**
     * Creates SimpleCompletionStage.
     *
     * @param defaultExecutor executor to be used for all async method without executor parameter.
     * @param completionStageFactory factory to create next stages
     */
    public SimpleCompletionStage(Executor defaultExecutor, CompletableCompletionStageFactory completionStageFactory) {
        super(defaultExecutor);
        this.completionStageFactory = completionStageFactory;
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
    	CompletableCompletionStage<U> nextStage = newCompletableCompletionStage();
        addCallbacks(
                result -> acceptResult(nextStage, () -> fn.apply(result)),
                handleFailure(nextStage),
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
    	CompletableCompletionStage<R> nextStage = newCompletableCompletionStage();

        // only the first result is accepted by completion stage,
        // the other one is ignored
        BiConsumer<R, Throwable> action = completeHandler(nextStage);
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
    	CompletableCompletionStage<U> nextStage = newCompletableCompletionStage();
        addCallbacks(
                result1 -> {
                    try {
                        fn.apply(result1).whenComplete( completeHandler(nextStage) );
                    } catch (Throwable e) {
                        handleFailure(nextStage, e);
                    }
                },
                handleFailure(nextStage),
                executor
        );
        return nextStage;
    }

    @Override
    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
    	CompletableCompletionStage<T> nextStage = newCompletableCompletionStage();
        addCallbacks(
                nextStage::complete,
                e -> acceptResult(nextStage, () -> fn.apply(e)),
                SAME_THREAD_EXECUTOR
        );
        return nextStage;
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    	CompletableCompletionStage<T> nextStage = newCompletableCompletionStage();
        addCallbacks(
                result -> acceptResult(
                		nextStage,
                        () -> {
                            action.accept(result, null);
                            return result;
                        }
                ),
                failure -> {
                    try {
                        action.accept(null, failure);
                        handleFailure(nextStage, failure);
                    } catch (Throwable e) {
                        handleFailure(nextStage, e);
                    }
                }, executor
        );
        return nextStage;
    }

    @Override
    public <U> CompletionStage<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn,
            Executor executor) {
    	CompletableCompletionStage<U> nextStage = newCompletableCompletionStage();
        addCallbacks(
                result -> acceptResult(nextStage,() -> fn.apply(result, null)),
                // exceptions are treated as success
                e -> acceptResult(nextStage, () -> fn.apply(null, e)),
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


	private <R> CompletableCompletionStage<R> newCompletableCompletionStage() {
        return completionStageFactory.createCompletionStage();
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
     * 
     */
    
    private static <T> void acceptResult(CompletableCompletionStage<T> s, Supplier<? extends T> supplier) {
        try {
            // exception can be thrown only by supplier. All callbacks are generated by us and they do not throw any exceptions
            s.complete(supplier.get());
        } catch (Throwable e) {
            handleFailure(s, e);
        }
    }

    /**
     * Handler that can be used in whenComplete method.
     *
     * @return BiConsumer that passes values to this CompletionStage.
     */
    private static <T> BiConsumer<T, Throwable> completeHandler(CompletableCompletionStage<T> s) {
        return (result, failure) -> {
            if (failure == null) {
                s.complete(result);
            } else {
                handleFailure(s, failure);
            }
        };
    }

    /**
     * Wraps exception completes exceptionally.
     */
    private static Consumer<Throwable> handleFailure(CompletableCompletionStage<?> s) {
    	return (e) -> handleFailure(s, e);
    }
    
    private static void handleFailure(CompletableCompletionStage<?> s, Throwable e) {
    	s.completeExceptionally(wrapException(e));
    }
    

    /**
     * Wraps exception to a {@link java.util.concurrent.CompletionException} if needed.
     *
     * @param e exception to be wrapped
     * @return CompletionException
     */
    private static Throwable wrapException(Throwable e) {
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
