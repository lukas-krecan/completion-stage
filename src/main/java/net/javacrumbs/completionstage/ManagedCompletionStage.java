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
            // to get access to PersistenceUnit, current principal / other security services, etc.
            // We must first capture context on the thread we are ending up (current stage thread) 
            // and only afterwards submit wrapped callback to actual executor
            
            // Btw, if we would wrap callbacks right away, like
            // super.addCallbacks(createProxy(successCallback), createProxy(failureCallback), executor);
            // then we effectively capture context of the method that created completion chain (like
            // stage.thenApplyAsync(...).thenApplyAsync(...).thenApplyAsync(...)) but not the context 
            // of the current stage! This would lead to incorrectly captured contex, for example, 
            // @RunAs annotated methods will get wrong security context
            super.addCallbacks(
                    v -> {
                        Consumer<? super T> successCallbackWithCtx = createProxy(successCallback);
                        executor.execute(() -> successCallbackWithCtx.accept(v)); 
                    }, 
                    t -> {
                        Consumer<Throwable> failureCallbackWithCtx = createProxy(failureCallback);
                        executor.execute(() -> failureCallbackWithCtx.accept(t));
                    }, 
                    SAME_THREAD_EXECUTOR
            );
        } else {
            super.addCallbacks(successCallback, failureCallback, executor);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <U> Consumer<U> createProxy(final Consumer< U> c) {
        return contextService.createContextualProxy(c, Consumer.class);
    }
}
