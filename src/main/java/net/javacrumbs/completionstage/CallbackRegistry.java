/**
 * Copyright 2009-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.completionstage;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Registry for Consumer callbacks. Works as a state machine switching between Initial, Intermediate, Final(Success|Failure) state.
 * <p/>
 * <p>Inspired by {@code org.springframework.util.concurrent.ListenableFutureCallbackRegistry} and
 * {@code com.google.common.util.concurrent.ExecutionList}</p>
 */
final class CallbackRegistry<T> {
    private final AtomicReference<State<T>> state = new AtomicReference<>(InitialState.instance());

    /**
     * Adds the given callbacks to this registry.
     */
    void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
        Objects.requireNonNull(successCallback, "'successCallback' must not be null");
        Objects.requireNonNull(failureCallback, "'failureCallback' must not be null");
        Objects.requireNonNull(executor, "'executor' must not be null");

        // When we are adding callbacks to FinalState we are calling callbacks directly. 
        // This looks like a side-effect that should be avoided when using getAndUpdate
        // However this is never happened actually -- as long as FinalState is never
        // progressed to an any new state we are calling "update" function on
        // FinalState exactly once
        state.getAndUpdate(s -> s.addCallbacks(successCallback, failureCallback, executor));
    }

    /**
     * To be called to set the result value.
     *
     * @param result the result value
     * @return true if this result will be used (first result registered)
     */
    boolean success(T result) {
        final State<T> oldState = state.getAndUpdate(s -> s.success(result));
        // Here and below no sync is necessary
        // while we are _always_ in immutable FinalState
        if (oldState != state.get()) {
            oldState.notifier().onSuccess(result);
            return true;
        }
        return false;
    }

    /**
     * To be called to set the failure exception
     *
     * @param failure the exception
     * @return true if this result will be used (first result registered)
     */
    boolean failure(Throwable failure) {
        final State<T> oldState = state.getAndUpdate(s -> s.failure(failure));
        // Here and below no sync is necessary
        // while we are _always_ in immutable FinalState
        if (oldState != state.get()) {
            oldState.notifier().onFailure(failure);
            return true;
        }
        return false;
    }

    /**
     * State of the registry. All subclasses are meant to be used form a synchronized block and are NOT
     * thread safe on their own.
     */
    static abstract class State<S> {
        protected abstract State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor);

        protected State<S> success(S result) {
            return new SuccessState<>(result);
        }

        protected State<S> failure(Throwable failure) {
            return new FailureState<>(failure);
        }

        protected Notifier<S> notifier() {
            return NotifierImpl.empty();
        }
    }

    /**
     * Result is not known yet and no callbacks registered. Using shared instance so we do not allocate instance where
     * it may not be needed.
     */
    private static class InitialState<S> extends State<S> {
        private static final InitialState<Object> instance = new InitialState<>();

        @Override
        protected State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            return new IntermediateState<>(new NotifierImpl<>(successCallback, failureCallback, executor));
        }

        @SuppressWarnings("unchecked")
        static <T> State<T> instance() {
            return (State<T>) instance;
        }
    }

    /**
     * Result is not known yet.
     */
    private static class IntermediateState<S> extends State<S> {
        final private Notifier<S> notifier;

        IntermediateState(final Notifier<S> notifier) {
            this.notifier = notifier;
        }

        @Override
        protected State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            return new IntermediateState<>(NotifierMulticaster.add(notifier, new NotifierImpl<>(successCallback, failureCallback, executor)));
        }

        @Override
        protected Notifier<S> notifier() {
            return notifier;
        }
    }

    private static abstract class FinalState<S> extends State<S> {
        @Override
        protected final State<S> success(S result) {
            // Do not obtrude result
            return this;
        }

        @Override
        protected final State<S> failure(Throwable failure) {
            // Do not obtrude exception
            return this;
        }

        static <S> void callCallback(Consumer<S> callback, S value, Executor executor) {
            executor.execute(() -> callback.accept(value));
        }
    }

    /**
     * Holds the result.
     */
    private static final class SuccessState<S> extends FinalState<S> {
        private final S result;

        SuccessState(S result) {
            this.result = result;
        }

        @Override
        protected State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            callCallback(successCallback, result, executor);
            return this;
        }
    }

    /**
     * Holds the failure.
     */
    private static final class FailureState<S> extends FinalState<S> {
        private final Throwable failure;

        FailureState(Throwable failure) {
            this.failure = failure;
        }

        @Override
        protected State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            callCallback(failureCallback, failure, executor);
            return this;
        }
    }

    private interface Notifier<T> {
        void onSuccess(T result);
        void onFailure(Throwable failure);
    }

    /**
     * Container for success, fallback callbacks and executor.
     * @param <T>
     */
    private static final class NotifierImpl<T> implements Notifier<T> {
        private static final Notifier<Object> EMPTY = new Notifier<Object>() {
            public void onSuccess(Object result) {}
            public void onFailure(Throwable failure) {}
        };

        final private Consumer<? super T> successCallback;
        final private Consumer<Throwable> failureCallback;
        final private Executor executor;

        NotifierImpl(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            this.successCallback = successCallback;
            this.failureCallback = failureCallback;
            this.executor = executor;
        }

        public void onSuccess(T result) {
            execute(successCallback, result);
        }

        public void onFailure(Throwable failure) {
            execute(failureCallback, failure);
        }

        private <S> void execute(Consumer<? super S> consumer, S value) {
            executor.execute( () -> consumer.accept(value) );
        }

        @SuppressWarnings("unchecked")
        static <T> Notifier<T> empty() {
            return (Notifier<T>)EMPTY;
        }

    }

    // Modeled after AWTEventMulticaster
    private static final class NotifierMulticaster<T> implements Notifier<T> {
        final private Notifier<T> a;
        final private Notifier<T> b;
        NotifierMulticaster(Notifier<T> a, Notifier<T> b) {
            this.a = a;
            this.b = b;
        }

        public void onSuccess(T result) {
            a.onSuccess(result);
            b.onSuccess(result);
        }

        public void onFailure(Throwable failure) {
            a.onFailure(failure);
            b.onFailure(failure);
        }

        static <T> Notifier<T> add(Notifier<T> a, Notifier<T> b) {
            return  (a == null)  ? b :
                (b == null)  ? a : new NotifierMulticaster<>(a, b);
        }
    }

}