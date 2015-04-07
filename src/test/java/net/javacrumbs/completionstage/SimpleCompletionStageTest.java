package net.javacrumbs.completionstage;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SimpleCompletionStageTest {

    private static final String VALUE = "value";
    private static final RuntimeException EXCEPTION = new RuntimeException("test");

    private final CompletionStageFactory factory = new CompletionStageFactory(Runnable::run);

    @SuppressWarnings("unchecked")
    private final BiConsumer<? super String, ? super Throwable> action = mock(BiConsumer.class);
    private final CompletableCompletionStage<String> stage;

    public SimpleCompletionStageTest() {
        stage = factory.createCompletionStage();
        stage.whenComplete(action);
    }

    @Test
    public void shouldCompleteUsingMethodReference() {
        CompletableFuture<String> future = CompletableFuture.completedFuture(VALUE);
        future.whenComplete(stage::doComplete);

        verify(action).accept(VALUE, null);
    }

    @Test
    public void shouldCompleteExceptionallyUsingMethodReference() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(EXCEPTION);
        future.whenComplete(stage::doComplete);
        verify(action).accept(null, EXCEPTION);
    }

    @Test
    public void shouldHandleUsingMethodReference() {
        CompletableFuture<String> future = CompletableFuture.completedFuture(VALUE);
        future.thenAccept(stage::doComplete);
        verify(action).accept(VALUE, null);
    }
}