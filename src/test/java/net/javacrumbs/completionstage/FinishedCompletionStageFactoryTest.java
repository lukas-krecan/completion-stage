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

import java.util.concurrent.CompletionStage;

public class FinishedCompletionStageFactoryTest extends AbstractCompletionStageTest {
    private final CompletionStageFactory factory = new CompletionStageFactory(defaultExecutor);

    @Override
    protected CompletionStage<String> createCompletionStage(String value) {
        return factory.completedStage(value);
    }

    @Override
    protected CompletionStage<String> createCompletionStage(Throwable e) {
        CompletableCompletionStage<String> completionStage = factory.createCompletionStage();
        completionStage.completeExceptionally(e);
        return completionStage;
    }

    @Override
    protected void finish(CompletionStage<String> completionStage) {

    }
}