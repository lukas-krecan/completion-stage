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
package net.javacrumbs.completionstage.load;

import net.javacrumbs.completionstage.CompletableCompletionStage;
import net.javacrumbs.completionstage.CompletionStageFactory;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Really naive load test, JMH anyone?
 */
public class LoadTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(50);
    private final CompletionStageFactory factory = new CompletionStageFactory(Runnable::run);
    private final Random random = new Random();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final AtomicInteger processed = new AtomicInteger();

    @Test
    @Ignore
    public void testLoad() throws InterruptedException, ExecutionException {
        for (int i = 0; i < 50_000_000; i++) {
            //testWithValueSetAtTheBeginning();
            testWithValueSetAtTheEnd();
        }
        assertThat(errors).isEmpty();
        System.out.println(processed.get());
    }

    private void testWithValueSetAtTheEnd() throws ExecutionException, InterruptedException {
        int value = getValue();
        CompletionStage<Integer> start = factory.createCompletionStage();
        Future<Void> future = createStages(value, start).toCompletableFuture();
        ((CompletableCompletionStage<Integer>)start).complete(value);
        future.get();
    }

    private void testWithValueSetAtTheBeginning() throws ExecutionException, InterruptedException {
        int value = getValue();
        CompletionStage<Integer> start = completedStage(value);
        Future<Void> future = createStages(value, start).toCompletableFuture();
        future.get();
    }

    private int getValue() {
        return random.nextInt(Integer.MAX_VALUE / 3);
    }

    private CompletionStage<Void> createStages(int value, CompletionStage<Integer> start) {
        return start
            .thenApplyAsync(i -> i * 2)
            .thenCombine(completedStage(50), (a, b) -> a + b)
            .thenApply(i -> i / 2)
            .thenApply(i -> i - value)
            .thenAccept(i -> {
                processed.incrementAndGet();
                if (i != 25) {
                    errors.add("Error from " + value + " result " + i);
                }
            });
    }

    private <T> CompletionStage<T> completedStage(T value) {
        return factory.completedStage(value);
    }
}
