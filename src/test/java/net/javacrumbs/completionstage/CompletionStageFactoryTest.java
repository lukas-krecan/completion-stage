package net.javacrumbs.completionstage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompletionStageFactoryTest {

    private CompletionStageFactory factory;

    @Mock
    private Executor defaultExecutor;

    @Mock
    private Supplier<String> supplier;

    @Mock
    private Runnable runnable;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    @Before
    public void before() {
        factory = new CompletionStageFactory(defaultExecutor);
    }

    @Test
    public void completedFutureTest() throws Exception {
        CompletionStage<String> stage = factory.completedFuture("test");

        CompletableFuture<String> future = stage.toCompletableFuture();
        assertTrue(future.isDone());
        assertEquals("test", future.get());
    }

    private static void executeCapturedRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
        runnableCaptor.getValue().run();
    }

    private void doSupplyAsyncTest(Executor executor, CompletionStage<String> stage, String expectedResult) throws Exception {
        CompletableFuture<String> future = stage.toCompletableFuture();

        // preconditions:
        assertFalse(future.isDone());
        verifyZeroInteractions(supplier);

        verify(executor).execute(runnableCaptor.capture());
        executeCapturedRunnable(runnableCaptor);
        verify(supplier).get();

        assertTrue(future.isDone());
        assertEquals(expectedResult, future.get());
    }

    @Test
    public void supplyAsyncTest() throws Exception {
        when(supplier.get()).thenReturn("test");

        CompletionStage<String> stage = factory.supplyAsync(supplier);

        doSupplyAsyncTest(defaultExecutor, stage, "test");
    }

    @Test
    public void supplyAsyncWithExecutorTest() throws Exception {
        when(supplier.get()).thenReturn("test");

        Executor executor = mock(Executor.class);
        CompletionStage<String> stage = factory.supplyAsync(supplier, executor);
        doSupplyAsyncTest(executor, stage, "test");
    }

    private void doRunAsyncTest(Executor executor, CompletionStage<Void> stage) throws Exception {
        CompletableFuture<Void> future = stage.toCompletableFuture();

        // preconditions:
        assertFalse(future.isDone());
        verifyZeroInteractions(runnable);

        verify(executor).execute(runnableCaptor.capture());
        executeCapturedRunnable(runnableCaptor);
        verify(runnable).run();

        assertTrue(future.isDone());
    }

    @Test
    public void runAsyncTest() throws Exception {
        CompletionStage<Void> stage = factory.runAsync(runnable);

        doRunAsyncTest(defaultExecutor, stage);
    }

    @Test
    public void runAsyncWithExecutorTest() throws Exception {
        Executor executor = mock(Executor.class);

        CompletionStage<Void> stage = factory.runAsync(runnable, executor);

        doRunAsyncTest(executor, stage);
    }
}
