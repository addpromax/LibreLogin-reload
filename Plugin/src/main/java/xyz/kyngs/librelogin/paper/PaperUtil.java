/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class PaperUtil {

    public static void runSyncAndWait(Runnable runnable, PaperLibreLogin plugin) {
        var scheduler = plugin.getScheduler();
        if (scheduler.isPrimaryThread()) {
            runnable.run();
        } else {
            var future = new CompletableFuture<Void>();
            scheduler.runTask(plugin.getBootstrap(), () -> {
                runnable.run();
                future.complete(null);
            });
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
