package net.fabricmc.api;

/** Заглушка лише для компіляції (у jar не потрапляє): справжній інтерфейс дає Fabric Loader. */
public interface ClientModInitializer {
    void onInitializeClient();
}
