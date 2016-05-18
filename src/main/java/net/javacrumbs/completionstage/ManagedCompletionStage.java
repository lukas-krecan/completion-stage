package net.javacrumbs.completionstage;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.enterprise.concurrent.ContextService;
import javax.enterprise.concurrent.ManagedExecutorService;

public class ManagedCompletionStage<T> extends SimpleCompletionStage<T> {
    private final ContextService contextService;
    
    public ManagedCompletionStage(Executor executor, ContextService contextService) {
        this(executor, contextService, () -> new ManagedCompletionStage<>(executor, contextService));
    }
    
    public ManagedCompletionStage(Executor executor, ContextService contextService, Supplier<? extends CompletableCompletionStage<?>> completionStageFactory) {
        super(executor, completionStageFactory);
        this.contextService = contextService;
    }
    
    @Override
    protected void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
        if (executor != SAME_THREAD_EXECUTOR && (executor instanceof ManagedExecutorService)) {
            // When stage is executed with ManagedExecutorService we must use contextual proxies
            // to get access to PersistenceUnit, current principal and other security services, etc
            successCallback = createProxy(successCallback);
            failureCallback = createProxy(failureCallback);
        }
        super.addCallbacks(successCallback, failureCallback, executor);
    }
    
    @SuppressWarnings("unchecked")
    private <U> Consumer<U> createProxy(final Consumer< U> c) {
        return contextService.createContextualProxy(c, Consumer.class);
    }
}
