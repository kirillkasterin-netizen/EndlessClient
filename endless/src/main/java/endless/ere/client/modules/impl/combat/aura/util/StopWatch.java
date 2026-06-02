package endless.ere.client.modules.impl.combat.aura.util;

/** Перенос StopWatch из Wraith. */
public class StopWatch {
    public long lastMS = System.currentTimeMillis();

    public void reset() {
        this.lastMS = System.currentTimeMillis();
    }

    public boolean isReached(long time) {
        return System.currentTimeMillis() - this.lastMS > time;
    }

    public boolean finished(double delay) {
        return (double) System.currentTimeMillis() - delay >= (double) this.lastMS;
    }

    public boolean every(double delay) {
        boolean f = this.finished(delay);
        if (f) this.reset();
        return f;
    }

    public long getTime() {
        return System.currentTimeMillis() - this.lastMS;
    }
}
