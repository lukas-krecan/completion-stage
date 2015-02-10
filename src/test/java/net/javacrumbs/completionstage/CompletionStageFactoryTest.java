package net.javacrumbs.completionstage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CompletionStageFactoryTest {

    public static final RuntimeException TEST_EXCEPTION = new RuntimeException("Test exception");
    public static final String TEST_VALUE = "test";
    private CompletionStageFactory factory;

    @Mock
    private Executor defaultExecutor;

    @Mock
    private Executor alternativeExecutor;

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
        CompletionStage<String> stage = factory.completedStage(TEST_VALUE);

        CompletableFuture<String> future = stage.toCompletableFuture();
        assertTrue(future.isDone());
        assertEquals(TEST_VALUE, future.get());
    }

    private static void executeCapturedRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
        runnableCaptor.getValue().run();
    }

    private <U> void doSupplyAsyncTest(Executor executor, CompletionStage<U> stage, Object supplier, String expectedResult) throws Exception {
        CompletableFuture<U> future = doSupplyAsyncTestWithoutResultCheck(executor, stage, supplier);
        assertEquals(expectedResult, future.get());
    }

    private <U> CompletableFuture<U> doSupplyAsyncTestWithoutResultCheck(Executor executor, CompletionStage<U> stage, Object supplier) {
        CompletableFuture<U> future = stage.toCompletableFuture();

        // preconditions:
        assertFalse(future.isDone());
        verifyZeroInteractions(supplier);

        verify(executor).execute(runnableCaptor.capture());
        executeCapturedRunnable(runnableCaptor);

        assertTrue(future.isDone());
        return future;
    }

    @Test
    public void supplyAsyncTest() throws Exception {
        when(supplier.get()).thenReturn(TEST_VALUE);

        CompletionStage<String> stage = factory.supplyAsync(supplier);

        doSupplyAsyncTest(defaultExecutor, stage, supplier, TEST_VALUE);
    }

    @Test
    public void supplyAsyncWithExecutorTest() throws Exception {
        when(supplier.get()).thenReturn(TEST_VALUE);

        CompletionStage<String> stage = factory.supplyAsync(supplier, alternativeExecutor);
        doSupplyAsyncTest(alternativeExecutor, stage, supplier, TEST_VALUE);
    }

    @Test
    public void exceptionFromSupplierShouldBePropagated() throws Exception {
        CompletionStage<String> stage = factory.supplyAsync(supplier, alternativeExecutor);

        checkExceptionThrown(stage);
    }

    /**
     * Cross check if the CompletableFuture has the same behavior.
     *
     * @throws Exception
     */
    @Test
    public void exceptionFromSupplierShouldBePropagatedInCompletableFuture() throws Exception {
        checkExceptionThrown(CompletableFuture.supplyAsync(supplier, alternativeExecutor));
    }

    protected void checkExceptionThrown(CompletionStage<String> stage) {
        when(supplier.get()).thenThrow(TEST_EXCEPTION);

        AtomicReference<Throwable> exception = new AtomicReference<>();
        stage.exceptionally(e -> {
            exception.set(e);
            return null;
        });

        CompletableFuture<String> future = doSupplyAsyncTestWithoutResultCheck(alternativeExecutor, stage, supplier);


        try {
            future.get();
            fail("Exception expected");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ExecutionException.class);
            assertSame(TEST_EXCEPTION, e.getCause());
        }

        assertThat(exception.get()).isInstanceOf(CompletionException.class);
        assertSame(TEST_EXCEPTION, exception.get().getCause());
    }


    @Test
    public void runAsyncTest() throws Exception {
        CompletionStage<Void> stage = factory.runAsync(runnable);

        doSupplyAsyncTest(defaultExecutor, stage, runnable, null);
    }

    @Test
    public void runAsyncWithExecutorTest() throws Exception {
        Executor executor = mock(Executor.class);

        CompletionStage<Void> stage = factory.runAsync(runnable, executor);

        doSupplyAsyncTest(executor, stage, runnable, null);
    }
}
