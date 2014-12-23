package net.javacrumbs.completionstage;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class UnfinishedCompletionStageFactoryTest extends AbstractUnfinishedCompletionStageTest {

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return new DelayedSimpleCompletionStage(c -> c.complete(value), defaultExecutor);
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        return new DelayedSimpleCompletionStage(c -> c.completeExceptionally(e), defaultExecutor);
    }

    @Override
    protected void finish(CompletionStage<String> completionStage) {
        ((DelayedSimpleCompletionStage)completionStage).executeDelayedAction();
    }

    private static class DelayedSimpleCompletionStage extends SimpleCompletionStage<String> {
        private final Consumer<DelayedSimpleCompletionStage> delayedAction;

        private DelayedSimpleCompletionStage(Consumer<DelayedSimpleCompletionStage> delayedAction, Executor defaultExecutor) {
            super(defaultExecutor);
            this.delayedAction = delayedAction;
        }

        private void executeDelayedAction() {
            delayedAction.accept(this);
        }
    }
}