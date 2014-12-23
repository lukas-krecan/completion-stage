package net.javacrumbs.futureconverter.java8common;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class UnfinishedCompletionStageFactoryTest extends AbstractUnfinishedCompletionStageTest {

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return new DelayedSimpleCompletionStage(c -> c.success(value));
    }

    @Override
    protected CompletionStage<String> createExceptionalCompletionStage(Throwable e) {
        return new DelayedSimpleCompletionStage(c -> c.failure(e));
    }

    @Override
    protected void finishCalculation(CompletionStage<String> completionStage) {
        ((DelayedSimpleCompletionStage)completionStage).executeDelayedAction();
    }

    private final class DelayedSimpleCompletionStage extends SimpleCompletionStage<String> {
        private final Consumer<DelayedSimpleCompletionStage> delayedAction;

        private DelayedSimpleCompletionStage(Consumer<DelayedSimpleCompletionStage> delayedAction) {
            this.delayedAction = delayedAction;
        }

        private void executeDelayedAction() {
            delayedAction.accept(this);
        }
    }
}