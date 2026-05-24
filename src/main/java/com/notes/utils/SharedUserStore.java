package com.notes.utils;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe credential store.
 *
 * RegistrationTests pushes email+password pairs here after each successful
 * registration. NoteCreationTests polls a pair when it needs to log in.
 *
 * This avoids NoteCreationTests having to re-register users, which would
 * create unnecessary test interdependency — while still letting the suites
 * share data when they run sequentially (Registration → Login → NoteCreation).
 *
 * If the queue is empty (standalone note-creation run), NoteCreationTests
 * falls back to registering a fresh user inline.
 */
public class SharedUserStore {

    private static final ConcurrentLinkedQueue<String[]> STORE = new ConcurrentLinkedQueue<>();

    private SharedUserStore() {}

    /**
     * Called by RegistrationTests after a successful registration.
     *
     * @param email    the registered email
     * @param password the password used during registration
     */
    public static void push(String email, String password) {
        STORE.offer(new String[]{email, password});
    }

    /**
     * Called by NoteCreationTests to retrieve (and remove) one credential pair.
     *
     * @return String[]{email, password} or null if the store is empty
     */
    public static String[] poll() {
        return STORE.poll();
    }

    /** Peek without removing — useful for debugging. */
    public static int size() {
        return STORE.size();
    }
}
