package net.javacrumbs.completionstage;

import java.util.concurrent.CompletionStage;

public class FinishedCompletionStageFactoryTest extends AbstractCompletionStageTest {
    private final CompletionStageFactory factory = new CompletionStageFactory(defaultExecutor);

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return factory.createCompletionStage((onSuccess, onFailure) -> onSuccess.accept(value));
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        return factory.createCompletionStage((onSuccess, onFailure) -> onFailure.accept(e));
    }

    @Override
    protected void finish(CompletionStage<String> completionStage) {

    }
}