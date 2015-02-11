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
package net.javacrumbs.completionstage.examples;

import net.javacrumbs.completionstage.CompletableCompletionStage;
import net.javacrumbs.completionstage.CompletionStageFactory;
import org.junit.Test;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

public class SpringListenableFutureTest {
    private final AsyncListenableTaskExecutor executor = new TaskExecutorAdapter(Runnable::run);
    private final CompletionStageFactory factory = new CompletionStageFactory(Runnable::run);

    @Test
    public void testTransformationFromSpring() {
        ListenableFuture<String> springListenableFuture = createSpringListenableFuture();

        CompletableCompletionStage<Object> completionStage = factory.createCompletionStage();
        springListenableFuture.addCallback(new ListenableFutureCallback<String>() {
            @Override
            public void onSuccess(String result) {
                completionStage.complete(result);
            }

            @Override
            public void onFailure(Throwable t) {
                completionStage.completeExceptionally(t);
            }
        });

        completionStage.thenAccept(System.out::println);
    }

    public ListenableFuture<String> createSpringListenableFuture() {
        return executor.submitListenable(() -> "testValue");
    }
}
