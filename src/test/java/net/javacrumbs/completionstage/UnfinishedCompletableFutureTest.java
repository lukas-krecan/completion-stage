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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Tests CompletableFuture. Just to be sure I am reading the spec correctly. Same tests are executed on
 * CompletionStage and CompletableFuture.
 */
public class UnfinishedCompletableFutureTest extends AbstractUnfinishedCompletionStageTest {

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return new DelayedCompletableFuture<>(c -> c.complete(value));
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        return new DelayedCompletableFuture<>(c -> c.completeExceptionally(e));
    }


    @Override
    protected void finish(CompletionStage<String> c) {
        ((DelayedCompletableFuture<String>) c).executeDelayedAction();
    }

    private static class DelayedCompletableFuture<T> extends CompletableFuture<T> {
        private final Consumer<CompletableFuture<T>> delayedAction;

        private DelayedCompletableFuture(Consumer<CompletableFuture<T>> delayedAction) {
            this.delayedAction = delayedAction;
        }

        private void executeDelayedAction() {
            delayedAction.accept(this);
        }
    }
}