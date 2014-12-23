package net.javacrumbs.futureconverter.java8common;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Thread.currentThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
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
 */
public abstract class AbstractCompletionStageTest {
    protected static final String VALUE = "test";
    protected static final String VALUE2 = "value2";
    protected static final RuntimeException EXCEPTION = new RuntimeException("Test");
    public static final String IN_EXECUTOR_THREAD_NAME = "in executor";

    protected abstract CompletionStage<String> createCompletionStage(String value);

    protected abstract CompletionStage<String> createExceptionalCompletionStage(Throwable e);

    protected abstract void finishCalculation(CompletionStage<String> c);

    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    @Test
    public void acceptEitherAcceptsOnlyOneValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finishCalculation(completionStage);
        finishCalculation(completionStage2);

        Consumer<String> consumer = mock(Consumer.class);
        completionStage.acceptEither(completionStage2, consumer);

        verify(consumer, times(1)).accept(any(String.class));
    }

    @Test
    public void acceptShouldWork() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Consumer<String> consumer = mock(Consumer.class);
        completionStage.thenAccept(consumer);

        finishCalculation(completionStage);

        verify(consumer).accept(VALUE);
    }

    @Test
    public void acceptAsyncShouldBeCalledUsingExecutor() throws InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        CountDownLatch waitLatch = new CountDownLatch(1);

        Executor executor = new ThreadNamingExecutor();
        completionStage.thenAcceptAsync(r -> {
            assertEquals(IN_EXECUTOR_THREAD_NAME, currentThread().getName());
            waitLatch.countDown();
        }, executor).exceptionally(errorHandler(waitLatch));

        finishCalculation(completionStage);

        waitLatch.await();
        assertThat(failures).isEmpty();
    }

    @Test
    public void followingStagesShouldBeCalledInTeSameThread() throws InterruptedException {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        CountDownLatch waitLatch = new CountDownLatch(1);

        Executor executor = new ThreadNamingExecutor();
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

        finishCalculation(completionStage);

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
        CompletionStage<String> completionStage = createExceptionalCompletionStage(EXCEPTION);

        Consumer<String> consumer = mock(Consumer.class);
        Function<Throwable, String> function = mock(Function.class);
        when(function.apply(EXCEPTION)).thenReturn(VALUE);
        completionStage.exceptionally(function).thenAccept(consumer);

        finishCalculation(completionStage);

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

        finishCalculation(completionStage);

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

        finishCalculation(completionStage);

        verify(errorHandler).apply(isA(CompletionException.class));
        verify(conversion).apply(VALUE);
    }

    @Test
    public void shouldCombineValues() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finishCalculation(completionStage2);

        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);
        Consumer<Integer> consumer = mock(Consumer.class);

        when(combiner.apply(VALUE, VALUE2)).thenReturn(5);

        completionStage1.thenCombine(completionStage2, combiner).thenAccept(consumer);
        finishCalculation(completionStage1);

        verify(combiner).apply(VALUE, VALUE2);
        verify(consumer).accept(5);
    }

    @Test
    public void shouldCombineValuesInOppositeOrder() {
        CompletionStage<String> completionStage1 = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finishCalculation(completionStage2);

        BiFunction<String, String, Integer> combiner = mock(BiFunction.class);
        Consumer<Integer> consumer = mock(Consumer.class);

        when(combiner.apply(VALUE2, VALUE)).thenReturn(5);

        completionStage2.thenCombine(completionStage1, combiner).thenAccept(consumer);
        finishCalculation(completionStage1);

        verify(combiner).apply(VALUE2, VALUE);
        verify(consumer).accept(5);
    }


    @Test
    public void exceptionFromThenAcceptShouldBePassedToTheNextPhase() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Consumer<String> consumer = mock(Consumer.class);
        Function<Throwable, Void> errorHandler = mock(Function.class);
        when(errorHandler.apply(EXCEPTION)).thenReturn(null);
        doThrow(EXCEPTION).when(consumer).accept(VALUE);
        completionStage.thenAccept(consumer).exceptionally(errorHandler);

        finishCalculation(completionStage);

        verify(errorHandler).apply(isA(CompletionException.class));
        verify(consumer).accept(VALUE);
    }

    @Test
    public void thenApplyShouldTransformTheValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        Consumer<Integer> consumer = mock(Consumer.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).thenAccept(consumer);

        finishCalculation(completionStage);

        verify(consumer).accept(8);
    }

    @Test
    public void shouldNotFailOnException() {
        CompletionStage<String> completionStage = createExceptionalCompletionStage(EXCEPTION);

        Consumer<Integer> consumer = mock(Consumer.class);
        Function<Throwable, Void> errorFunction = mock(Function.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).thenAccept(consumer).exceptionally(errorFunction);

        finishCalculation(completionStage);

        verifyZeroInteractions(consumer);
        verify(errorFunction, times(1)).apply(isA(CompletionException.class));
    }

    @Test
    public void whenCompleteShouldAcceptValue() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);

        BiConsumer<Integer, Throwable> consumer = mock(BiConsumer.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).whenComplete(consumer);

        finishCalculation(completionStage);

        verify(consumer).accept(8, null);
    }

    @Test
    public void whenCompleteShouldAcceptException() {
        CompletionStage<String> completionStage = createExceptionalCompletionStage(EXCEPTION);

        BiConsumer<Integer, Throwable> consumer = mock(BiConsumer.class);
        completionStage.thenApply(String::length).thenApply(i -> i * 2).whenComplete(consumer);

        finishCalculation(completionStage);

        verify(consumer).accept((Integer) isNull(), isA(CompletionException.class));
    }

    /**
     * Names thread
     */
    private class ThreadNamingExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            Thread thread = new Thread(command);
            thread.setName(IN_EXECUTOR_THREAD_NAME);
            thread.start();
        }
    }
}