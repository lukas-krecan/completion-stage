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
package net.javacrumbs.completionstage.completecompleted;

import net.javacrumbs.completionstage.CompletableCompletionStage;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public abstract class AbstractCanYouCompleteAlreadyCompletedTest {

    protected static final String VALUE = "value";
    protected static final String VALUE2 = "value2";
    protected static final RuntimeException EXCEPTION = new RuntimeException();

    protected abstract CompletableCompletionStage<String> createCompletionStage();

    @Test
    public void attemptToCompleteAlreadyCompletedShouldSilentlyIgnoreTheOtherValue() throws ExecutionException, InterruptedException {
        CompletableCompletionStage<String> completable = createCompletionStage();
        assertTrue(completable.complete(VALUE));
        assertFalse(completable.complete(VALUE2));

        BiFunction<String, Throwable, ?> handler = createMockHandler();
        completable.handle(handler);
        verify(handler, times(1)).apply(VALUE, null);
    }

    @Test
    public void attemptToCompleteExceptionallyAlreadyCompletedShouldSilentlyIgnoreTheException() throws ExecutionException, InterruptedException {
        CompletableCompletionStage<String> completable = createCompletionStage();
        assertTrue(completable.complete(VALUE));
        assertFalse(completable.completeExceptionally(EXCEPTION));

        BiFunction<String, Throwable, ?> handler = createMockHandler();
        completable.handle(handler);
        verify(handler, times(1)).apply(VALUE, null);
    }

    @Test
    public void attemptToCompleteAlreadyFailedShouldSilentlyIgnoreTheValue() throws ExecutionException, InterruptedException {
        CompletableCompletionStage<String> completable = createCompletionStage();
        assertTrue(completable.completeExceptionally(EXCEPTION));
        assertFalse(completable.complete(VALUE));


        BiFunction<String, Throwable, ?> handler = createMockHandler();
        completable.handle(handler);
        verify(handler, times(1)).apply(null, EXCEPTION);
    }

    @SuppressWarnings("unchecked")
    private BiFunction<String, Throwable, ?> createMockHandler() {
        return mock(BiFunction.class);
    }

}
