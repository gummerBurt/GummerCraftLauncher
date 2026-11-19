/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import java.util.List;

/**
 * 19.11.2026 (власник: «Мод не під ту версію Minecraft. -- давай, але мене трішки лякає "виняток тільки моди, де діапазон
 * версій записано криво"»). Чи приймає умова версії з опису мода дану версію. Правила ті самі, що в завантажувачів:
 * Fabric (умови {@code depends}, «1.20.x», «~», «^», «>=a <b», масив = будь-яка з умов) і Maven-діапазони Forge і
 * NeoForge («[1.20.1]», «[1.20,1.21)», кілька через кому; версія без дужок лише рекомендація, її Forge не перевіряє).
 * <p>
 * Відповідь: {@code TRUE}, {@code FALSE} або {@code null} («не знаю»). {@code FALSE} лише тоді, коли умову розібрано
 * повністю і вона точно не виконується: так само її відкине сам завантажувач і гра не запуститься. Усе незвичне (снапшоти,
 * літери у версії, шаблони «${...}») дає {@code null}, і перевірка перед запуском такий мод не чіпає.
 */
public final class McRange {

    private McRange() {
    }

    /** Версія лише з чисел через крапку («1.20.1»), інакше null. */
    public static int[] parse(String v) {
        if (v == null) return null;
        v = v.trim();
        if (!v.matches("[0-9]+(\\.[0-9]+)*")) return null;
        String[] p = v.split("\\.");
        int[] out = new int[p.length];
        try {
            for (int i = 0; i < p.length; i++) out[i] = Integer.parseInt(p[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    /** Порівняння з доповненням нулями: 1.20 == 1.20.0. */
    public static int cmp(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ Fabric

    /** Fabric: будь-яка з умов (масив у fabric.mod.json або одна умова). */
    static Boolean fabric(List<String> any, String version) {
        int[] v = parse(version);
        if (v == null || any == null || any.isEmpty()) return null;
        boolean unknown = false;
        for (String one : any) {
            Boolean r = fabricAll(one, v);
            if (r == null) unknown = true;
            else if (r) return Boolean.TRUE;
        }
        return unknown ? null : Boolean.FALSE;
    }

    /** Одна умова Fabric: частини через пробіл, усі мають виконатись. */
    static Boolean fabricAll(String expr, int[] v) {
        if (expr == null) return null;
        expr = expr.trim();
        if (expr.isEmpty() || expr.equals("*")) return Boolean.TRUE;
        if (expr.contains("${")) return null;
        boolean unknown = false;
        for (String part : expr.split("\\s+")) {
            Boolean r = fabricPart(part, v);
            if (r == null) unknown = true;
            else if (!r) return Boolean.FALSE;
        }
        return unknown ? null : Boolean.TRUE;
    }

    private static Boolean fabricPart(String part, int[] v) {
        String op, ver;
        if (part.startsWith(">=") || part.startsWith("<=")) {
            op = part.substring(0, 2);
            ver = part.substring(2);
        } else if ("><=~^".indexOf(part.charAt(0)) >= 0) {
            op = part.substring(0, 1);
            ver = part.substring(1);
        } else {
            op = "=";
            ver = part;
        }
        ver = ver.trim();
        if (ver.isEmpty() || ver.equals("*") || ver.equalsIgnoreCase("x")) return Boolean.TRUE;
        // «>=1.21-» і «>=1.21-alpha.24.10.a»: так моди пускають і снапшоти. Для релізної версії гри нижня межа з
        // передрелізом та сама, що й без нього (1.21 > 1.21-alpha, а 1.20.6 менше за обидві); верхня «<1.21-» це «<1.21»
        int dash = ver.indexOf('-');
        if (dash >= 0) {
            if (op.equals("=") || op.equals("~") || op.equals("^")) return null;
            ver = ver.substring(0, dash);
            if (op.equals(">")) op = ">=";
            else if (op.equals("<=")) op = "<";
        }
        int plus = ver.indexOf('+'); // метадані збірки Fabric не порівнює
        if (plus >= 0) ver = ver.substring(0, plus);
        String[] comps = ver.split("\\.", -1);
        int wild = -1;
        int[] p = new int[comps.length];
        for (int i = 0; i < comps.length; i++) {
            String c = comps[i];
            if (c.equals("x") || c.equals("X") || c.equals("*")) {
                wild = i;
                break;
            }
            if (!c.matches("[0-9]+")) return null;
            try {
                p[i] = Integer.parseInt(c);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (wild >= 0) {
            // «1.20.x»: збігаються всі частини до x
            if (!op.equals("=") || wild == 0) return null;
            for (int i = 0; i < wild; i++) if ((i < v.length ? v[i] : 0) != p[i]) return Boolean.FALSE;
            return Boolean.TRUE;
        }
        int c = cmp(v, p);
        switch (op) {
            case "=":
                return c == 0;
            case ">=":
                return c >= 0;
            case ">":
                return c > 0;
            case "<=":
                return c <= 0;
            case "<":
                return c < 0;
            case "~": { // ~1.20.1: >=1.20.1 <1.21; ~1: >=1 <2
                int[] up = p.length >= 2 ? new int[]{p[0], p[1] + 1} : new int[]{p[0] + 1};
                return c >= 0 && cmp(v, up) < 0;
            }
            case "^": // та сама головна версія
                return c >= 0 && cmp(v, new int[]{p[0] + 1}) < 0;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ Forge / NeoForge (Maven)

    /** Maven-діапазон Forge і NeoForge. */
    static Boolean maven(String spec, String version) {
        int[] v = parse(version);
        if (v == null || spec == null) return null;
        spec = spec.trim();
        if (spec.isEmpty() || spec.equals("*")) return Boolean.TRUE;
        if (spec.contains("${")) return null;
        if (!(spec.startsWith("[") || spec.startsWith("("))) return Boolean.TRUE; // лише рекомендація, Forge її не перевіряє
        boolean unknown = false;
        int i = 0;
        while (i < spec.length()) {
            char c = spec.charAt(i);
            if (c == ',' || c == ' ') {
                i++;
                continue;
            }
            if (c != '[' && c != '(') return null;
            int end = -1;
            for (int k = i + 1; k < spec.length(); k++) {
                char e = spec.charAt(k);
                if (e == ']' || e == ')') {
                    end = k;
                    break;
                }
            }
            if (end < 0) return null;
            Boolean r = restriction(spec.substring(i + 1, end), c == '[', spec.charAt(end) == ']', v);
            if (r == null) unknown = true;
            else if (r) return Boolean.TRUE;
            i = end + 1;
        }
        return unknown ? null : Boolean.FALSE;
    }

    private static Boolean restriction(String inner, boolean lowInc, boolean upInc, int[] v) {
        int comma = inner.indexOf(',');
        if (comma < 0) {
            int[] exact = parse(inner);
            if (exact == null || !lowInc || !upInc) return null;
            return cmp(v, exact) == 0;
        }
        String lo = inner.substring(0, comma).trim(), hi = inner.substring(comma + 1).trim();
        if (hi.indexOf(',') >= 0) return null;
        if (!lo.isEmpty()) {
            int[] l = parse(lo);
            if (l == null) return null;
            int c = cmp(v, l);
            if (c < 0 || (c == 0 && !lowInc)) return Boolean.FALSE;
        }
        if (!hi.isEmpty()) {
            int[] h = parse(hi);
            if (h == null) return null;
            int c = cmp(v, h);
            if (c > 0 || (c == 0 && !upInc)) return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    /** Версія мода для порівняння з умовою «несумісний з версією ...»: без «+метаданих»; з літерами null. */
    static String plainModVersion(String v) {
        if (v == null) return null;
        int plus = v.indexOf('+');
        if (plus >= 0) v = v.substring(0, plus);
        return parse(v) == null ? null : v.trim();
    }
}
