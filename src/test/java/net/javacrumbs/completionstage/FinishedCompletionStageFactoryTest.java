package net.javacrumbs.completionstage;

import java.util.concurrent.CompletionStage;

public class FinishedCompletionStageFactoryTest extends AbstractCompletionStageTest {
    private final CompletionStageFactory factory = new CompletionStageFactory(defaultExecutor);

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        CompletableCompletionStage<String> completionStage = factory.createCompletionStage();
        completionStage.complete(value);
        return completionStage;
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        CompletableCompletionStage<String> completionStage = factory.createCompletionStage();
        completionStage.completeExceptionally(e);
        return completionStage;
    }

    @Override
    protected void finish(CompletionStage<String> completionStage) {

    }
}