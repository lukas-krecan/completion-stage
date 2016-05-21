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

import net.javacrumbs.completionstage.spi.CompletableCompletionStageFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class UnfinishedCompletionStageFactoryTest extends AbstractUnfinishedCompletionStageTest {

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return new DelayedSimpleCompletionStage(c -> c.complete(value), defaultExecutor);
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        return new DelayedSimpleCompletionStage(c -> c.completeExceptionally(e), defaultExecutor);
    }

    @Override
    protected void finish(CompletionStage<String> completionStage) {
        ((DelayedSimpleCompletionStage)completionStage).executeDelayedAction();
    }

    private static class DelayedSimpleCompletionStage extends SimpleCompletionStage<String> {
        private final Consumer<DelayedSimpleCompletionStage> delayedAction;

        private DelayedSimpleCompletionStage(Consumer<DelayedSimpleCompletionStage> delayedAction, Executor defaultExecutor) {
            super(defaultExecutor, new CompletionStageFactory(defaultExecutor));
            this.delayedAction = delayedAction;
        }

        private void executeDelayedAction() {
            delayedAction.accept(this);
        }
    }
}