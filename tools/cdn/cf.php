<?php
/*
 * 24.11.2026: проксі CurseForge API для лаунчера (імпорт zip-збірок CurseForge, з 1.40.0).
 *
 * Де: CDN 116.202.25.246, /mnt/cdn-volume/gummercraft/cf.php (у контейнері php_web /var/www/cf.php),
 * адреса https://cdn.gummercraft.fun/cf.php (launcher.properties: curseforgeProxyUrl). Не в /launcher/ (там .php
 * віддається текстом, див. design-like.php).
 *
 * Навіщо: ключ CurseForge лише тут, у лаунчері (jar) його немає. Лаунчер питає лише дані файлів і модів за ID зі
 * збірки; самі файли гравець качає напряму з CDN CurseForge (edge.forgecdn.net), через нас іде лише JSON.
 *
 * Запит: POST, тіло JSON {"files":[fileId, ...]} (до 2000 ID, лише додатні цілі).
 * Відповідь: {"files":[{"id","modId","fileName","displayName","downloadUrl"(null, якщо автор заборонив сторонні
 * завантаження),"sha1","length"}], "mods":[{"id","name","slug","classId","allowModDistribution","url","author"}]}.
 *
 * З 1.40.0 ще сторінка «CurseForge» лаунчера (модпаки, як сторінка Modrinth):
 *   {"search":{"q","game","loader"(forge|fabric|quilt|neoforge),"category"(id),"sort"(best|new|updated),"offset"}}
 *     -> {"total","hits":[{"id","name","slug","summary","author","downloads","icon","gallery","url",...}]} (кеш 10 хв);
 *   {"packFiles":modId} -> {"files":[{"id","name","fileName","type","date","games","loaders","downloadUrl","sha1","length"}],
 *     "mod":{...}} (лише модпаки, без серверних паків, від новішого, кеш 30 хв);
 *   {"categories":true} -> {"categories":[{"id","name","slug"}]} (категорії модпаків, кеш 1 день).
 *
 * З 24.11.2026 (моди, ресурспаки, шейдери CurseForge у лаунчері, як Modrinth):
 *   "search" ще з "class" (4471 модпаки за замовчуванням, 6 моди, 12 ресурспаки, 6552 шейдери);
 *   {"categories":classId} -> категорії цього класу (лише верхній рівень);
 *   {"projectFiles":{"id","game"?,"loader"?}} -> {"files":[... "deps":[{"modId","type":required|incompatible}]],
 *     "mod":{...}, "latest":[{"game","loader","file","type"}]} (лише моди, ресурспаки, шейдери; кеш 30 хв).
 *
 * З 25.11.2026 (експорт збірки .mrpack: моди CurseForge посиланням «проект + файл», а не файлом):
 *   {"fingerprints":[uint32, ...]} (до 2000, fingerprint CurseForge: MurmurHash2 з seed 1 по байтах файлу без 9, 10,
 *     13, 32) -> {"matches":[{"fingerprint","fileId","modId"}]} лише точні збіги. Дані файлів і модів далі звичайним
 *     {"files":[...]}. Збіги кешуються на 30 днів (файл з тим самим вмістом той самий).
 *
 * З 25.11.2026 (оновлення модів і докачування з CurseForge, а не лише з Modrinth):
 *   {"modsLatest":{"ids":[modId,...],"game","loaders":[...]}} -> {"latest":[{"modId","file","type","loader"}]}:
 *     найновіші файли модів під цю гру і завантажувачі (latestFilesIndexes, кеш 1 година).
 *
 * Ключ: .secrets/curseforge.php поруч (<?php return '...';), тека з крапкою, nginx на неї 403, а через PHP файл нічого
 * не виводить. Кеш: .cfcache/ (дані файлу CurseForge не міняються, тримаємо 30 днів; мод 1 день). Обмеження: 60 запитів
 * на хвилину з однієї IP (з 1.40.0, бо пошук) (IP так само, як у design-like.php: останній X-Forwarded-For від nginx хоста).
 */

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('X-Robots-Tag: noindex');

const API = 'https://api.curseforge.com/v1';
const CACHE = __DIR__ . '/.cfcache';
const MAX_IDS = 2000;
const PER_MINUTE = 60;
const FILE_TTL = 30 * 86400;
const MOD_TTL = 86400;

function reply(int $code, array $body): void
{
    http_response_code($code);
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function client_ip(): string
{
    $ip = $_SERVER['REMOTE_ADDR'] ?? '';
    $local = !filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE);
    if ($local && !empty($_SERVER['HTTP_X_FORWARDED_FOR'])) {
        $parts = array_map('trim', explode(',', $_SERVER['HTTP_X_FORWARDED_FOR']));
        $last = end($parts);
        if (filter_var($last, FILTER_VALIDATE_IP)) $ip = $last;
    }
    if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV6)) {
        $bin = inet_pton($ip);
        $ip = inet_ntop(substr($bin, 0, 8) . str_repeat("\0", 8)) . '/64';
    }
    return $ip;
}

function rate_limit(): void
{
    $dir = CACHE . '/rl';
    if (!is_dir($dir)) @mkdir($dir, 0700, true);
    $f = $dir . '/' . hash('sha256', client_ip()) . '-' . intdiv(time(), 60);
    $fh = @fopen($f, 'c+');
    if (!$fh) return;
    flock($fh, LOCK_EX);
    $n = (int)stream_get_contents($fh) + 1;
    ftruncate($fh, 0);
    rewind($fh);
    fwrite($fh, (string)$n);
    flock($fh, LOCK_UN);
    fclose($fh);
    if ($n > PER_MINUTE) reply(429, ['error' => 'too many requests']);
    if (mt_rand(1, 50) === 1) {
        foreach (glob($dir . '/*') ?: [] as $old) {
            if (filemtime($old) < time() - 300) @unlink($old);
        }
    }
}

function api_key(): string
{
    $k = @include __DIR__ . '/.secrets/curseforge.php';
    if (!is_string($k) || $k === '') reply(503, ['error' => 'not configured']);
    return $k;
}

function cf_post(string $path, array $body): array
{
    $ch = curl_init(API . $path);
    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => json_encode($body),
        CURLOPT_HTTPHEADER => ['Content-Type: application/json', 'Accept: application/json', 'x-api-key: ' . api_key()],
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 40,
        CURLOPT_USERAGENT => 'GummerCraft-Launcher-Proxy/1.0',
    ]);
    $out = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($out === false || $code !== 200) reply(502, ['error' => 'curseforge ' . $code]);
    $j = json_decode($out, true);
    if (!is_array($j) || !isset($j['data']) || !is_array($j['data'])) reply(502, ['error' => 'curseforge bad answer']);
    return $j['data'];
}

function cache_get(string $kind, int $id, int $ttl): ?array
{
    $f = CACHE . "/$kind/" . ($id % 1000) . "/$id.json";
    if (!is_file($f) || filemtime($f) < time() - $ttl) return null;
    $j = json_decode((string)@file_get_contents($f), true);
    return is_array($j) ? $j : null;
}

function cache_put(string $kind, int $id, array $v): void
{
    $d = CACHE . "/$kind/" . ($id % 1000);
    if (!is_dir($d)) @mkdir($d, 0700, true);
    $tmp = "$d/$id.json." . getmypid();
    if (@file_put_contents($tmp, json_encode($v, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES)) !== false) {
        @rename($tmp, "$d/$id.json");
    }
}

/** @return int[] */
function ids_from($list): array
{
    if (!is_array($list) || count($list) === 0 || count($list) > MAX_IDS) reply(400, ['error' => 'bad ids']);
    $out = [];
    foreach ($list as $v) {
        if (!is_int($v) || $v <= 0) reply(400, ['error' => 'bad ids']);
        $out[$v] = true;
    }
    return array_keys($out);
}

function cf_get(string $path, array $query): array
{
    $ch = curl_init(API . $path . ($query ? '?' . http_build_query($query) : ''));
    curl_setopt_array($ch, [
        CURLOPT_HTTPHEADER => ['Accept: application/json', 'x-api-key: ' . api_key()],
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 40,
        CURLOPT_USERAGENT => 'GummerCraft-Launcher-Proxy/1.0',
    ]);
    $out = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($out === false || $code !== 200) reply(502, ['error' => 'curseforge ' . $code]);
    $j = json_decode($out, true);
    if (!is_array($j) || !array_key_exists('data', $j)) reply(502, ['error' => 'curseforge bad answer']);
    return $j;
}

/** Кеш відповіді за ключем (пошук і списки), $ttl секунд. */
function cache_key_get(string $key, int $ttl): ?array
{
    $f = CACHE . '/q/' . substr($key, 0, 2) . "/$key.json";
    if (!is_file($f) || filemtime($f) < time() - $ttl) return null;
    $j = json_decode((string)@file_get_contents($f), true);
    return is_array($j) ? $j : null;
}

function cache_key_put(string $key, array $v): void
{
    $d = CACHE . '/q/' . substr($key, 0, 2);
    if (!is_dir($d)) @mkdir($d, 0700, true);
    $tmp = "$d/$key.json." . getmypid();
    if (@file_put_contents($tmp, json_encode($v, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES)) !== false) {
        @rename($tmp, "$d/$key.json");
    }
}

const LOADERS = ['forge' => 1, 'fabric' => 4, 'quilt' => 5, 'neoforge' => 6];

function mod_short(array $m): array
{
    $author = '';
    foreach ($m['authors'] ?? [] as $a) {
        $author = (string)($a['name'] ?? '');
        break;
    }
    $gallery = null;
    foreach ($m['screenshots'] ?? [] as $sc) {
        if (!empty($sc['url'])) {
            $gallery = (string)$sc['url'];
            break;
        }
    }
    return [
        'id' => (int)$m['id'],
        'name' => (string)($m['name'] ?? ''),
        'slug' => (string)($m['slug'] ?? ''),
        'summary' => (string)($m['summary'] ?? ''),
        'author' => $author,
        'downloads' => (int)($m['downloadCount'] ?? 0),
        'icon' => isset($m['logo']['thumbnailUrl']) ? (string)$m['logo']['thumbnailUrl'] : null,
        'gallery' => $gallery,
        'url' => (string)($m['links']['websiteUrl'] ?? ''),
        'classId' => (int)($m['classId'] ?? 0),
        'allowModDistribution' => ($m['allowModDistribution'] ?? true) !== false,
    ];
}

/** 24.11.2026: класи проєктів, які показує лаунчер: модпаки, моди, ресурспаки, шейдери. */
const CLASSES = [4471, 6, 12, 6552];

/** Клас з запиту ("class"), без нього модпаки (як у першій версії сторінки CurseForge). */
function class_of(array $q): int
{
    $c = $q['class'] ?? 4471;
    if (!is_int($c) || !in_array($c, CLASSES, true)) reply(400, ['error' => 'bad class']);
    return $c;
}

function do_search(array $q): void
{
    // total: CurseForge сам рахує не більше 10000 (totalCount 10000 для модпаків, модів і ресурспаків, перевірено
    // 24.11.2026), і далі index 10000 не гортає (400). Справжньої кількості API не віддає
    $params = ['gameId' => 432, 'classId' => class_of($q), 'pageSize' => 20, 'sortOrder' => 'desc'];
    $text = trim((string)($q['q'] ?? ''));
    if ($text !== '') $params['searchFilter'] = mb_substr($text, 0, 80);
    $game = (string)($q['game'] ?? '');
    if ($game !== '' && preg_match('/^[0-9A-Za-z._-]{1,20}$/', $game)) $params['gameVersion'] = $game;
    $loader = (string)($q['loader'] ?? '');
    if (isset(LOADERS[$loader])) $params['modLoaderType'] = LOADERS[$loader];
    $cat = $q['category'] ?? null;
    if (is_int($cat) && $cat > 0) $params['categoryId'] = $cat;
    $sort = (string)($q['sort'] ?? 'best');
    $params['sortField'] = $sort === 'new' ? 11 : ($sort === 'updated' ? 3 : 2);
    $offset = $q['offset'] ?? 0;
    if (!is_int($offset) || $offset < 0 || $offset > 9980) reply(400, ['error' => 'bad offset']);
    $params['index'] = $offset;
    $key = hash('sha256', 'search|' . json_encode($params));
    $c = cache_key_get($key, 600);
    if ($c !== null) reply(200, $c);
    $j = cf_get('/mods/search', $params);
    $hits = [];
    foreach ($j['data'] as $m) $hits[] = mod_short($m);
    $out = ['total' => (int)min(10000, $j['pagination']['totalCount'] ?? count($hits)), 'hits' => $hits];
    cache_key_put($key, $out);
    reply(200, $out);
}

function do_pack_files(int $modId): void
{
    $key = hash('sha256', "packFiles|$modId");
    $c = cache_key_get($key, 1800);
    if ($c !== null) reply(200, $c);
    $mod = cf_get("/mods/$modId", [])['data'];
    if ((int)($mod['classId'] ?? 0) !== 4471) reply(400, ['error' => 'not a modpack']);
    $files = [];
    for ($index = 0; $index < 400; $index += 50) {
        $j = cf_get("/mods/$modId/files", ['index' => $index, 'pageSize' => 50]);
        foreach ($j['data'] as $f) {
            if (!empty($f['isServerPack'])) continue;
            $sha1 = null;
            foreach ($f['hashes'] ?? [] as $h) {
                if (($h['algo'] ?? 0) === 1) $sha1 = strtolower((string)$h['value']);
            }
            $games = [];
            $loaders = [];
            foreach ($f['gameVersions'] ?? [] as $g) {
                $l = strtolower((string)$g);
                if (isset(LOADERS[$l])) $loaders[] = $l;
                elseif (preg_match('/^[0-9]+(\.[0-9]+)+$/', (string)$g)) $games[] = (string)$g;
            }
            $files[] = [
                'id' => (int)$f['id'],
                'name' => (string)($f['displayName'] ?? ''),
                'fileName' => (string)($f['fileName'] ?? ''),
                'type' => [1 => 'release', 2 => 'beta', 3 => 'alpha'][(int)($f['releaseType'] ?? 1)] ?? 'release',
                'date' => (string)($f['fileDate'] ?? ''),
                'games' => $games,
                'loaders' => $loaders,
                'downloadUrl' => isset($f['downloadUrl']) && is_string($f['downloadUrl']) ? $f['downloadUrl'] : null,
                'sha1' => $sha1,
                'length' => (int)($f['fileLength'] ?? 0),
            ];
        }
        if (count($j['data']) < 50) break;
    }
    usort($files, fn($a, $b) => strcmp($b['date'], $a['date']));
    $out = ['files' => $files, 'mod' => mod_short($mod)];
    cache_key_put($key, $out);
    reply(200, $out);
}

/** Номери завантажувачів CurseForge (modLoader у latestFilesIndexes) -> назва. */
const LOADER_NAMES = [1 => 'forge', 4 => 'fabric', 5 => 'quilt', 6 => 'neoforge'];

/** Файл CurseForge -> те, що потрібно лаунчеру (і залежності: required, incompatible). */
function file_short(array $f): array
{
    $sha1 = null;
    foreach ($f['hashes'] ?? [] as $h) {
        if (($h['algo'] ?? 0) === 1) $sha1 = strtolower((string)$h['value']);
    }
    $games = [];
    $loaders = [];
    foreach ($f['gameVersions'] ?? [] as $g) {
        $l = strtolower((string)$g);
        if (isset(LOADERS[$l])) $loaders[] = $l;
        elseif (preg_match('/^[0-9]+(\.[0-9]+)+$/', (string)$g)) $games[] = (string)$g;
    }
    $deps = [];
    foreach ($f['dependencies'] ?? [] as $d) {
        $t = (int)($d['relationType'] ?? 0);
        if (($t === 3 || $t === 5) && (int)($d['modId'] ?? 0) > 0) {
            $deps[] = ['modId' => (int)$d['modId'], 'type' => $t === 3 ? 'required' : 'incompatible'];
        }
    }
    return [
        'id' => (int)$f['id'],
        'name' => (string)($f['displayName'] ?? ''),
        'fileName' => (string)($f['fileName'] ?? ''),
        'type' => [1 => 'release', 2 => 'beta', 3 => 'alpha'][(int)($f['releaseType'] ?? 1)] ?? 'release',
        'date' => (string)($f['fileDate'] ?? ''),
        'games' => $games,
        'loaders' => $loaders,
        'downloadUrl' => isset($f['downloadUrl']) && is_string($f['downloadUrl']) ? $f['downloadUrl'] : null,
        'sha1' => $sha1,
        'length' => (int)($f['fileLength'] ?? 0),
        'deps' => $deps,
    ];
}

/**
 * 24.11.2026: файли мода, ресурспака чи шейдера (не модпака) під гру і завантажувач, від новішого, до 200, і
 * "latest" (найновіший файл на кожну пару гра+завантажувач з latestFilesIndexes: з нього видно, у які збірки мод
 * підходить). Без "game" лише latest і mod, файлів не тягнемо.
 */
function do_project_files(array $q): void
{
    $id = $q['id'] ?? 0;
    if (!is_int($id) || $id <= 0) reply(400, ['error' => 'bad mod id']);
    $game = (string)($q['game'] ?? '');
    if ($game !== '' && !preg_match('/^[0-9A-Za-z._-]{1,20}$/', $game)) reply(400, ['error' => 'bad game']);
    $loader = (string)($q['loader'] ?? '');
    if ($loader !== '' && !isset(LOADERS[$loader])) reply(400, ['error' => 'bad loader']);
    $key = hash('sha256', "projectFiles|$id|$game|$loader");
    $c = cache_key_get($key, 1800);
    if ($c !== null) reply(200, $c);
    $mod = cf_get("/mods/$id", [])['data'];
    $cls = (int)($mod['classId'] ?? 0);
    if (!in_array($cls, [6, 12, 6552], true)) reply(400, ['error' => 'not a mod']);
    $latest = [];
    foreach ($mod['latestFilesIndexes'] ?? [] as $x) {
        $latest[] = [
            'game' => (string)($x['gameVersion'] ?? ''),
            'loader' => LOADER_NAMES[(int)($x['modLoader'] ?? 0)] ?? null,
            'file' => (int)($x['fileId'] ?? 0),
            'type' => [1 => 'release', 2 => 'beta', 3 => 'alpha'][(int)($x['releaseType'] ?? 1)] ?? 'release',
        ];
    }
    $files = [];
    if ($game !== '') {
        $params = ['gameVersion' => $game, 'pageSize' => 50];
        if ($loader !== '') $params['modLoaderType'] = LOADERS[$loader];
        for ($index = 0; $index < 200; $index += 50) {
            $params['index'] = $index;
            $j = cf_get("/mods/$id/files", $params);
            foreach ($j['data'] as $f) {
                if (!empty($f['isServerPack'])) continue;
                $files[] = file_short($f);
            }
            if (count($j['data']) < 50) break;
        }
        usort($files, fn($a, $b) => strcmp($b['date'], $a['date']));
    }
    $out = ['files' => $files, 'mod' => mod_short($mod), 'latest' => $latest];
    cache_key_put($key, $out);
    reply(200, $out);
}

function do_categories(int $cls): void
{
    $key = hash('sha256', "categories|$cls");
    $c = cache_key_get($key, 86400);
    if ($c !== null) reply(200, $c);
    $out = [];
    foreach (cf_get('/categories', ['gameId' => 432, 'classId' => $cls])['data'] as $cat) {
        if (!empty($cat['isClass'])) continue;
        // у модів категорії вкладені (Technology > Energy): лише верхній рівень, як у фільтрі сайту
        if ((int)($cat['parentCategoryId'] ?? $cls) !== $cls) continue;
        $out[] = ['id' => (int)$cat['id'], 'name' => (string)($cat['name'] ?? ''), 'slug' => (string)($cat['slug'] ?? '')];
    }
    usort($out, fn($a, $b) => strcasecmp($a['name'], $b['name']));
    $res = ['categories' => $out];
    cache_key_put($key, $res);
    reply(200, $res);
}

/** 25.11.2026: файли CurseForge за fingerprint (лише точні збіги). */
function do_fingerprints($list): void
{
    if (!is_array($list) || count($list) === 0 || count($list) > MAX_IDS) reply(400, ['error' => 'bad fingerprints']);
    $want = [];
    foreach ($list as $v) {
        if (!is_int($v) || $v < 0 || $v > 4294967295) reply(400, ['error' => 'bad fingerprints']);
        $want[$v] = true;
    }
    $matches = [];
    $miss = [];
    foreach (array_keys($want) as $fp) {
        $c = cache_key_get(hash('sha256', "fp|$fp"), FILE_TTL);
        if ($c !== null) $matches[] = $c; else $miss[] = $fp;
    }
    foreach (array_chunk($miss, 500) as $chunk) {
        $data = cf_post('/fingerprints/432', ['fingerprints' => $chunk]);
        foreach ($data['exactMatches'] ?? [] as $m) {
            $f = $m['file'] ?? null;
            if (!is_array($f)) continue;
            $fp = (int)($f['fileFingerprint'] ?? -1);
            if (!isset($want[$fp])) continue;
            $v = ['fingerprint' => $fp, 'fileId' => (int)($f['id'] ?? 0), 'modId' => (int)($f['modId'] ?? 0)];
            if ($v['fileId'] <= 0) continue;
            $matches[] = $v;
            cache_key_put(hash('sha256', "fp|$fp"), $v);
        }
    }
    reply(200, ['matches' => $matches]);
}

/**
 * 25.11.2026 (оновлення модів з CurseForge, як з Modrinth): найновіші файли модів під цю гру одним запитом на весь
 * список ({@code latestFilesIndexes} CurseForge: найновіший файл на кожну пару гра + завантажувач і тип релізу).
 * Запит {"modsLatest":{"ids":[modId,...],"game":"1.21.1","loaders":["fabric",...]}} (до 2000 id, до 4 завантажувачів)
 * -> {"latest":[{"modId","file","type","loader"}]} лише для цієї гри і цих завантажувачів. Кеш на мод 1 година.
 */
function do_mods_latest(array $q): void
{
    $ids = ids_from($q['ids'] ?? null);
    $game = (string)($q['game'] ?? '');
    if (!preg_match('/^[0-9A-Za-z._-]{1,20}$/', $game)) reply(400, ['error' => 'bad game']);
    $loaders = $q['loaders'] ?? [];
    if (!is_array($loaders) || count($loaders) === 0 || count($loaders) > 4) reply(400, ['error' => 'bad loaders']);
    $want = [];
    foreach ($loaders as $l) {
        if (!is_string($l) || !isset(LOADERS[$l])) reply(400, ['error' => 'bad loader']);
        $want[LOADERS[$l]] = $l;
    }
    $index = [];
    $miss = [];
    foreach ($ids as $id) {
        $c = cache_key_get(hash('sha256', "latest|$id"), 3600);
        if ($c !== null) $index[$id] = $c; else $miss[] = $id;
    }
    foreach (array_chunk($miss, 500) as $chunk) {
        $got = [];
        foreach (cf_post('/mods', ['modIds' => $chunk]) as $m) {
            $id = (int)($m['id'] ?? 0);
            if ($id <= 0) continue;
            $x = [];
            foreach ($m['latestFilesIndexes'] ?? [] as $i) {
                $x[] = ['g' => (string)($i['gameVersion'] ?? ''), 'l' => (int)($i['modLoader'] ?? 0),
                    'f' => (int)($i['fileId'] ?? 0), 't' => (int)($i['releaseType'] ?? 1)];
            }
            $v = ['x' => $x];
            $index[$id] = $v;
            $got[$id] = true;
            cache_key_put(hash('sha256', "latest|$id"), $v);
        }
        foreach ($chunk as $id) {
            if (!isset($got[$id])) cache_key_put(hash('sha256', "latest|$id"), $index[$id] = ['x' => []]);
        }
    }
    $out = [];
    foreach ($index as $id => $v) {
        foreach ($v['x'] as $i) {
            if ($i['g'] !== $game || !isset($want[$i['l']]) || $i['f'] <= 0) continue;
            $out[] = ['modId' => (int)$id, 'file' => $i['f'],
                'type' => [1 => 'release', 2 => 'beta', 3 => 'alpha'][$i['t']] ?? 'release', 'loader' => $want[$i['l']]];
        }
    }
    reply(200, ['latest' => $out]);
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') reply(405, ['error' => 'POST only']);
$raw = file_get_contents('php://input', false, null, 0, 200000);
$req = json_decode((string)$raw, true);
if (!is_array($req)) reply(400, ['error' => 'bad json']);
rate_limit();

if (isset($req['search']) && is_array($req['search'])) do_search($req['search']);
if (isset($req['packFiles'])) {
    if (!is_int($req['packFiles']) || $req['packFiles'] <= 0) reply(400, ['error' => 'bad mod id']);
    do_pack_files($req['packFiles']);
}
if (isset($req['projectFiles']) && is_array($req['projectFiles'])) do_project_files($req['projectFiles']);
if (isset($req['fingerprints'])) do_fingerprints($req['fingerprints']);
if (isset($req['modsLatest']) && is_array($req['modsLatest'])) do_mods_latest($req['modsLatest']);
if (isset($req['categories'])) do_categories(is_int($req['categories']) ? class_of(['class' => $req['categories']]) : 4471);

$fileIds = ids_from($req['files'] ?? null);

$files = [];
$miss = [];
foreach ($fileIds as $id) {
    $c = cache_get('file', $id, FILE_TTL);
    if ($c !== null) $files[$id] = $c; else $miss[] = $id;
}
foreach (array_chunk($miss, 500) as $chunk) {
    foreach (cf_post('/mods/files', ['fileIds' => $chunk]) as $f) {
        $sha1 = null;
        foreach ($f['hashes'] ?? [] as $h) {
            if (($h['algo'] ?? 0) === 1) $sha1 = strtolower((string)$h['value']);
        }
        $v = [
            'id' => (int)$f['id'],
            'modId' => (int)($f['modId'] ?? 0),
            'fileName' => (string)($f['fileName'] ?? ''),
            'displayName' => (string)($f['displayName'] ?? ''),
            'downloadUrl' => isset($f['downloadUrl']) && is_string($f['downloadUrl']) ? $f['downloadUrl'] : null,
            'sha1' => $sha1,
            'length' => (int)($f['fileLength'] ?? 0),
        ];
        $files[$v['id']] = $v;
        cache_put('file', $v['id'], $v);
    }
}

$modIds = [];
foreach ($files as $f) if ($f['modId'] > 0) $modIds[$f['modId']] = true;
$mods = [];
$miss = [];
foreach (array_keys($modIds) as $id) {
    $c = cache_get('mod', $id, MOD_TTL);
    if ($c !== null) $mods[$id] = $c; else $miss[] = $id;
}
foreach (array_chunk($miss, 500) as $chunk) {
    foreach (cf_post('/mods', ['modIds' => $chunk]) as $m) {
        $author = '';
        foreach ($m['authors'] ?? [] as $a) {
            $author = (string)($a['name'] ?? '');
            break;
        }
        $v = [
            'id' => (int)$m['id'],
            'name' => (string)($m['name'] ?? ''),
            'slug' => (string)($m['slug'] ?? ''),
            'classId' => (int)($m['classId'] ?? 0),
            'allowModDistribution' => ($m['allowModDistribution'] ?? true) !== false,
            'url' => (string)($m['links']['websiteUrl'] ?? ''),
            'author' => $author,
        ];
        $mods[$v['id']] = $v;
        cache_put('mod', $v['id'], $v);
    }
}

reply(200, ['files' => array_values($files), 'mods' => array_values($mods)]);
