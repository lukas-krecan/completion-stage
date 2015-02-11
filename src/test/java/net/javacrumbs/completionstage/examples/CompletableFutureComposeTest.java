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

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureComposeTest {

    @Test
    public void completedAfter() {
        CompletableFuture<String> future1 = new CompletableFuture<>();
        CompletableFuture<String> future2 = new CompletableFuture<>();

        future1.thenCompose(x -> future2).whenComplete((r, e) -> System.out.println("After: " + e));

        future1.complete("value");
        future2.completeExceptionally(new RuntimeException());
    }

    @Test
    public void completedBefore() {
        CompletableFuture<String> future1 = new CompletableFuture<>();
        CompletableFuture<String> future2 = new CompletableFuture<>();

        future1.complete("value");
        future2.completeExceptionally(new RuntimeException());

        future1.thenCompose(x -> future2).whenComplete((r, e) -> System.out.println("Before: " +e));
    }
}
