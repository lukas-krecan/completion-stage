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
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
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

    @Mock
    private Consumer<String> consumer;

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

        // subsequent async method should use default executor again.
        stage.thenAcceptAsync(consumer);
        verify(defaultExecutor).execute(any(Runnable.class));
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
