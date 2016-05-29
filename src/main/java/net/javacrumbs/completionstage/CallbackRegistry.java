/**
 * Copyright 2009-2015 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.completionstage;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Registry for Consumer callbacks. Works as a state machine switching between InitialState, IntermediateState, Success and Failure state.
 * <p/>
 * <p>Inspired by {@code org.springframework.util.concurrent.ListenableFutureCallbackRegistry} and
 * {@code com.google.common.util.concurrent.ExecutionList}</p>
 * <p>
 * Explicit synchronization only around blocks responsible for state switching.
 */
final class CallbackRegistry<T> {
    private State<T> state = InitialState.instance();

    private final Object mutex = new Object();

    /**
     * Adds the given callbacks to this registry.
     */
    void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
        Objects.requireNonNull(successCallback, "'successCallback' must not be null");
        Objects.requireNonNull(failureCallback, "'failureCallback' must not be null");
        Objects.requireNonNull(executor, "'executor' must not be null");

        synchronized (mutex) {
            state = state.addCallbacks(successCallback, failureCallback, executor);
        }
    }

    /**
     * To be called to set the result value.
     *
     * @param result the result value
     * @return true if this result will be used (first result registered)
     */
    boolean success(T result) {
        State<T> oldState;
        synchronized (mutex) {
            if (state.isCompleted()) {
                return false;
            }
            oldState = state;
            state = state.getSuccessState(result);
        }
        oldState.callSuccessCallbacks(result);
        return true;

    }

    /**
     * To be called to set the failure exception
     *
     * @param failure the exception
     * @return true if this result will be used (first result registered)
     */
    boolean failure(Throwable failure) {
        State<T> oldState;
        synchronized (mutex) {
            if (state.isCompleted()) {
                return false;
            }
            oldState = state;
            state = state.getFailureState(failure);
        }
        oldState.callFailureCallbacks(failure);
        return true;
    }

    /**
     * State of the registry. All subclasses are meant to be used form a synchronized block and are NOT
     * thread safe on their own.
     */
    private static abstract class State<S> {
        protected abstract State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor);

        protected State<S> getSuccessState(S result) {
            throw new IllegalStateException("success method should not be called multiple times");
        }

        protected void callSuccessCallbacks(S result) {
        }

        protected State<S> getFailureState(Throwable failure) {
            throw new IllegalStateException("failure method should not be called multiple times");
        }

        protected void callFailureCallbacks(Throwable failure) {
        }

        protected boolean isCompleted() {
            return true;
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
            IntermediateState<S> intermediateState = new IntermediateState<>();
            intermediateState.addCallbacks(successCallback, failureCallback, executor);
            return intermediateState;
        }

        @Override
        protected State<S> getSuccessState(S result) {
            return new SuccessState<>(result);
        }

        @Override
        protected State<S> getFailureState(Throwable failure) {
            return new FailureState<>(failure);
        }

        @Override
        protected boolean isCompleted() {
            return false;
        }

        @SuppressWarnings("unchecked")
        private static <T> State<T> instance() {
            return (State<T>) instance;
        }
    }

    /**
     * Result is not known yet.
     */
    private static class IntermediateState<S> extends State<S> {
        private final Queue<CallbackHolder<? super S>> callbacks = new LinkedList<>();

        @Override
        protected State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            callbacks.add(new CallbackHolder<>(successCallback, failureCallback, executor));
            return this;
        }

        @Override
        protected State<S> getSuccessState(S result) {
            return new SuccessState<>(result);
        }

        @Override
        protected void callSuccessCallbacks(S result) {
            // no need to remove callbacks from the queue, this instance will be thrown away at once
            for (CallbackHolder<? super S> callback : callbacks) {
                callback.callSuccessCallback(result);
            }
        }

        @Override
        protected State<S> getFailureState(Throwable failure) {
            return new FailureState<>(failure);
        }

        @Override
        protected void callFailureCallbacks(Throwable failure) {
            // no need to remove callbacks from the queue, this instance will be thrown away at once
            for (CallbackHolder<? super S> callback : callbacks) {
                callback.callFailureCallback(failure);
            }
        }

        @Override
        protected boolean isCompleted() {
            return false;
        }
    }

    /**
     * Holds the result.
     */
    private static final class SuccessState<S> extends State<S> {
        private final S result;

        private SuccessState(S result) {
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
    private static final class FailureState<S> extends State<S> {
        private final Throwable failure;

        private FailureState(Throwable failure) {
            this.failure = failure;
        }

        @Override
        protected State<S> addCallbacks(Consumer<? super S> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            callCallback(failureCallback, failure, executor);
            return this;
        }
    }


    private static final class CallbackHolder<S> {
        private final Consumer<S> successCallback;
        private final Consumer<Throwable> failureCallback;
        private final Executor executor;

        private CallbackHolder(Consumer<S> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            this.successCallback = successCallback;
            this.failureCallback = failureCallback;
            this.executor = executor;
        }

        void callSuccessCallback(S result) {
            callCallback(successCallback, result, executor);
        }

        void callFailureCallback(Throwable failure) {
            callCallback(failureCallback, failure, executor);
        }
    }

    private static <T> void callCallback(Consumer<T> callback, T value, Executor executor) {
        executor.execute(() -> callback.accept(value));
    }

}
