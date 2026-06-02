package dev.endless.util.neuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import dev.endless.util.neuro.rotation.NeuroDataCollector;
import dev.endless.util.neuro.rotation.NeuroRotationPredictor;
import dev.endless.util.neuro.rotation.CatBoostRotationPredictor;
import dev.endless.util.neuro.rotation.PatternRecorder;
import dev.endless.util.neuro.rotation.PatternPlayer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Команды для управления ML-системой ротации
 * Использование в чате:
 * - .neuro start <название> - начать запись данных с названием
 * - .neuro stop - остановить запись
 * - .neuro save <название> - сохранить текущую запись
 * - .neuro list - показать список записей
 * - .neuro load - загрузить CatBoost модель
 * - .neuro info - информация о модели
 * - .neuro dir - открыть директорию с данными
 * - .neuro help - показать помощь
 */
public class NeuroCommand {
    
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static String currentRecordingName = null;
    
    /**
     * Обрабатывает команду
     * @param args аргументы команды
     */
    public static void execute(String[] args) {
        if (args.length == 0) {
            sendHelp();
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "start" -> handleStart(args);
            case "stop" -> handleStop();
            case "save" -> handleSave(args);
            case "list" -> handleList();
            case "load" -> handleLoad();
            case "info" -> handleInfo();
            case "dir" -> handleDir();
            case "record" -> handleRecord(args);
            case "pattern" -> handlePattern(args);
            case "model" -> handleModel(args);
            case "train" -> handleTrain(args);
            case "help" -> sendHelp();
            default -> sendMessage("§cНеизвестная команда. Используйте .neuro help");
        }
    }
    
    /**
     * Начинает запись данных с названием
     */
    private static void handleStart(String[] args) {
        if (NeuroDataCollector.isRecording()) {
            sendMessage("§eЗапись уже идет! Используйте §c.neuro stop §eчтобы остановить");
            return;
        }
        
        String recordingName;
        if (args.length >= 2) {
            recordingName = args[1];
        } else {
            // Генерируем название по дате
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            recordingName = "session_" + sdf.format(new Date());
        }
        
        currentRecordingName = recordingName;
        
        sendMessage("§6========================================");
        sendMessage("§a§l✓ ЗАПИСЬ ДАННЫХ: §e" + recordingName);
        sendMessage("§6========================================");
        sendMessage("§c§l⚠ ВАЖНО: ВЫКЛЮЧИТЕ ВСЕ ЧИТЫ! ⚠");
        sendMessage("§6========================================");
        sendMessage("§7Система записывает §eВАШИ§7 движения мыши");
        sendMessage("§7Если KillAura включен - запись НЕ работает!");
        sendMessage("");
        sendMessage("§aКак правильно записывать:");
        sendMessage("§71. §cВыключите KillAura и другие читы");
        sendMessage("§72. §7Играйте §eВРУЧНУЮ§7 как обычно");
        sendMessage("§73. §7Двигайте мышью §eСАМИ");
        sendMessage("§74. §7Атакуйте §eСАМИ§7 (ЛКМ)");
        sendMessage("§75. §7Играйте §e15-20 минут");
        sendMessage("");
        sendMessage("§6========================================");
        
        NeuroDataCollector.startRecording();
        sendMessage("§a✓ Запись §e" + recordingName + " §aначата!");
        sendMessage("§7Играйте легитимно. Используйте §e.neuro stop §7для остановки");
    }
    
    /**
     * Останавливает запись данных
     */
    private static void handleStop() {
        if (!NeuroDataCollector.isRecording()) {
            sendMessage("§eЗапись не активна!");
            return;
        }
        
        int count = NeuroDataCollector.stopRecording();
        
        if (count > 0) {
            sendMessage("§a✓ Запись остановлена!");
            sendMessage("§aСобрано §e" + count + " §aсэмплов");
            if (currentRecordingName != null) {
                sendMessage("§7Сессия: §e" + currentRecordingName);
            }
            sendMessage("§7Данные сохранены в §ewraith/neuro_data/");
            sendMessage("");
            sendMessage("§aСледующие шаги:");
            sendMessage("§71. Запишите еще несколько сессий (минимум 1000 сэмплов)");
            sendMessage("§72. Используйте §e.neuro list §7чтобы посмотреть записи");
            sendMessage("§73. Обучите модель: §epython python/train_catboost_rotation.py");
            sendMessage("§74. Загрузите модель: §e.neuro load");
        } else {
            sendMessage("§c✗ Собрано 0 сэмплов!");
            sendMessage("§7Возможные причины:");
            sendMessage("§7- KillAura был включен (нужно выключить!)");
            sendMessage("§7- Не было целей рядом");
            sendMessage("§7- Вы не атаковали");
        }
        
        currentRecordingName = null;
    }
    
    /**
     * Сохраняет текущую запись с названием
     */
    private static void handleSave(String[] args) {
        if (!NeuroDataCollector.isRecording()) {
            sendMessage("§eЗапись не активна! Используйте §a.neuro start <название>");
            return;
        }
        
        if (args.length < 2) {
            sendMessage("§cИспользование: .neuro save <название>");
            sendMessage("§7Пример: §e.neuro save my_session");
            return;
        }
        
        String saveName = args[1];
        
        // Останавливаем запись и сохраняем
        int count = NeuroDataCollector.stopRecording();
        
        if (count > 0) {
            sendMessage("§a✓ Запись сохранена как §e" + saveName);
            sendMessage("§aСобрано §e" + count + " §aсэмплов");
            sendMessage("§7Данные в §ewraith/neuro_data/rotation_data_*.json");
        } else {
            sendMessage("§c✗ Нет данных для сохранения");
        }
        
        currentRecordingName = null;
    }
    
    /**
     * Показывает список записанных данных
     */
    private static void handleList() {
        try {
            Path dataDir = Paths.get("endless/neuro_data");
            
            if (!Files.exists(dataDir)) {
                sendMessage("§eДиректория §cwraith/neuro_data §eне найдена");
                sendMessage("§7Используйте §a.neuro start <название> §7для начала записи");
                return;
            }
            
            File[] files = dataDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            
            if (files == null || files.length == 0) {
                sendMessage("§eНет записанных данных");
                sendMessage("§7Используйте §a.neuro start <название> §7для начала записи");
                return;
            }
            
            sendMessage("§6========================================");
            sendMessage("§a§lСПИСОК ЗАПИСЕЙ §7(" + files.length + " файлов)");
            sendMessage("§6========================================");
            
            long totalSize = 0;
            for (File file : files) {
                long size = file.length();
                totalSize += size;
                String sizeStr = formatFileSize(size);
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(file.lastModified()));
                
                sendMessage("§e" + file.getName());
                sendMessage("  §7Размер: §f" + sizeStr + " §7| Дата: §f" + date);
            }
            
            sendMessage("");
            sendMessage("§7Общий размер: §f" + formatFileSize(totalSize));
            sendMessage("§6========================================");
            sendMessage("§7Для обучения модели:");
            sendMessage("§epython python/train_catboost_rotation.py");
            
        } catch (Exception e) {
            sendMessage("§cОшибка чтения директории: " + e.getMessage());
        }
    }
    
    /**
     * Загружает CatBoost модель
     */
    private static void handleLoad() {
        sendMessage("§7Загрузка CatBoost модели...");
        
        boolean success = CatBoostRotationPredictor.loadModels();
        
        if (success) {
            sendMessage("§a✓ CatBoost модель успешно загружена!");
            sendMessage("§7Теперь можно использовать режим §eNeuro §7в KillAura");
            sendMessage("");
            sendMessage("§aКак использовать:");
            sendMessage("§71. Включите §aKillAura");
            sendMessage("§72. Выберите ротацию §eNeuro");
            sendMessage("§73. Нейросеть будет использовать §eВАШИ§7 паттерны!");
        } else {
            sendMessage("§c✗ Ошибка загрузки модели!");
            sendMessage("");
            sendMessage("§7Возможные причины:");
            sendMessage("§71. Модель не обучена");
            sendMessage("§72. Файлы не найдены:");
            sendMessage("   §cwraith/models/catboost_rotation_yaw.cbm");
            sendMessage("   §cwraith/models/catboost_rotation_pitch.cbm");
            sendMessage("");
            sendMessage("§aКак исправить:");
            sendMessage("§71. Запишите данные: §e.neuro start <название>");
            sendMessage("§72. Обучите модель:");
            sendMessage("   §epython python/train_catboost_rotation.py");
            sendMessage("§73. Загрузите снова: §e.neuro load");
        }
    }
    
    /**
     * Показывает информацию о модели
     */
    private static void handleInfo() {
        sendMessage("§6========================================");
        sendMessage("§a§lИНФОРМАЦИЯ О NEURO ROTATION");
        sendMessage("§6========================================");
        
        // Статус записи
        if (NeuroDataCollector.isRecording()) {
            sendMessage("§7Запись: §aАКТИВНА");
            if (currentRecordingName != null) {
                sendMessage("§7Сессия: §e" + currentRecordingName);
            }
            int collected = NeuroDataCollector.getCollectedData().size();
            sendMessage("§7Собрано: §e" + collected + " §7сэмплов");
        } else {
            sendMessage("§7Запись: §cНЕАКТИВНА");
        }
        
        sendMessage("");
        
        // Статус модели
        if (CatBoostRotationPredictor.isModelLoaded()) {
            sendMessage("§7CatBoost модель: §aЗАГРУЖЕНА");
            sendMessage("§7Тип: §eCatBoost Regressor");
            sendMessage("§7Алгоритм: §eGradient Boosting");
        } else {
            sendMessage("§7CatBoost модель: §cНЕ ЗАГРУЖЕНА");
        }
        
        sendMessage("");
        
        // Информация о данных
        try {
            Path dataDir = Paths.get("endless/neuro_data");
            if (Files.exists(dataDir)) {
                File[] files = dataDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null && files.length > 0) {
                    sendMessage("§7Записей данных: §e" + files.length);
                    long totalSize = 0;
                    for (File f : files) totalSize += f.length();
                    sendMessage("§7Общий размер: §e" + formatFileSize(totalSize));
                } else {
                    sendMessage("§7Записей данных: §c0");
                }
            } else {
                sendMessage("§7Записей данных: §c0");
            }
        } catch (Exception e) {
            sendMessage("§7Записей данных: §cОшибка чтения");
        }
        
        sendMessage("");
        sendMessage("§6========================================");
        sendMessage("§7Используйте §e.neuro help §7для справки");
    }
    
    /**
     * Открывает директорию с данными
     */
    private static void handleDir() {
        try {
            Path dataDir = Paths.get("endless/neuro_data");
            
            // Создаем директорию если не существует
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                sendMessage("§aСоздана директория: §ewraith/neuro_data/");
            }
            
            // Открываем в проводнике
            File dir = dataDir.toFile();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("explorer.exe " + dir.getAbsolutePath());
            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                Runtime.getRuntime().exec("open " + dir.getAbsolutePath());
            } else {
                Runtime.getRuntime().exec("xdg-open " + dir.getAbsolutePath());
            }
            
            sendMessage("§aОткрыта директория: §ewraith/neuro_data/");
            sendMessage("§7Здесь хранятся записанные данные");
            
        } catch (Exception e) {
            sendMessage("§cОшибка открытия директории: " + e.getMessage());
            sendMessage("§7Путь: §ewraith/neuro_data/");
        }
    }
    
    /**
     * Форматирует размер файла
     */
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }
    
    /**
     * Обработка команд записи паттернов
     */
    private static void handlePattern(String[] args) {
        if (args.length < 2) {
            sendMessage("§cИспользование: .neuro pattern <start|stop>");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "start" -> {
                if (PatternRecorder.isRecording()) {
                    sendMessage("§eЗапись паттернов уже идет!");
                } else {
                    sendMessage("§6========================================");
                    sendMessage("§a§l✓ ЗАПИСЬ ПАТТЕРНОВ АТАК");
                    sendMessage("§6========================================");
                    sendMessage("§7Система записывает §eПОЛНЫЕ§7 паттерны:");
                    sendMessage("§7- Как ты наводишься на цель");
                    sendMessage("§7- Как двигаешь мышью");
                    sendMessage("§7- Когда атакуешь");
                    sendMessage("");
                    sendMessage("§c§l⚠ ВАЖНО: ВЫКЛЮЧИ KillAura!");
                    sendMessage("");
                    sendMessage("§aКак записывать:");
                    sendMessage("§71. §cВыключи KillAura");
                    sendMessage("§72. §7Играй §eВРУЧНУЮ§7 10-15 минут");
                    sendMessage("§73. §7Атакуй мобов/игроков");
                    sendMessage("§74. §7Играй естественно");
                    sendMessage("§6========================================");
                    
                    PatternRecorder.startRecording();
                    sendMessage("§a✓ Запись паттернов начата!");
                }
            }
            case "stop" -> {
                if (!PatternRecorder.isRecording()) {
                    sendMessage("§eЗапись паттернов не активна!");
                } else {
                    int count = PatternRecorder.stopRecording();
                    if (count > 0) {
                        sendMessage("§a✓ Запись остановлена!");
                        sendMessage("§aСобрано §e" + count + " §aпаттернов атак");
                        sendMessage("§7Данные сохранены в wraith/patterns/");
                        sendMessage("");
                        sendMessage("§aТеперь:");
                        sendMessage("§71. Включи KillAura");
                        sendMessage("§72. Выбери режим §eNeuro");
                        sendMessage("§73. Нейросеть будет повторять §eТВОИ§7 паттерны!");
                    } else {
                        sendMessage("§c✗ Собрано 0 паттернов!");
                        sendMessage("§7Возможные причины:");
                        sendMessage("§7- KillAura был включен");
                        sendMessage("§7- Не было целей рядом");
                        sendMessage("§7- Ты не атаковал");
                    }
                }
            }
            default -> sendMessage("§cИспользование: .neuro pattern <start|stop>");
        }
    }
    
    /**
     * Обработка команд записи данных
     */
    private static void handleRecord(String[] args) {
        if (args.length < 2) {
            sendMessage("§cИспользование: .neuro record <start|stop>");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "start" -> {
                if (NeuroDataCollector.isRecording()) {
                    sendMessage("§eЗапись уже идет!");
                } else {
                    sendMessage("§6========================================");
                    sendMessage("§c§l⚠ ВАЖНО: ВЫКЛЮЧИТЕ ВСЕ ЧИТЫ! ⚠");
                    sendMessage("§6========================================");
                    sendMessage("§7Система записывает §eВАШИ§7 движения мыши");
                    sendMessage("§7Если KillAura включен - запись НЕ работает!");
                    sendMessage("");
                    sendMessage("§aКак правильно записывать:");
                    sendMessage("§71. §cВыключите KillAura и другие читы");
                    sendMessage("§72. §7Играйте §eВРУЧНУЮ§7 как обычно");
                    sendMessage("§73. §7Двигайте мышью §eСАМИ");
                    sendMessage("§74. §7Атакуйте §eСАМИ§7 (ЛКМ)");
                    sendMessage("§75. §7Играйте §e15-20 минут");
                    sendMessage("");
                    sendMessage("§6========================================");
                    
                    NeuroDataCollector.startRecording();
                    sendMessage("§a✓ Запись начата!");
                    sendMessage("§7Играйте легитимно. Данные сохранятся при остановке.");
                }
            }
            case "stop" -> {
                if (!NeuroDataCollector.isRecording()) {
                    sendMessage("§eЗапись не активна!");
                } else {
                    int count = NeuroDataCollector.stopRecording();
                    if (count > 0) {
                        sendMessage("§a✓ Запись остановлена!");
                        sendMessage("§aСобрано §e" + count + " §aсэмплов");
                        sendMessage("§7Данные сохранены в wraith/neuro_data/");
                        sendMessage("");
                        sendMessage("§aСледующий шаг:");
                        sendMessage("§e.neuro train start §7- автообучение");
                    } else {
                        sendMessage("§c✗ Собрано 0 сэмплов!");
                        sendMessage("§7Возможные причины:");
                        sendMessage("§7- KillAura был включен (нужно выключить!)");
                        sendMessage("§7- Не было целей рядом");
                        sendMessage("§7- Вы не атаковали");
                    }
                }
            }
            default -> sendMessage("§cИспользование: .neuro record <start|stop>");
        }
    }
    
    /**
     * Обработка команд модели
     */
    private static void handleModel(String[] args) {
        if (args.length < 2) {
            sendMessage("§cИспользование: .neuro model <load|reload|status|use>");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "load", "reload" -> {
                sendMessage("§7Загрузка модели...");
                boolean success = NeuroRotationPredictor.loadModel("endless/models/rotation_model.pt");
                if (success) {
                    sendMessage("§aМодель успешно загружена!");
                    sendMessage("§7Теперь можно использовать режим Neuro в KillAura");
                } else {
                    sendMessage("§cОшибка загрузки модели!");
                    sendMessage("§7Проверьте наличие файла wraith/models/rotation_model.pt");
                    sendMessage("§7Обучите модель используя §e.neuro train start");
                }
            }
            case "use" -> {
                if (args.length < 3) {
                    sendMessage("§cИспользование: .neuro model use <default|aggressive|defensive|elytra|custom>");
                    sendMessage("§7Доступные модели:");
                    sendMessage("  §edefault §7- универсальная модель");
                    sendMessage("  §eaggressive §7- агрессивный стиль");
                    sendMessage("  §edefensive §7- защитный стиль");
                    sendMessage("  §eelytra §7- специализация на elytra PvP");
                    sendMessage("  §ecustom §7- ваша обученная модель");
                    return;
                }
                
                String modelName = args[2].toLowerCase();
                String modelPath;
                String displayName;
                
                switch (modelName) {
                    case "default" -> {
                        modelPath = "endless/models/pretrained/default_model.pt";
                        displayName = "Default (универсальная)";
                    }
                    case "aggressive" -> {
                        modelPath = "endless/models/pretrained/aggressive_model.pt";
                        displayName = "Aggressive (агрессивная)";
                    }
                    case "defensive" -> {
                        modelPath = "endless/models/pretrained/defensive_model.pt";
                        displayName = "Defensive (защитная)";
                    }
                    case "elytra" -> {
                        modelPath = "endless/models/pretrained/elytra_specialist_model.pt";
                        displayName = "Elytra Specialist";
                    }
                    case "custom" -> {
                        modelPath = "endless/models/rotation_model.pt";
                        displayName = "Custom (ваша модель)";
                    }
                    default -> {
                        sendMessage("§cНеизвестная модель: " + modelName);
                        sendMessage("§7Используйте: default, aggressive, defensive, elytra, custom");
                        return;
                    }
                }
                
                sendMessage("§7Загрузка модели §e" + displayName + "§7...");
                boolean success = NeuroRotationPredictor.loadModel(modelPath);
                
                if (success) {
                    sendMessage("§aМодель §e" + displayName + " §aзагружена!");
                    sendMessage("§7Теперь можно использовать режим Neuro в KillAura");
                } else {
                    sendMessage("§cОшибка загрузки модели!");
                    sendMessage("§7Проверьте наличие файла: " + modelPath);
                    if (!modelName.equals("custom")) {
                        sendMessage("§7Создайте предобученные модели:");
                        sendMessage("§7  python python/create_pretrained_models.py");
                    }
                }
            }
            case "status" -> {
                if (NeuroRotationPredictor.isModelLoaded()) {
                    sendMessage("§aМодель загружена и готова к использованию");
                } else {
                    sendMessage("§cМодель не загружена");
                    sendMessage("§7Используйте .neuro model load для загрузки");
                }
            }
            default -> sendMessage("§cИспользование: .neuro model <load|reload|status|use>");
        }
    }
    
    /**
     * Обработка команд обучения
     */
    private static void handleTrain(String[] args) {
        if (args.length < 2) {
            sendMessage("§cИспользование: .neuro train <start|stop|status>");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "start" -> {
                if (ModelTrainer.isTraining()) {
                    sendMessage("§eОбучение уже запущено!");
                } else {
                    sendMessage("§aЗапуск автоматического обучения...");
                    sendMessage("§7Проверка зависимостей и данных...");
                    
                    ModelTrainer.startTraining().thenAccept(success -> {
                        if (success) {
                            sendMessage("§a✓ Обучение завершено успешно!");
                            sendMessage("§7Модель готова к использованию");
                        } else {
                            sendMessage("§c✗ Обучение не удалось");
                            sendMessage("§7Проверьте консоль для деталей");
                        }
                    });
                }
            }
            case "stop" -> {
                if (ModelTrainer.isTraining()) {
                    ModelTrainer.stopTraining();
                    sendMessage("§eОбучение остановлено");
                } else {
                    sendMessage("§eОбучение не запущено");
                }
            }
            case "status" -> {
                if (ModelTrainer.isTraining()) {
                    sendMessage("§eОбучение в процессе...");
                    sendMessage("§7Проверьте консоль для прогресса");
                } else {
                    sendMessage("§7Обучение не запущено");
                }
            }
            default -> sendMessage("§cИспользование: .neuro train <start|stop|status>");
        }
    }
    
    /**
     * Показывает справку
     */
    private static void sendHelp() {
        sendMessage("§6========================================");
        sendMessage("§a§l§nNEURO ROTATION COMMANDS");
        sendMessage("§6========================================");
        sendMessage("");
        sendMessage("§e§lОСНОВНЫЕ КОМАНДЫ (CatBoost):");
        sendMessage("§a.neuro start <название> §7- начать запись");
        sendMessage("§a.neuro stop §7- остановить запись");
        sendMessage("§a.neuro save <название> §7- сохранить запись");
        sendMessage("§a.neuro list §7- список записей");
        sendMessage("§a.neuro load §7- загрузить модель");
        sendMessage("§a.neuro info §7- информация о системе");
        sendMessage("§a.neuro dir §7- открыть папку с данными");
        sendMessage("");
        sendMessage("§c§l⚠ ВАЖНО:");
        sendMessage("§7Запись работает §cТОЛЬКО БЕЗ ЧИТОВ!");
        sendMessage("§7Выключи KillAura и играй §eВРУЧНУЮ");
        sendMessage("");
        sendMessage("§a§lБЫСТРЫЙ СТАРТ:");
        sendMessage("§71. §cВыключи KillAura!");
        sendMessage("§72. §e.neuro start моя_сессия");
        sendMessage("§73. §7Играй §eВРУЧНУЮ§7 15-20 мин");
        sendMessage("§74. §e.neuro stop");
        sendMessage("§75. §7Повтори 2-3 раза (разные сессии)");
        sendMessage("§76. §7Обучи модель:");
        sendMessage("    §epython python/train_catboost_rotation.py");
        sendMessage("§77. §e.neuro load");
        sendMessage("§78. §aВключи KillAura§7 -> Ротация -> §eNeuro");
        sendMessage("");
        sendMessage("§7§lДОПОЛНИТЕЛЬНО:");
        sendMessage("§e.neuro pattern start/stop §7- запись паттернов");
        sendMessage("§e.neuro model load §7- LSTM модель (старая)");
        sendMessage("§e.neuro train start §7- автообучение");
        sendMessage("§6========================================");
    }
    
    /**
     * Отправляет сообщение в чат
     */
    private static void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§8[§5Neuro§8] §r" + message), false);
        }
    }
}
