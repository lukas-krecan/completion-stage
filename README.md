CompletionStage alternative implementation
==========================================

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
        <version>0.0.6</version>
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


# Disclaimer

It's quite fresh code as you can tell from the version number. So the API may change and there may be some bugs. That being said, it
has quite good code coverage so I do not expect any obvious bugs. If you find one, please let me know.

For converting between different kinds of Java futures, please check [future-converter](https://github.com/lukas-krecan/future-converter)
it's bit more mature.

# Why can't I just use a CompletableFuture?

You definitely can. The main problem I have that it is tightly coupled with fork-join framework.
And fork-join framework is meant to be used mainly for CPU intensive tasks. But my usual use-case
is the opposite, I want to use asynchronous processing mainly for blocking tasks. I do not want to
block a thread while waiting for a network or database operation.

Of course, you do not have to use fork-join executors with CompletableFutures. You just have to be careful since it is
the default choice for async methods.



