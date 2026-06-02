package endless.ere.base.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Удалённый kill-switch без своего сервера.
 *
 * <p>Берёт JSON-статус из любого публично-читаемого источника, который ты контролируешь.
 * Поддержано прямо сейчас:
 *
 * <ol>
 *   <li><b>GitHub Gist</b> — самый простой вариант. Создаёшь приватный гист с одним
 *       файлом {@code status.json}, копируешь его «Raw» URL и кладёшь в
 *       {@link #ENDPOINTS}. Редактируешь Gist — клиенты подхватывают изменения за
 *       {@link #POLL_INTERVAL}.</li>
 *   <li><b>Pastebin / любой raw-хостинг</b> — то же самое, любой HTTPS-URL,
 *       отдающий JSON.</li>
 *   <li><b>Публичный Telegram-канал</b> — указываешь URL вида
 *       {@code https://t.me/s/<channel>}, и клиент берёт самое свежее сообщение,
 *       внутри которого должен лежать JSON-блок (можно в кавычках или просто текстом).</li>
 * </ol>
 *
 * <p>Формат JSON:
 * <pre>{
 *   "enabled":     true|false,
 *   "min_version": "2.0",
 *   "uuid":        "*",        // или конкретный UUID игрока
 *   "reason":      "..."
 * }</pre>
 *
 * <p>Если {@code enabled} = false, или версия клиента ниже {@code min_version},
 * или {@code uuid} совпадает с UUID игрока, клиент аккуратно завершит работу
 * через {@link MinecraftClient#scheduleStop()}.
 *
 * <p>Безопасность: эндпоинты идут по HTTPS, писать в Gist/канал может только владелец
 * аккаунта. Подпись не требуется — если кто-то и узнает URL, изменить контент
 * он не сможет, а блокировать эндпоинт через hosts невыгодно атакующему
 * (default-on: при недоступном эндпоинте клиент остаётся включён).
 */
public final class RemoteKillSwitch {

    /**
     * Список URL, в порядке приоритета. URL хранится зашифрованным (XOR + base64),
     * чтобы он не светился в jar в виде plain-text. Расшифровывается один раз
     * при первом обращении.
     */
    private static final byte[] URL_KEY = new byte[] {
            (byte)0x65, (byte)0x6e, (byte)0x64, (byte)0x6c,
            (byte)0x65, (byte)0x73, (byte)0x73, (byte)0x2d,
            (byte)0x67, (byte)0x75, (byte)0x61, (byte)0x72,
            (byte)0x64, (byte)0x2d, (byte)0x6b, (byte)0x31
    };
    /** Зашифрованные URL'ы. См. {@link #encrypt(String)} в jUnit-классе. */
    private static final List<String> ENC_ENDPOINTS = List.of(
            // plain: https://gist.githubusercontent.com/lmgcheat/33d2a34cdcb40f10fcb92ab8b6bfae2d/raw/gistfile1.txt
            "DRoQHBZJXAIAHBIGSkoCRQ0bBhkWFgFOCBsVFwpZRVIKA0sACBQQRQIUFV1XHg8DBF1QDwEQERlXE1BCAk4JCFcPBlQHRRFLBhBTFktfCkZKCQ0fERUaQQJETwYcWQ=="
    );

    private static volatile List<String> resolvedEndpoints;

    private static final Duration POLL_INTERVAL  = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Endless-KillSwitch");
                t.setDaemon(true);
                return t;
            });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile boolean started = false;
    private static volatile boolean disabled = false;

    private RemoteKillSwitch() {}

    public static synchronized void start() {
        if (started) return;
        List<String> urls = decryptEndpoints();
        if (urls.isEmpty()) return;
        resolvedEndpoints = urls;
        started = true;
        SCHEDULER.scheduleAtFixedRate(RemoteKillSwitch::poll,
                1, POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);
    }

    public static boolean isDisabled() {
        return disabled;
    }

    private static void poll() {
        List<String> urls = resolvedEndpoints;
        if (urls == null) return;
        for (String url : urls) {
            try {
                JsonObject body = fetch(url);
                if (body == null) continue;
                if (shouldDisable(body)) {
                    String reason = body.has("reason") ? body.get("reason").getAsString() : "remote disable";
                    triggerShutdown(reason);
                }
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static List<String> decryptEndpoints() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String enc : ENC_ENDPOINTS) {
            try {
                byte[] data = java.util.Base64.getDecoder().decode(enc);
                byte[] plain = new byte[data.length];
                for (int i = 0; i < data.length; i++) {
                    plain[i] = (byte) (data[i] ^ URL_KEY[i % URL_KEY.length]);
                }
                out.add(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
        }
        return List.copyOf(out);
    }

    private static JsonObject fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Endless-Client/2.0")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        String text = resp.body();
        if (text == null || text.isBlank()) return null;

        // Telegram t.me/s/CHANNEL — HTML, надо вытащить последний JSON-блок.
        if (url.contains("t.me/s/")) {
            String latest = extractLatestJsonFromTelegramHtml(text);
            if (latest == null) return null;
            text = latest;
        } else {
            String trimmed = text.trim();
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            text = trimmed.substring(start, end + 1);
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /**
     * t.me/s/<channel> отдаёт HTML со списком сообщений; берём последнее
     * сообщение и из него вытаскиваем JSON-блок (между первой {@code {} и последней {@code }}).
     */
    private static final Pattern TG_MESSAGE_TEXT = Pattern.compile(
            "<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);

    private static String extractLatestJsonFromTelegramHtml(String html) {
        Matcher m = TG_MESSAGE_TEXT.matcher(html);
        String last = null;
        while (m.find()) last = m.group(1);
        if (last == null) return null;
        // Убираем <br>, теги форматирования и HTML-entities грубо.
        String text = last
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }

    private static boolean shouldDisable(JsonObject body) {
        if (body.has("enabled") && !body.get("enabled").getAsBoolean()) return true;

        if (body.has("min_version")) {
            try {
                if (compareVersions(endless.ere.Endless.VER, body.get("min_version").getAsString()) < 0) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        if (body.has("uuid")) {
            String target = body.get("uuid").getAsString();
            if (target != null && !target.isBlank() && !"*".equals(target)) {
                try {
                    UUID local = MinecraftClient.getInstance().getSession().getUuidOrNull();
                    if (local != null && local.toString().equalsIgnoreCase(target)) return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? safeInt(pa[i]) : 0;
            int vb = i < pb.length ? safeInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int safeInt(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }

    private static synchronized void triggerShutdown(String reason) {
        if (disabled) return;
        disabled = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    if (mc.player != null && mc.world != null) {
                        mc.world.disconnect();
                    }
                } catch (Throwable ignored) {}
                try {
                    mc.scheduleStop();
                } catch (Throwable ignored) {}
            });
        }
        SCHEDULER.schedule(() -> System.exit(0), 1, TimeUnit.SECONDS);
        System.err.println("[Endless] Remote disable: " + reason);
    }
}
