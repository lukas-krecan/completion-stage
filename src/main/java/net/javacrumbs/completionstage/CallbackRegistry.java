/**
 * Copyright 2009-2014 the original author or authors.
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

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Registry for Consumer callbacks. Works as a state machine switching between New and Success and Failure state.
 * <p/>
 * <p>Inspired by {@code org.springframework.util.concurrent.ListenableFutureCallbackRegistry} and
 * {@code com.google.common.util.concurrent.ExecutionList}</p>
 */
final class CallbackRegistry<T> {
    private State state = new NewState();

    private final Object mutex = new Object();

    /**
     * Adds the given callbacks to this registry.
     */
    public void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
        Objects.requireNonNull(successCallback, "'successCallback' must not be null");
        Objects.requireNonNull(failureCallback, "'failureCallback' must not be null");
        Objects.requireNonNull(executor, "'executor' must not be null");

        synchronized (mutex) {
            state.addCallbacks(successCallback, failureCallback, executor);
        }
    }

    /**
     * To be called to set the result value.
     *
     * @param result the result value
     * @return true if this result will be used (first result registered)
     */
    public boolean success(T result) {
        synchronized (mutex) {
            if (state.isCompleted()) {
                return false;
            }

            state = state.success(result);
            return true;
        }
    }

    /**
     * To be called to set the failure exception
     *
     * @param failure the exception
     * @return true if this result will be used (first result registered)
     */
    public boolean failure(Throwable failure) {
        synchronized (mutex) {
            if (state.isCompleted()) {
                return false;
            }

            state = state.failure(failure);
            return true;
        }
    }

    private static <S> void callCallback(Consumer<S> callback, S value, Executor executor) {
        executor.execute(() -> callback.accept(value));
    }

    private static <S> void callCallback(CallbackExecutorPair<S> callbackExecutorPair, S result) {
        callCallback(callbackExecutorPair.getCallback(), result, callbackExecutorPair.getExecutor());
    }

    /**
     * State of the registry.
     */
    private abstract class State {
        protected abstract void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor);

        protected State success(T result) {
            throw new IllegalStateException("success method should not be called multiple times");
        }

        protected State failure(Throwable failure) {
            throw new IllegalStateException("failure method should not be called multiple times");
        }

        protected boolean isCompleted() {
            return true;
        }
    }

    /**
     * Result is not known yet.
     */
    private class NewState extends State {
        private final Queue<CallbackExecutorPair<? super T>> successCallbacks = new LinkedList<>();
        private final Queue<CallbackExecutorPair<Throwable>> failureCallbacks = new LinkedList<>();

        @Override
        protected void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            successCallbacks.add(new CallbackExecutorPair<>(successCallback, executor));
            failureCallbacks.add(new CallbackExecutorPair<>(failureCallback, executor));
        }

        @Override
        protected State success(T result) {
            while (!successCallbacks.isEmpty()) {
                callCallback(successCallbacks.poll(), result);
            }
            return new SuccessState(result);
        }

        @Override
        protected State failure(Throwable failure) {
            while (!failureCallbacks.isEmpty()) {
                callCallback(failureCallbacks.poll(), failure);
            }
            return new FailureState(failure);
        }

        @Override
        protected boolean isCompleted() {
            return false;
        }
    }

    /**
     * Keeps the result.
     */
    private final class SuccessState extends State {
        private final T result;

        private SuccessState(T result) {
            this.result = result;
        }

        @Override
        protected void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            callCallback(successCallback, result, executor);
        }
    }

    /**
     * Keeps the failure.
     */
    private final class FailureState extends State {
        private final Throwable failure;

        private FailureState(Throwable failure) {
            this.failure = failure;
        }

        @Override
        protected void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
            callCallback(failureCallback, failure, executor);
        }
    }


    private static final class CallbackExecutorPair<S> {
        private final Consumer<S> callback;
        private final Executor executor;

        private CallbackExecutorPair(Consumer<S> callback, Executor executor) {
            this.callback = callback;
            this.executor = executor;
        }

        public Consumer<S> getCallback() {
            return callback;
        }

        public Executor getExecutor() {
            return executor;
        }
    }

}
