package dev.endless.util.discord;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;

/**
 * Endless Discord Rich Presence integration.
 *
 * Built on top of {@link meteordevelopment.discordipc.DiscordIPC} (a pure-Java
 * IPC client — no native libraries needed). Exposes a tiny static API used by
 * mixins and the main mod entry point:
 *
 * <ul>
 *   <li>{@link #init()} — opens the IPC channel and starts the watchdog.</li>
 *   <li>{@link #updateInMenu()} — call when the player enters the title/menu screen.</li>
 *   <li>{@link #updateInGame(String)} — call when the player joins a world or server.</li>
 *   <li>{@link #shutdown()} — release the IPC connection.</li>
 * </ul>
 *
 * The presence always shows {@code "Endless 1.21.4"} on the first line; the
 * second line is the current game state (menu or server address).
 *
 * Stays silent on platforms where Discord is not running — failures are
 * caught so they never break the game.
 */
public final class DiscordRPC {

    /** Discord application id used for the rich-presence assets and name. */
    private static final long APPLICATION_ID = 1506022321998008452L;

    /** Constant first-line text shown in the presence card. */
    private static final String DETAILS = "Endless 1.21.4";

    /** Default state shown before any explicit update is pushed. */
    private static final String STATE_LOADING = "Загрузка…";

    /** Watchdog thread that periodically re-pushes the presence to Discord. */
    private static Thread watchdogThread;
    private static volatile boolean running;

    private static volatile long startTimestamp;
    private static volatile String currentState = STATE_LOADING;

    private DiscordRPC() { /* utility */ }

    /**
     * Initialises the IPC connection. Idempotent — calling twice is a no-op.
     * Safe to call on the main thread; all blocking IO happens on the watchdog.
     */
    public static synchronized void init() {
        if (running) return;
        running = true;
        startTimestamp = System.currentTimeMillis() / 1000L;

        // Discord IPC errors are quietly swallowed so they don't pollute the log.
        DiscordIPC.setOnError((code, message) -> { /* ignore */ });

        watchdogThread = new Thread(DiscordRPC::runWatchdog, "Endless-DiscordRPC");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    /**
     * Switches the presence to the "in main menu" state.
     */
    public static void updateInMenu() {
        setState("В главном меню");
    }

    /**
     * Switches the presence to the "in game" state with the given context.
     *
     * @param serverName Server address, world name, or any other label
     *                   describing where the player is. Truncated to 128 chars.
     */
    public static void updateInGame(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            setState("Играет");
        } else {
            setState("Играет на " + serverName);
        }
    }

    /**
     * Generic update — sets details/state directly. Mostly for parity with the
     * older API; the details field is fixed to {@value #DETAILS} regardless.
     */
    public static void updatePresence(String details, String state) {
        setState(state == null ? "" : state);
    }

    /**
     * Tears down the IPC connection. Called from the JVM shutdown hook.
     */
    public static synchronized void shutdown() {
        running = false;
        if (watchdogThread != null) {
            watchdogThread.interrupt();
            watchdogThread = null;
        }
        try {
            DiscordIPC.stop();
        } catch (Throwable ignored) {
            // Discord may already be gone — that's fine.
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private static void setState(String state) {
        currentState = state == null ? "" : state;
        if (running && DiscordIPC.isConnected()) {
            try {
                pushPresence();
            } catch (Throwable ignored) {
                // Best-effort: the watchdog will retry on the next tick.
            }
        }
    }

    /**
     * Builds a fresh {@link RichPresence} from the current state and sends it
     * to Discord.
     */
    private static void pushPresence() {
        RichPresence presence = new RichPresence();
        presence.setDetails(DETAILS);
        presence.setState(currentState);
        presence.setStart(startTimestamp);
        DiscordIPC.setActivity(presence);
    }

    /**
     * Background loop that establishes (and re-establishes) the IPC connection
     * and periodically refreshes the presence. Discord drops idle presences
     * after a while, so a heartbeat keeps it visible.
     */
    private static void runWatchdog() {
        try {
            // Initial connection. setActivity() is also called from the
            // ready-callback for the very first push.
            DiscordIPC.start(APPLICATION_ID, DiscordRPC::pushPresence);

            while (running) {
                if (DiscordIPC.isConnected()) {
                    try {
                        pushPresence();
                    } catch (Throwable ignored) { /* retry next tick */ }
                } else {
                    // Try to (re)connect. start() is idempotent on a closed
                    // connection.
                    try {
                        DiscordIPC.start(APPLICATION_ID, DiscordRPC::pushPresence);
                    } catch (Throwable ignored) { /* Discord not running yet */ }
                }
                try {
                    Thread.sleep(15_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Throwable ignored) {
            // Library failed to load (e.g. unsupported platform) — disable.
            running = false;
        }
    }
}
