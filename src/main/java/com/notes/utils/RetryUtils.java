package com.notes.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

public class RetryUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);
    private RetryUtils() {}

    public static <T> T retry(int maxAttempts, long delayMs, Supplier<T> action) {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try { return action.get(); }
            catch (Exception e) {
                last = e;
                log.warn("Attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new RuntimeException("All " + maxAttempts + " attempts failed", last);
    }
}