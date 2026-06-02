# ─────────────────────────────────────────────────────────────────────────────
# ProGuard configuration for Wraith Client (Fabric 1.21.4 + Mixin)
#
# Цель: обфусцировать имена внутренних утилит/UI, но НЕ ломать:
#   - Fabric ModInitializer entrypoint (ru.wraith.Wraith)
#   - Mixin-классы (имена и пакет должны совпадать с wraith.mixins.json)
#   - Reflection-доступ к настройкам модулей (Module.getSettings() через
#     Class.getDeclaredFields(), фильтруется по типу Setting)
#   - Event Bus (Guava): @Subscribe методы, классы событий
#   - Lombok-генерируемые методы и Gson-сериализацию
# ─────────────────────────────────────────────────────────────────────────────

# Не оптимизировать (Fabric/Mixin не любят optimization passes).
-dontoptimize
-dontpreverify

# Не вырезать «неиспользуемые» поля/методы — они часто используются через
# reflection (модули, настройки, mixin) и Guava EventBus.
-dontshrink

# Безопасно расширять видимость, но не переупаковывать всё в один пакет —
# это сломает поиск Mixin-классов по wraith.mixins.json.
-allowaccessmodification

# Сохраняем критичные атрибуты, нужные для рантайма Fabric/Mixin/Lombok.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleTypeAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable

-renamesourcefileattribute SourceFile
-keepparameternames

# ─────────────────────────────────────────────────────────────────────────────
# Fabric / Mixin entry points (имя и пакет ДОЛЖНЫ остаться)
# ─────────────────────────────────────────────────────────────────────────────

# Главный класс мода — указан в fabric.mod.json как entrypoint.
-keep class ru.wraith.Wraith { *; }

-keep class * implements net.fabricmc.api.ModInitializer { *; }
-keep class * implements net.fabricmc.api.ClientModInitializer { *; }
-keep class * implements net.fabricmc.api.DedicatedServerModInitializer { *; }

# Все Mixin'ы перечислены в wraith.mixins.json по простым именам.
# Их пакет (ru.wraith.mixin.*) и имена менять нельзя.
-keep class ru.wraith.mixin.** { *; }
-keepnames class ru.wraith.mixin.**

-keep @org.spongepowered.asm.mixin.Mixin class * { *; }
-keep @org.spongepowered.asm.mixin.Unique class * { *; }
-keepclassmembers class * {
    @org.spongepowered.asm.mixin.** *;
}

# ─────────────────────────────────────────────────────────────────────────────
# Модули, настройки, события — критичны для reflection'а Wraith'а
# ─────────────────────────────────────────────────────────────────────────────

# Модули создаются по new ClassName(), а Module.getSettings() через
# getDeclaredFields() ищет поля типа Setting → нельзя ни вырезать поля,
# ни ломать иерархию.
-keep class ru.wraith.module.** { *; }
-keep class ru.wraith.module.settings.** { *; }

# События подписываются через Guava @Subscribe, ищутся по сигнатуре.
-keep class ru.wraith.event.** { *; }
-keepclassmembers class * {
    @com.google.common.eventbus.Subscribe *;
}

# Команды и мисс-утилиты, к которым ходят строго по типам.
-keep class ru.wraith.util.commands.** { *; }
-keep class ru.wraith.util.base.Instance { *; }
-keep class ru.wraith.util.friend.** { *; }
-keep class ru.wraith.util.rotation.** { *; }

# Theme storage / config'и парсятся Gson'ом по полям.
-keep class ru.wraith.module.settings.impl.** { *; }
-keepclassmembers class ru.wraith.module.settings.impl.** {
    <fields>;
    <init>(...);
}

# ─────────────────────────────────────────────────────────────────────────────
# Сторонние фреймворки
# ─────────────────────────────────────────────────────────────────────────────

# Gson — поля моделей и адаптеры.
-keepclassmembers class * {
    @com.google.gson.annotations.** *;
}
-keep class * implements com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# Lombok — оставляем сгенерированные методы.
-keepclassmembers class * {
    @lombok.** *;
}
-keep class lombok.** { *; }

# Enum'ы — Minecraft часто читает values()/valueOf() через reflection.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Native-методы (DJL/CatBoost/PyTorch).
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI / DJL / PyTorch — не обфусцируем библиотеки, они подгружаются по имени.
-keep class ai.djl.** { *; }
-keep class ai.catboost.** { *; }
-keep class oshi.** { *; }
-keep class meteordevelopment.** { *; }

# Mixin / sponge — тоже не трогаем.
-keep class org.spongepowered.** { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Прочее
# ─────────────────────────────────────────────────────────────────────────────

# Сериализация — иногда используется по readObject/writeObject.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Подавляем шум.
-dontwarn **
-ignorewarnings
-dontnote **
