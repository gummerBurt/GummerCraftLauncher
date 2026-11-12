<?php
/*
 * Список модпаків v2 для лаунчера 1.32+ (GummerCraft).
 *
 * Де лежить на CDN: /mnt/cdn-volume/gummercraft/modpacks/packages-v2.php, поруч з packages.php (v1).
 * Адреса: https://cdn.gummer.cc/modpacks/packages-v2.php?key=... (окрема локація nginx не потрібна:
 * спрацьовує загальне правило для *.php).
 *
 * Як працює: підключає packages.php як є (той самий список, ті самі приватні ключі, які живуть лише на CDN)
 * і доповнює кожен пак полями з packages-v2-meta.json:
 *   priority          - перевизначає пріоритет з packages.php (більший вище в списку);
 *   group             - група паків (наприклад "main"): лаунчер показує групу однією карткою з вибором версії;
 *   server            - true для паків, що мають свій сервер (показуються першими);
 *   recommendedMemory - рекомендована пам'ять у МБ (дефолт для пам'яті цього паку);
 *   serverAddress     - адреса сервера (host[:port]): онлайн на картці і «Одразу на сервер» (з 1.32).
 * Шлях location робиться абсолютним від кореня (/v1/packages/...), бо цей файл лежить за іншою адресою, ніж v1.
 *
 * packages.php і v1 не змінюються: лаунчери 1.31 і старші падають на будь-якому новому полі, тому нові поля лише тут.
 */

ob_start();
include __DIR__ . '/packages.php';
ob_end_clean();

$meta = array();
$metaFile = __DIR__ . '/packages-v2-meta.json';
if (is_file($metaFile)) {
    $decoded = json_decode(file_get_contents($metaFile), true);
    if (is_array($decoded)) {
        $meta = $decoded;
    }
}
$metaPackages = (isset($meta['packages']) && is_array($meta['packages'])) ? $meta['packages'] : array();
$metaGroups = (isset($meta['groups']) && is_array($meta['groups'])) ? $meta['groups'] : array();

$packages = array();
$sourcePackages = (isset($document) && isset($document['packages']) && is_array($document['packages'])) ? $document['packages'] : array();
foreach ($sourcePackages as $pack) {
    if (!is_array($pack) || !isset($pack['name'])) {
        continue;
    }
    $pack['location'] = '/v1/packages/' . ltrim($pack['location'], '/');
    $extra = isset($metaPackages[$pack['name']]) && is_array($metaPackages[$pack['name']]) ? $metaPackages[$pack['name']] : array();
    foreach (array('priority', 'group', 'server', 'recommendedMemory', 'serverAddress') as $field) {
        if (array_key_exists($field, $extra)) {
            $pack[$field] = $extra[$field];
        }
    }
    $packages[] = $pack;
}

$v2 = array(
    'minimumVersion' => isset($document['minimumVersion']) ? $document['minimumVersion'] : 1,
    'formatVersion' => 2,
    'groups' => (object) $metaGroups,
    'packages' => $packages,
);

header('Content-Type: application/json; charset=utf-8');
echo json_encode($v2, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
