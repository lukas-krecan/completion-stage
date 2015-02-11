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

import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Thread.currentThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


/**
 * original test followingStagesShouldBeCalledInTeSameThread
 * callback hell
 * followingStagesShouldBeCalledInTeSameThread - can be executed in the main thread
 * Duplicity
 * Transformation to function
 * then compose
 * state
 * coverage is great
 * exception handling in combine (which one will handle the exception?)
 * CompletableFuture.doThenCombine
 */
@SuppressWarnings("unchecked")
public abstract class AbstractCompletionStageTest {
    protected static final String VALUE = "test";
    protected static final String VALUE2 = "value2";
    protected static final RuntimeException EXCEPTION = new RuntimeException("Test");
    protected static final String IN_EXECUTOR_THREAD_NAME = "in executor";
    protected static final String IN_DEFAULT_EXECUTOR_THREAD_NAME = "in default executor";
    private static final String MAIN = "main";
    private final Executor executor = new ThreadNamingExecutor(IN_EXECUTOR_THREAD_NAME);
    private final Consumer<String> consumer = mock(Consumer.class);
    private final BiConsumer<String, Throwable> biConsumer = mock(BiConsumer.class);
    private final Consumer<Integer> intConsumer = mock(Consumer.class);

    protected abstract CompletionStage<String> createCompletionStage(String value);

    protected abstract CompletionStage<String> createCompletionStage(Throwable e);

    protected abstract void finish(CompletionStage<String> c);

    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    protected Executor defaultExecutor = new ThreadNamingExecutor(IN_DEFAULT_EXECUTOR_THREAD_NAME);

    protected boolean finished() {
        return true;
    }

    @Test
    public void acceptEitherAcceptsOnlyOneValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage);
        finish(completionStage2);

        completionStage.acceptEither(completionStage2, consumer);

        verify(consumer, times(1)).accept(any(String.class));
    }

    @Test
    public void runAfterEitherCalledOnlyOnce() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage);

        Runnable runnable = mock(Runnable.class);
        completionStage.runAfterEither(completionStage2, runnable);

        finish(completionStage2);

        verify(runnable, times(1)).run();
    }

    @Test
    public void acceptShouldWork() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        completionStage.thenAccept(consumer);

        finish(completionStage);

        verify(consumer).accept(VALUE);
    }

    @Test
    public void acceptAsyncShouldBeCalledUsingExecutor() throws InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        CountDownLatch waitLatch = new CountDownLatch(1);

        completionStage.thenAcceptAsync(r -> {
            assertEquals(IN_EXECUTOR_THREAD_NAME, currentThread().getName());
            waitLatch.countDown();
        }, executor).exceptionally(errorHandler(waitLatch));

        finish(completionStage);

        waitLatch.await();
        assertThat(failures).isEmpty();
    }

    @Test
    public void followingStagesShouldBeCalledInTeSameThread() throws InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        CountDownLatch waitLatch = new CountDownLatch(1);

        completionStage
                .thenApplyAsync(r -> {
                    assertEquals(IN_EXECUTOR_THREAD_NAME, currentThread().getName());
                    return "a";
                }, executor)
                .thenAccept(r -> {
                    // In fact it can be executed even in main thread depending if the previous callback finished sooner than
                    // thenAccept is called
                    // assertEquals(IN_EXECUTOR_THREAD_NAME, currentThread().getName());
                    assertEquals("a", r);
                    waitLatch.countDown();
                })
                .exceptionally(errorHandler(waitLatch));

        finish(completionStage);

        waitLatch.await();
        assertThat(failures).isEmpty();
    }

    @Test
    public void allAsyncCallShouldBeCalledInDefaultExecutor() throws InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        CountDownLatch waitLatch = new CountDownLatch(1);
        currentThread().setName(MAIN);

        completionStage
                .thenApplyAsync(r -> {
                    assertNotEquals(MAIN, currentThread().getName());
                    return "a";
                })
                .thenAcceptAsync(r -> {
                    assertNotEquals(MAIN, currentThread().getName());
                    assertEquals("a", r);
                    waitLatch.countDown();
                })
                .exceptionally(errorHandler(waitLatch));

        finish(completionStage);

        waitLatch.await();
        assertThat(failures).isEmpty();
    }

    private Function<Throwable, Void> errorHandler(CountDownLatch waitLatch) {
        return e -> {
            failures.add(e);
            waitLatch.countDown();
            return null;
        };
    }

    @Test
    public void whenCompleteShouldAcceptUnwrappedException() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        completionStage.whenComplete(biConsumer);

        finish(completionStage);
        verify(biConsumer, times(1)).accept(null, EXCEPTION);
    }

    @Test
    public void handleShouldAcceptUnwrappedException() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        BiFunction<String, Throwable, ?> handler = mock(BiFunction.class);
        completionStage.handle(handler);

        finish(completionStage);
        verify(handler, times(1)).apply(null, EXCEPTION);
    }

    @Test
    public void exceptionallyShouldTranslateExceptionToAValue() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        Function<Throwable, String> function = mock(Function.class);
        when(function.apply(EXCEPTION)).thenReturn(VALUE);
        completionStage.exceptionally(function).thenAccept(consumer);

        finish(completionStage);

        verify(function, times(1)).apply(EXCEPTION);
        verify(consumer, times(1)).accept(VALUE);
    }

    @Test
    public void exceptionallyShouldPassValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Function<Throwable, String> function = mock(Function.class);
        when(function.apply(EXCEPTION)).thenReturn(VALUE);
        completionStage.exceptionally(function).thenAccept(consumer);

        finish(completionStage);

        verifyZeroInteractions(function);
        verify(consumer).accept(VALUE);
    }

    @Test
    public void exceptionFromThenApplyShouldBePassedToTheNextPhase() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Function<String, Integer> conversion = mock(Function.class);
        Function<Throwable, Integer> errorHandler = mock(Function.class);
        when(errorHandler.apply(EXCEPTION)).thenReturn(null);
        when(conversion.apply(VALUE)).thenThrow(EXCEPTION);
        completionStage.thenApply(conversion).exceptionally(errorHandler);

        finish(completionStage);

        verify(errorHandler).apply(isACompletionException());
        verify(conversion).apply(VALUE);
    }

    @Test
    public void ifExceptionallyFunctionFailsItShouldBePassedFurther() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        Function<Throwable, String> errorHandler = mock(Function.class);
        when(errorHandler.apply(EXCEPTION)).thenReturn(VALUE);
        completionStage.exceptionally(e -> {
            throw EXCEPTION;
        }).exceptionally(errorHandler).thenAccept(consumer);

        finish(completionStage);

        verify(errorHandler).apply(isACompletionException());
        verify(consumer).accept(null);
    }

    @Test
    public void shouldCombineValues() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);
        when(combiner.apply(VALUE, VALUE2)).thenReturn(5);

        completionStage1.thenCombine(completionStage2, combiner).thenAccept(intConsumer);
        finish(completionStage1);

        verify(combiner).apply(VALUE, VALUE2);
        verify(intConsumer).accept(5);
    }

    @Test
    public void shouldCombineValuesInOppositeOrder() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);

        when(combiner.apply(VALUE2, VALUE)).thenReturn(5);

        completionStage2.thenCombine(completionStage1, combiner).thenAccept(intConsumer);
        finish(completionStage1);

        verify(combiner).apply(VALUE2, VALUE);
        verify(intConsumer).accept(5);
    }

    @Test
    public void combineShouldHandleExceptionCorrectly() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);


        BiFunction<Object, Throwable, ?> handler = mock(BiFunction.class);

        completionStage1.thenCombine(completionStage2, (a, b) -> {
            throw EXCEPTION;
        }).handle(handler);

        finish(completionStage1);

        verify(handler).apply(isNull(String.class), isACompletionException());
    }

    @Test
    public void combineShouldHandlePreviousStageFailureCorrectly() {
        CompletionStage<String> completionStage1 = createCompletionStage(EXCEPTION);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);


        BiFunction<Object, Throwable, ?> handler = mock(BiFunction.class);
        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);

        completionStage1.thenCombine(completionStage2, combiner).handle(handler);

        finish(completionStage1);

        verify(handler).apply(isNull(String.class), isACompletionException());
        verifyZeroInteractions(combiner);
    }

    @Test
    public void combineShouldHandleTheOtherPreviousStageFailureCorrectly() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(EXCEPTION);
        finish(completionStage2);


        BiFunction<Object, Throwable, ?> handler = mock(BiFunction.class);
        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);

        completionStage1.thenCombine(completionStage2, combiner).handle(handler);

        finish(completionStage1);

        verify(handler).apply(isNull(String.class), isACompletionException());
        verifyZeroInteractions(combiner);
    }

    @Test
    public void combineAsyncShouldExecuteFunctionInCorrectExecutor() throws ExecutionException, InterruptedException {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);


        CompletableFuture<Object> completableFuture = completionStage1.thenCombineAsync(completionStage2, (r1, r2) -> {
            assertEquals(IN_EXECUTOR_THREAD_NAME, currentThread().getName());
            return null;
        }, executor).toCompletableFuture();

        finish(completionStage1);
        finish(completionStage2);

        completableFuture.get();
    }

    @Test
    public void shouldAcceptBothValues() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        BiConsumer<String, String> biConsumer = mock(BiConsumer.class);

        completionStage1.thenAcceptBoth(completionStage2, biConsumer);
        if (!finished()) {
            verifyZeroInteractions(biConsumer);
        }

        finish(completionStage1);

        verify(biConsumer).accept(VALUE, VALUE2);
    }

    @Test
    public void shouldRunAfterBothValues() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        Runnable runnable = mock(Runnable.class);

        completionStage1.runAfterBoth(completionStage2, runnable);
        if (!finished()) {
            verifyZeroInteractions(runnable);
        }

        finish(completionStage1);

        verify(runnable).run();
    }


    @Test
    public void exceptionFromThenAcceptShouldBePassedToTheNextPhase() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Function<Throwable, Void> errorHandler = mock(Function.class);
        when(errorHandler.apply(EXCEPTION)).thenReturn(null);
        doThrow(EXCEPTION).when(consumer).accept(VALUE);
        completionStage.thenAccept(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(errorHandler).apply(isACompletionException());
        verify(consumer).accept(VALUE);
    }

    @Test
    public void thenApplyShouldTransformTheValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        completionStage.thenApply(String::length).thenApply(i -> i * 2).thenAccept(intConsumer);

        finish(completionStage);

        verify(intConsumer).accept(8);
    }

    @Test
    public void exceptionFromTheNextPhaseShouldNotAffectPreviousPhases() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        BiFunction<Object, Throwable, ?> handler = mock(BiFunction.class);

        completionStage.thenApply(String::length).thenApply(i -> {
            throw EXCEPTION;
        }).handle(handler);
        finish(completionStage);

        verify(handler, times(1)).apply(isNull(), isACompletionException());
    }

    @Test
    public void shouldNotFailOnException() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        Function<Throwable, Void> errorFunction = mock(Function.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).thenAccept(intConsumer).exceptionally(errorFunction);

        finish(completionStage);

        verifyZeroInteractions(intConsumer);
        verify(errorFunction, times(1)).apply(isACompletionException());
    }

    @Test
    public void handleShouldBeCalled() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        BiFunction<String, Throwable, Integer> consumer = mock(BiFunction.class);
        completionStage.handle(consumer);

        finish(completionStage);

        verify(consumer).apply(VALUE, null);
    }

    @Test
    public void exceptionFromHandleShouldBePropagatedOnSuccess() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        BiFunction<String, Throwable, Integer> consumer = (s, throwable) -> {
            throw EXCEPTION;
        };

        Function<Throwable, Integer> errorHandler = mock(Function.class);
        completionStage.handle(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(errorHandler).apply(isACompletionException());
    }

    @Test
    public void exceptionFromHandleShouldBePropagatedOnError() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        BiFunction<String, Throwable, Integer> consumer = (s, throwable) -> {
            throw EXCEPTION;
        };

        Function<Throwable, Integer> errorHandler = mock(Function.class);
        completionStage.handle(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(errorHandler).apply(isACompletionException());
    }

    @Test
    public void handleShouldNotPassException() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        BiFunction<String, Throwable, Integer> consumer = mock(BiFunction.class);
        Function<Throwable, Integer> errorHandler = mock(Function.class);
        completionStage.handle(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(consumer).apply(null, EXCEPTION);
        verifyZeroInteractions(errorHandler);
    }


    @Test
    public void whenCompleteShouldAcceptValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        BiConsumer<Integer, Throwable> consumer = mock(BiConsumer.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).whenComplete(consumer).thenAccept(intConsumer);

        finish(completionStage);

        verify(consumer).accept(8, null);
        verify(intConsumer).accept(8);
    }

    @Test
    public void whenCompleteShouldPassException() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        BiConsumer<Integer, Throwable> consumer = mock(BiConsumer.class);
        Function<Throwable, Integer> errorHandler = mock(Function.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).whenComplete(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(consumer).accept((Integer) isNull(), isACompletionException());
        verify(errorHandler).apply(isACompletionException());
    }

    @Test
    public void whenCompleteShouldPassExceptionFromConsumerOnSuccess() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        BiConsumer<String, Throwable> consumer = (r, e) -> {
            throw EXCEPTION;
        };
        Function<Throwable, String> errorHandler = mock(Function.class);
        completionStage.whenComplete(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(errorHandler).apply(isACompletionException());
    }

    @Test
    public void whenCompleteShouldPassExceptionFromConsumerOnFailure() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        BiConsumer<String, Throwable> consumer = (r, e) -> {
            throw EXCEPTION;
        };
        Function<Throwable, String> errorHandler = mock(Function.class);
        completionStage.whenComplete(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(errorHandler).apply(isACompletionException());
    }

    @Test
    public void thenRunShouldRun() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Runnable runnable = mock(Runnable.class);
        completionStage.thenRun(runnable);

        finish(completionStage);

        verify(runnable).run();
    }

    @Test
    public void thenComposeWaitsForTheOtherResult() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);

        completionStage.thenCompose(r -> completionStage2).thenAccept(consumer);

        finish(completionStage);
        finish(completionStage2);

        verify(consumer, times(1)).accept(VALUE2);
    }

    @Test
    public void thenComposePassesPreviousFailure() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);


        BiFunction<String, Throwable, ?> handler = mock(BiFunction.class);
        completionStage.thenCompose(r -> completionStage2).handle(handler);

        finish(completionStage);
        finish(completionStage2);

        verify(handler).apply(isNull(String.class), isACompletionException());
    }

    @Test
    public void thenComposePassesFailureFromTheOtherCompletionStage() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(EXCEPTION);

      completionStage.thenCompose(r -> completionStage2).whenComplete(biConsumer);

        finish(completionStage);
        finish(completionStage2);


        verify(biConsumer).accept(isNull(String.class), isACompletionException());
    }


    @Test
    public void toCompletableFutureShouldPassValue() throws ExecutionException, InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        CompletableFuture<String> completableFuture = completionStage.toCompletableFuture();

        finish(completionStage);

        assertThat(completableFuture.get()).isEqualTo(VALUE);
    }

    @Test
    public void toCompletableFutureShouldPassException() throws ExecutionException, InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        CompletableFuture<String> completableFuture = completionStage.toCompletableFuture();

        finish(completionStage);

        try {
            completableFuture.get();
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isSameAs(EXCEPTION);
        }
    }

    @Test
    public void thenComposeWrapsExceptionIfFunctionFails() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        BiFunction<Object, Throwable, ?> handler = mock(BiFunction.class);
        completionStage.thenCompose(r -> {
            throw EXCEPTION;
        }).handle(handler);

        finish(completionStage);

        verify(handler, times(1)).apply(isNull(String.class), isACompletionException());
    }

    @Test
    public void combineAsyncShouldWork() throws ExecutionException, InterruptedException {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);

        CompletionStage<String> combined = completionStage1.thenCombineAsync(completionStage2, (a, b) -> a + b);

        finish(completionStage1);
        finish(completionStage2);

        assertThat(combined.toCompletableFuture().get()).isEqualTo(VALUE + VALUE2);
    }

    @Test
    public void runAfterBothAsyncShouldWaitForBoth() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);

        Runnable runnable = mock(Runnable.class);
        CompletionStage<Void> newCompletionStage = completionStage1.runAfterBothAsync(completionStage2, runnable);

        finish(completionStage1);
        finish(completionStage2);

        waitForIt(newCompletionStage);
        verify(runnable, times(1)).run();
    }

    @Test
    public void thenAcceptBothAsyncShouldWaitForBoth() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);

        BiConsumer<String, String> consumer = mock(BiConsumer.class);
        CompletionStage<Void> newCompletionStage = completionStage1.thenAcceptBothAsync(completionStage2, consumer);

        finish(completionStage1);
        finish(completionStage2);

        waitForIt(newCompletionStage);
        verify(consumer).accept(VALUE, VALUE2);
    }

    @Test
    public void applyToEitherShouldProcessOnlyOne() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage1);

        Function<? super String, Object> function = mock(Function.class);
        completionStage1.applyToEither(completionStage2, function);

        finish(completionStage2);

        verify(function, times(1)).apply(anyString());
    }

    @Test
    public void applyToEitherAsyncShouldExecuteFunctionInExecutor() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage1);

        CompletionStage<Object> newCompletionStage = completionStage1.applyToEitherAsync(completionStage2, s -> {
            assertEquals(IN_EXECUTOR_THREAD_NAME, currentThread().getName());
            return null;
        }, executor);

        finish(completionStage2);

        waitForIt(newCompletionStage);

    }

    @Test // just for code coverage. The code is tested by sync version
    public void acceptEitherAsyncDoesNotFail() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage1);
        finish(completionStage2);

        completionStage1.acceptEitherAsync(completionStage2, x -> {
        });
    }

    @Test // just for code coverage. The code is tested by sync version
    public void runAfterEitherAsyncDoesNotFail() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage1);
        finish(completionStage2);

        completionStage1.runAfterEitherAsync(completionStage2, () -> {
        });
    }

    @Test // just for code coverage. The code is tested by sync version
    public void thenComposeAsyncAsyncDoesNotFail() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage1);
        finish(completionStage2);

        completionStage1.thenComposeAsync(x -> completionStage2);
    }

    @Test // just for code coverage. The code is tested by sync version
    public void whenCompleteAsyncDoesNotFail() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        finish(completionStage1);

        completionStage1.whenCompleteAsync((r, e) -> {
        });
    }

    @Test // just for code coverage. The code is tested by sync version
    public void handleAsyncDoesNotFail() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        finish(completionStage1);

        completionStage1.handleAsync((r, e) -> 1);
    }

    @Test // just for code coverage. The code is tested by sync version
    public void thenRunAsyncDoesNotFail() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        finish(completionStage1);

        completionStage1.thenRunAsync(() -> {
        });
    }


    private void waitForIt(CompletionStage<?> newCompletionStage) {
        try {
            newCompletionStage.toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            fail(e.getMessage());
        }
    }

    private CompletionException isACompletionException() {
        return argThat(new ArgumentMatcher<CompletionException>() {
            @Override
            public boolean matches(Object argument) {
                return argument instanceof CompletionException && ((CompletionException) argument).getCause() == EXCEPTION;
            }
        });
    }

    /**
     * Names thread
     */
    protected static class ThreadNamingExecutor implements Executor {
        private final String threadName;

        protected ThreadNamingExecutor(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public void execute(Runnable command) {
            String originalName = currentThread().getName();
            currentThread().setName(threadName);
            try {
                command.run();
            } finally {
                currentThread().setName(originalName);
            }
        }
    }
}