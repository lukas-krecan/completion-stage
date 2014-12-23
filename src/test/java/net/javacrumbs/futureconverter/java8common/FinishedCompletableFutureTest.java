package net.javacrumbs.futureconverter.java8common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tests CompletableFuture. Just to be sure I am reading the spec correctly. Same tests are executed on
 * CompletionStage and CompletableFuture.
 */
public class FinishedCompletableFutureTest extends AbstractCompletionStageTest {

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        completableFuture.complete(value);
        return completableFuture;
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(e);
        return completableFuture;
    }

    @Override
    protected void finishCalculation(CompletionStage<String> completionStage) {

    }
}