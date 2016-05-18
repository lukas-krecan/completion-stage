package net.javacrumbs.completionstage;

import javax.enterprise.concurrent.ContextService;
import javax.enterprise.concurrent.ManagedExecutorService;

/**
 * Factory for managed variant of {@link java.util.concurrent.CompletionStage} implementation.
 * This factory assumes that by default async code will be executed with {@link javax.enterprise.concurrent.ManagedExecutorService}
 * hence necessary proxies are created with {@link javax.enterprise.concurrent.ContextService}
 */
public class ManagedCompletionStageFactory extends CompletionStageFactory {

    private final ContextService contextService;
    
    public ManagedCompletionStageFactory(ManagedExecutorService defaultAsyncExecutor, ContextService contextService) {
        super(defaultAsyncExecutor);
        this.contextService = contextService;
    }
    
    /**
     * Creates managed completion stage.
     * @param <T> type of the CompletionStage
     * @return CompletionStage
     */
    public <T> CompletableCompletionStage<T> createCompletionStage() {
        return new ManagedCompletionStage<>(defaultAsyncExecutor, contextService, this::createCompletionStage);
    }
}
