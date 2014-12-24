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

import java.util.concurrent.Executor;

/**
 * Factory for {@link java.util.concurrent.CompletionStage} implementation.
 */
public class CompletionStageFactory {
    private final Executor defaultExecutor;

    /**
     * Creates factory.
     * @param defaultExecutor executor to be used for async methods without executor parameter
     */
    public CompletionStageFactory(Executor defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }

    /**
     * Creates completion stage.
     * @param <T> type of the CompletionStage
     * @return CompletionStage
     */
    public <T> CompletableCompletionStage<T> createCompletionStage() {
        return new SimpleCompletionStage<>(defaultExecutor);
    }
}
