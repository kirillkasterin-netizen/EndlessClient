package dev.endless.util.time;

public class Timer {
    private long lastMS;

    public Timer() {
        reset();
    }

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public boolean hasReached(long milliseconds) {
        return System.currentTimeMillis() - lastMS >= milliseconds;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public long getLastMS() {
        return lastMS;
    }

    public void setLastMS(long lastMS) {
        this.lastMS = lastMS;
    }
}
