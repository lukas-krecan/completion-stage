CompletionStage alternative implementation
==========================================

An alternative implementation to Java 8 [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html).
Its main focus is to simplify support of [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) in
other libraries.

# How to use

Add Maven dependency

    <dependency>
        <groupId>net.javacrumbs.completion-stage</groupId>
        <artifactId>completion-stage</artifactId>
        <version>0.0.2</version>
    </dependency>

And enjoy

    private final CompletionStageFactory factory =
                                        new CompletionStageFactory(defaultAsyncExecutor);

    ...

    CompletableCompletionStage<Object> completionStage = factory.createCompletionStage();

    ...

    // once the result is ready, let us know
    completionStage.complete(value);

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


# Disclaimer

It's quite fresh code as you can tell from the version number. So the API may change and there may be some bugs. That being said, it
has quite good code coverage so I do not expect any obvious bugs.

For converting between different kinds of Java futures, please check [future-converter](https://github.com/lukas-krecan/future-converter)
it's bit more mature.

# Why can't I just use a CompletableFuture?

You definitely can. The main problem I have that it is tightly coupled with fork-join framework.
And fork-join framework is meant to be used mainly for CPU intensive tasks. But my usual use-case
is the opposite, I want to use asynchronous processing mainly for blocking tasks. I do not want to
block a thread while waiting for a network or database operation.

Of course, you do not have to use fork-join executors with CompletableFutures. You just have to be careful since it is
the default choice for async methods.



