package com.agui;

import java.util.concurrent.atomic.AtomicBoolean;

public class LoadingAnimation {
    private static final String[] SPINNER_CHARS = {"|", "/", "-", "\\"};
    private static final String[] DOTS_ANIMATION = {"", ".", "..", "..."};

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread animationThread;
    private final String message;
    private final AnimationType type;
    private final int delayMs;

    public enum AnimationType {
        SPINNER, DOTS
    }

    public LoadingAnimation(String message) {
        this(message, AnimationType.SPINNER, 200);
    }

    public LoadingAnimation(String message, AnimationType type, int delayMs) {
        this.message = message;
        this.type = type;
        this.delayMs = delayMs;
    }

    public void start() {
        if (isRunning.get()) {
            return; // Already running
        }

        isRunning.set(true);
        animationThread = new Thread(this::animate);
        animationThread.setDaemon(true);
        animationThread.start();
    }

    public void stop() {
        if (!isRunning.get()) {
            return; // Not running
        }

        isRunning.set(false);

        if (animationThread != null) {
            try {
                animationThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Clear the loading line
        System.out.print("\r" + " ".repeat(50) + "\r");
        System.out.flush();
    }

    private void animate() {
        int index = 0;
        String[] animation = type == AnimationType.SPINNER ? SPINNER_CHARS : DOTS_ANIMATION;

        while (isRunning.get()) {
            String frame = animation[index % animation.length];

            if (type == AnimationType.SPINNER) {
                System.out.print("\r" + message + " " + frame);
            } else {
                System.out.print("\r" + message + frame);
            }
            System.out.flush();

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            index++;
        }
    }
}