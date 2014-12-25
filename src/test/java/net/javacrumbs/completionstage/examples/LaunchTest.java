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
package net.javacrumbs.completionstage.examples;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

public class LaunchTest {

    @Test
    public void testLaunch() {
        CompletableFuture<AuthorizationCode> authorizationCode = getAuthorizationCode();
        authorizationCode
                .thenApply(this::launchMissiles)
                .thenApply(this::generateDamageReport)
                .thenAccept(this::updateMainScreen)
                .exceptionally(this::playAlertSound);
    }

    private Void playAlertSound(Throwable throwable) {
        return null;
    }

    private void updateMainScreen(String s) {

    }

    private String generateDamageReport(Boolean result) {
        return null;
    }

    private boolean launchMissiles(AuthorizationCode authorizationCode) {
        return false;
    }


    private CompletableFuture<AuthorizationCode> getAuthorizationCode() {
        return new CompletableFuture<>();
    }

    private class AuthorizationCode {
    }
}
