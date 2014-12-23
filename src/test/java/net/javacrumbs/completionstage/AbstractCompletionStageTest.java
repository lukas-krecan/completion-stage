package net.javacrumbs.completionstage;

import org.junit.Test;

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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
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
 */
@SuppressWarnings("unchecked")
public abstract class AbstractCompletionStageTest {
    protected static final String VALUE = "test";
    protected static final String VALUE2 = "value2";
    protected static final RuntimeException EXCEPTION = new RuntimeException("Test");
    protected static final String IN_EXECUTOR_THREAD_NAME = "in executor";
    protected static final String IN_DEFAULT_EXECUTOR_THREAD_NAME = "in default executor";
    private static final String MAIN = "main";

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

        Consumer<String> consumer = mock(Consumer.class);
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

        Consumer<String> consumer = mock(Consumer.class);
        completionStage.thenAccept(consumer);

        finish(completionStage);

        verify(consumer).accept(VALUE);
    }

    @Test
    public void acceptAsyncShouldBeCalledUsingExecutor() throws InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        CountDownLatch waitLatch = new CountDownLatch(1);

        Executor executor = new ThreadNamingExecutor(IN_EXECUTOR_THREAD_NAME);
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

        Executor executor = new ThreadNamingExecutor(IN_EXECUTOR_THREAD_NAME);
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
    public void exceptionallyShouldTranslateExceptionToAValue() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        Consumer<String> consumer = mock(Consumer.class);
        Function<Throwable, String> function = mock(Function.class);
        when(function.apply(EXCEPTION)).thenReturn(VALUE);
        completionStage.exceptionally(function).thenAccept(consumer);

        finish(completionStage);

        verify(function, times(1)).apply(EXCEPTION);
        verify(consumer).accept(VALUE);
    }

    @Test
    public void exceptionallyShouldPassValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Consumer<String> consumer = mock(Consumer.class);
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

        verify(errorHandler).apply(isA(CompletionException.class));
        verify(conversion).apply(VALUE);
    }

    @Test
    public void ifExceptionallyFunctionFailsItShouldBePassedFurther() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        Consumer<String> consumer = mock(Consumer.class);
        Function<Throwable, String> errorHandler = mock(Function.class);
        when(errorHandler.apply(EXCEPTION)).thenReturn(VALUE);
        completionStage.exceptionally(e -> {
            throw EXCEPTION;
        }).exceptionally(errorHandler).thenAccept(consumer);

        finish(completionStage);

        verify(errorHandler).apply(isA(CompletionException.class));
        verify(consumer).accept(null);
    }

    @Test
    public void shouldCombineValues() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);
        Consumer<Integer> consumer = mock(Consumer.class);

        when(combiner.apply(VALUE, VALUE2)).thenReturn(5);

        completionStage1.thenCombine(completionStage2, combiner).thenAccept(consumer);
        finish(completionStage1);

        verify(combiner).apply(VALUE, VALUE2);
        verify(consumer).accept(5);
    }

    @Test
    public void shouldCombineValuesInOppositeOrder() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);
        Consumer<Integer> consumer = mock(Consumer.class);

        when(combiner.apply(VALUE2, VALUE)).thenReturn(5);

        completionStage2.thenCombine(completionStage1, combiner).thenAccept(consumer);
        finish(completionStage1);

        verify(combiner).apply(VALUE2, VALUE);
        verify(consumer).accept(5);
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

        Consumer<String> consumer = mock(Consumer.class);
        Function<Throwable, Void> errorHandler = mock(Function.class);
        when(errorHandler.apply(EXCEPTION)).thenReturn(null);
        doThrow(EXCEPTION).when(consumer).accept(VALUE);
        completionStage.thenAccept(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(errorHandler).apply(isA(CompletionException.class));
        verify(consumer).accept(VALUE);
    }

    @Test
    public void thenApplyShouldTransformTheValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Consumer<Integer> consumer = mock(Consumer.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).thenAccept(consumer);

        finish(completionStage);

        verify(consumer).accept(8);
    }

    @Test
    public void shouldNotFailOnException() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        Consumer<Integer> consumer = mock(Consumer.class);
        Function<Throwable, Void> errorFunction = mock(Function.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).thenAccept(consumer).exceptionally(errorFunction);

        finish(completionStage);

        verifyZeroInteractions(consumer);
        verify(errorFunction, times(1)).apply(isA(CompletionException.class));
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

        verify(errorHandler).apply(isA(CompletionException.class));
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

        verify(errorHandler).apply(isA(CompletionException.class));
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
        completionStage.thenApply(String::length).thenApply(i -> i * 2).whenComplete(consumer);

        finish(completionStage);

        verify(consumer).accept(8, null);
    }

    @Test
    public void whenCompleteShouldPassException() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);

        BiConsumer<Integer, Throwable> consumer = mock(BiConsumer.class);
        Function<Throwable, Integer> errorHandler = mock(Function.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).whenComplete(consumer).exceptionally(errorHandler);

        finish(completionStage);

        verify(consumer).accept((Integer) isNull(), isA(CompletionException.class));
        verify(errorHandler).apply(isA(CompletionException.class));
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

        verify(errorHandler).apply(isA(CompletionException.class));
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

        verify(errorHandler).apply(isA(CompletionException.class));
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


        Consumer<String> consumer = mock(Consumer.class);
        completionStage.thenCompose(r -> completionStage2).thenAccept(consumer);

        finish(completionStage);
        finish(completionStage2);

        verify(consumer, times(1)).accept(VALUE2);
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

        verify(handler, times(1)).apply(isNull(String.class), isA(CompletionException.class));
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
            Thread thread = new Thread(command);
            thread.setName(threadName);
            thread.start();
        }
    }
}