package net.javacrumbs.futureconverter.java8common;

import java.util.concurrent.CompletionStage;

public class FinishedCompletionStageFactoryTest extends AbstractCompletionStageTest {
    private final CompletionStageFactory factory = new CompletionStageFactory();

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return factory.createCompletableFuture((onSuccess, onFailure) -> {
            onSuccess.accept(value);
        });
    }

    @Override
    protected CompletionStage<String> createExceptionalCompletionStage(Throwable e) {
        return factory.createCompletableFuture((onSuccess, onFailure) -> onFailure.accept(e));
    }

    @Override
    protected void finishCalculation(CompletionStage<String> completionStage) {

    }
}