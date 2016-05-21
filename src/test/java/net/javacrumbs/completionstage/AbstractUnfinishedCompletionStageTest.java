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

import org.junit.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public abstract class AbstractUnfinishedCompletionStageTest extends AbstractCompletionStageTest {

    protected boolean finished() {
        return false;
    }

    @Test
    public void acceptEitherTakesOtherValueIfTheFirstIsNotReady() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = mock(Consumer.class);
        completionStage.acceptEither(completionStage2, consumer);

        verify(consumer, times(1)).accept(VALUE2);
        finish(completionStage);

        verify(consumer, times(1)).accept(any(String.class));
    }

    @Test
    public void acceptEitherPropagatesExceptionFromSecondCompletable() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(EXCEPTION);
        finish(completionStage2);

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Function<Throwable, Void> errorHandler = mock(Function.class);
        completionStage.acceptEither(completionStage2, consumer).exceptionally(errorHandler);

        verify(errorHandler, times(1)).apply(any(CompletionException.class));
        finish(completionStage);

        verifyZeroInteractions(consumer);
    }

    @Test
    public void acceptEitherPropagatesExceptionFromFirstCompletable() {
        CompletionStage<String> completionStage = createCompletionStage(EXCEPTION);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage);

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Function<Throwable, Void> errorHandler = mock(Function.class);
        completionStage.acceptEither(completionStage2, consumer).exceptionally(errorHandler);

        verify(errorHandler, times(1)).apply(any(CompletionException.class));
        finish(completionStage2);

        verifyZeroInteractions(consumer);
    }

    @Test
    public void acceptEitherPropagatesExceptionThrownByConsumerWhenProcessingFirsStage() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage);

        Consumer<String> consumer = s -> {
            throw EXCEPTION;
        };

        @SuppressWarnings("unchecked")
        Function<Throwable, Void> errorHandler = mock(Function.class);
        completionStage.acceptEither(completionStage2, consumer).exceptionally(errorHandler);

        verify(errorHandler, times(1)).apply(any(CompletionException.class));
        finish(completionStage2);
    }

    @Test
    public void acceptEitherPropagatesExceptionThrownByConsumerWhenProcessingSecondStage() {
        CompletionStage<String> completionStage = createCompletionStage(VALUE);
        CompletionStage<String> completionStage2 = createCompletionStage(VALUE2);
        finish(completionStage2);

        Consumer<String> consumer = s -> {
            throw EXCEPTION;
        };

        @SuppressWarnings("unchecked")
        Function<Throwable, Void> errorHandler = mock(Function.class);
        completionStage.acceptEither(completionStage2, consumer).exceptionally(errorHandler);

        verify(errorHandler, times(1)).apply(any(CompletionException.class));
        finish(completionStage);
    }
}
