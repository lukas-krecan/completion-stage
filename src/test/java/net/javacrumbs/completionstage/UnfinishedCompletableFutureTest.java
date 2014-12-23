package net.javacrumbs.completionstage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Tests CompletableFuture. Just to be sure I am reading the spec correctly. Same tests are executed on
 * CompletionStage and CompletableFuture.
 */
public class UnfinishedCompletableFutureTest extends AbstractUnfinishedCompletionStageTest {

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return new DelayedCompletableFuture<>(c -> c.complete(value));
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        return new DelayedCompletableFuture<>(c -> c.completeExceptionally(e));
    }


    @Override
    protected void finish(CompletionStage c) {
        ((DelayedCompletableFuture) c).executeDelayedAction();
    }

    private static class DelayedCompletableFuture<T> extends CompletableFuture<T> {
        private final Consumer<CompletableFuture<T>> delayedAction;

        private DelayedCompletableFuture(Consumer<CompletableFuture<T>> delayedAction) {
            this.delayedAction = delayedAction;
        }

        private void executeDelayedAction() {
            delayedAction.accept(this);
        }
    }
}