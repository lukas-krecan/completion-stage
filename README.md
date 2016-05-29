CompletionStage alternative implementation 
==========================================

[![Build Status](https://travis-ci.org/lukas-krecan/completion-stage.png?branch=master)](https://travis-ci.org/lukas-krecan/completion-stage) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.completion-stage/completion-stage/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.completion-stage/completion-stage)

An alternative implementation to Java 8 [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html).
Its main focus is to simplify support of [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) in
other libraries.

The objective is to provide a simple, easy to understand alternative. You can check the
[code](https://github.com/lukas-krecan/completion-stage/blob/master/src/main/java/net/javacrumbs/completionstage/SimpleCompletionStage.java)
and see for yourself.

# How to use

Add Maven dependency

    <dependency>
        <groupId>net.javacrumbs.completion-stage</groupId>
        <artifactId>completion-stage</artifactId>
        <version>0.0.9</version>
    </dependency>

And enjoy

    private final CompletionStageFactory factory =
                                        new CompletionStageFactory(defaultAsyncExecutor);

    ...

    CompletableCompletionStage<Object> completionStage = factory.createCompletionStage();

    ...

    // once the result is ready, let us know
    completionStage.complete(value);

    ...
    // in case of exception
    completionStage.completeExceptionally(exception);

    ...
    // create already completed stage
    CompletionStage<String> completed = factory.completedStage(value);

    ...
    // asynchronously execute supplier
    CompletionStage<String> asyncStage = factory.supplyAsync(supplier);


For example, to convert Spring [ListenableFuture](http://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/concurrent/ListenableFuture.html)
to CompletionStage you can

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

# Design
The best way to understand how it works is to check the [code](https://github.com/lukas-krecan/completion-stage/blob/master/src/main/java/net/javacrumbs/completionstage/SimpleCompletionStage.java).
I have written two articles describing design decisions behind the implementation you can read it [here](http://java.dzone.com/articles/implementing-java-8) and [here](http://java.dzone.com/articles/implementing-java-8-0).

# Why can't I just use a CompletableFuture?

You definitely can. The main problem I have that it is tightly coupled with fork-join framework.
And fork-join framework is meant to be used mainly for CPU intensive tasks. But my usual use-case
is the opposite, I want to use asynchronous processing mainly for blocking tasks. I do not want to
block a thread while waiting for a network or database operation.

Of course, you do not have to use fork-join executors with CompletableFutures. You just have to be careful since it is
the default choice for async methods.

# Release notes

### 0.0.9
* Fine grained locks

### 0.0.8
* SimpleCompletionStage made extensible

### 0.0.7
* Incorrect release - do not use

### 0.0.6
* Added CompletableCompletionStage.doCompleteMethods

### 0.0.5
* Added factory methods to CompletionStageFactory

### 0.0.4
* Small optimizations

### 0.0.3
* thenComposeAsync error handling fixed
* thenCombineAsync uses correct executor
* whenCompleteAsync passes value to next stage
* Internal refactoring

### 0.0.2
* Fixed error handling in thenCombine function



