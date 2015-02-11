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
package net.javacrumbs.completionstage.load;

import net.javacrumbs.completionstage.CompletableCompletionStage;
import net.javacrumbs.completionstage.CompletionStageFactory;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class LoadTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(50);
    private final CompletionStageFactory factory = new CompletionStageFactory(executor);
    private final Random random = new Random();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final AtomicInteger processed = new AtomicInteger();

    @Test
    @Ignore
    public void testLoad() throws InterruptedException {
        for (int i = 0; i < 100_000_000; i++) {
            this.doTest();
        }
        assertThat(errors).isEmpty();
        System.out.println(processed.get());
    }

    public void doTest() {
        int value = random.nextInt(Integer.MAX_VALUE / 3);
        delayedCompletionStage(value)
                .applyToEither(delayedCompletionStage(value), i -> i * 2)
                .thenCombine(delayedCompletionStage(50), (a, b) -> a + b)
                .thenApply(i -> i / 2)
                .thenApply(i -> i - value)
                .thenAccept(i -> {
                    processed.incrementAndGet();
                    if (i != 25) {
                        errors.add("Error from " + value + " result " + i);
                    }
                });
    }

    private <T> CompletionStage<T> delayedCompletionStage(T value) {
        CompletableCompletionStage<T> completionStage = factory.createCompletionStage();
        //executor.execute(() -> completionStage.complete(value));
        completionStage.complete(value);
        return completionStage;
    }
}
