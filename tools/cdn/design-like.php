<?php
/*
 * 16.11.2026: лайки виглядів лаунчера (Liquid Glass, Класика, Бібліотека). Один лайк з однієї IP-адреси на вигляд.
 *
 * Де: CDN 116.202.25.246, /mnt/cdn-volume/gummercraft/design-like.php (у контейнері php_web /var/www/design-like.php),
 * адреса https://cdn.gummercraft.fun/design-like.php (launcher.properties: designLikeUrl).
 * НЕ класти в /launcher/: там nginx-правило "location ^~ /launcher/" віддає файли як статику, і .php пішов би текстом.
 *
 * Запити (лаунчер шле POST, Cloudflare POST не кешує):
 *   look=glass|classic|library            -> {"look":..,"count":N,"liked":true|false}
 *   look=..&set=1 / set=0                 -> поставити / забрати лайк, відповідь та сама
 *   all=1                                 -> {"glass":N,"classic":N,"library":N}
 *
 * IP: nginx хоста бере справжню адресу з CF-Connecting-IP (лише від мереж Cloudflare, conf.d/00-cloudflare.conf) і дописує
 * її останньою в X-Forwarded-For; php_web слухає 8181 лише для nginx хоста (INPUT DROP). Тому довіряємо ОСТАННЬОМУ запису
 * X-Forwarded-For, і лише коли запит прийшов з локальної адреси. IPv6 рахується по /64 (одна людина зазвичай має цілу /64).
 *
 * Дані: design-like.data.php поруч (перший рядок <?php exit; ?>, тож через веб нічого не видно). Зберігаються лише
 * солоні sha256 від IP (сіль генерується при першому запуску і лежить там же), самих IP немає.
 */

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('X-Robots-Tag: noindex');

const LOOKS = ['glass', 'classic', 'library'];
const DATA = __DIR__ . '/design-like.data.php';
const GUARD = "<?php exit; ?>\n";

function reply(int $code, array $body): void
{
    http_response_code($code);
    echo json_encode($body, JSON_UNESCAPED_UNICODE);
    exit;
}

function client_ip(): string
{
    $ip = $_SERVER['REMOTE_ADDR'] ?? '';
    $local = !filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE);
    if ($local && !empty($_SERVER['HTTP_X_FORWARDED_FOR'])) {
        $parts = array_map('trim', explode(',', $_SERVER['HTTP_X_FORWARDED_FOR']));
        $last = end($parts);
        if (filter_var($last, FILTER_VALIDATE_IP)) {
            $ip = $last;
        }
    }
    if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV6)) {
        $bin = inet_pton($ip);
        $ip = inet_ntop(substr($bin, 0, 8) . str_repeat("\0", 8)) . '/64';
    }
    return $ip;
}

function load($fh): array
{
    rewind($fh);
    $raw = stream_get_contents($fh);
    if (strpos($raw, GUARD) === 0) {
        $raw = substr($raw, strlen(GUARD));
    }
    $d = json_decode($raw, true);
    if (!is_array($d)) {
        $d = [];
    }
    if (empty($d['salt'])) {
        $d['salt'] = bin2hex(random_bytes(16));
    }
    if (!isset($d['looks']) || !is_array($d['looks'])) {
        $d['looks'] = [];
    }
    foreach (LOOKS as $l) {
        if (!isset($d['looks'][$l]) || !is_array($d['looks'][$l])) {
            $d['looks'][$l] = [];
        }
    }
    return $d;
}

function save($fh, array $d): void
{
    rewind($fh);
    ftruncate($fh, 0);
    fwrite($fh, GUARD . json_encode($d, JSON_UNESCAPED_UNICODE));
    fflush($fh);
}

$in = $_POST + $_GET;
$set = isset($in['set']) ? (string)$in['set'] : null;
if ($set !== null && $_SERVER['REQUEST_METHOD'] !== 'POST') {
    reply(405, ['error' => 'POST only']);
}

$fh = fopen(DATA, 'c+');
if ($fh === false) {
    reply(500, ['error' => 'storage']);
}
flock($fh, $set === null ? LOCK_SH : LOCK_EX);
$d = load($fh);

if (!empty($in['all'])) {
    $out = [];
    foreach (LOOKS as $l) {
        $out[$l] = count($d['looks'][$l]);
    }
    flock($fh, LOCK_UN);
    reply(200, $out);
}

$look = (string)($in['look'] ?? '');
if (!in_array($look, LOOKS, true)) {
    flock($fh, LOCK_UN);
    reply(400, ['error' => 'look']);
}

$id = substr(hash('sha256', $d['salt'] . '|' . client_ip()), 0, 32);
$fresh = filesize(DATA) === 0;

if ($set === '1') {
    $d['looks'][$look][$id] = time();
} elseif ($set === '0') {
    unset($d['looks'][$look][$id]);
}
if ($set === '1' || $set === '0' || $fresh) {
    if ($fresh && $set === null) { // перший запуск: сіль треба зберегти, а лок спільний
        flock($fh, LOCK_EX);
    }
    save($fh, $d);
}
flock($fh, LOCK_UN);
fclose($fh);

reply(200, [
    'look' => $look,
    'count' => count($d['looks'][$look]),
    'liked' => isset($d['looks'][$look][$id]),
]);
