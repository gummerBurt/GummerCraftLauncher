/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.gummer.launcher.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Онлайн сервера для картки (з 1.32): звичайний «пінг зі списку серверів» Minecraft (Server List Ping), той самий,
 * що робить гра в меню мультиплеєра. Нічого на серверах міняти не треба, працює і через TCPShield.
 * Результат кешується на хвилину, помилка дає null (картка просто без онлайну).
 */
public final class ServerPing {

    /** Онлайн і максимум гравців. */
    public static final class Status {
        public final int online;
        public final int max;

        Status(int online, int max) {
            this.online = online;
            this.max = max;
        }
    }

    // 14.11.2026: було 60 с, як і таймер опитування, тож кожен другий тік брав кеш і цифра жила ~2 хв.
    // Кеш лише щоб не пінгувати ту саму адресу двічі за один прохід (пак і картка новини з тим самим сервером).
    private static final long CACHE_MS = 15_000L;
    private static final Map<String, Object[]> cache = new ConcurrentHashMap<String, Object[]>();

    private ServerPing() {
    }

    /** Адреса «host» або «host:port». Блокує до ~5 с, тож лише не з EDT. */
    public static Status query(String address) {
        if (address == null || address.trim().isEmpty()) return null;
        Object[] cached = cache.get(address);
        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < CACHE_MS) {
            return (Status) cached[1];
        }
        Status s;
        try {
            s = ping(address.trim());
        } catch (Exception e) {
            s = null;
        }
        cache.put(address, new Object[]{System.currentTimeMillis(), s});
        return s;
    }

    static String[] split(String address) {
        int colon = address.lastIndexOf(':');
        if (colon > 0 && address.indexOf(':') == colon) {
            return new String[]{address.substring(0, colon), address.substring(colon + 1)};
        }
        return new String[]{address, "25565"};
    }

    private static Status ping(String address) throws IOException {
        String[] hp = split(address);
        String host = hp[0];
        int port = Integer.parseInt(hp[1]);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 4000);
            socket.setSoTimeout(4000);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            writeVarInt(hs, 0);            // пакет handshake
            writeVarInt(hs, 767);          // протокол (для статусу сервер приймає будь-який)
            byte[] hb = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(hs, hb.length);
            hs.write(hb);
            hs.write((port >> 8) & 0xFF);
            hs.write(port & 0xFF);
            writeVarInt(hs, 1);            // наступний стан: status
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            writeVarInt(packet, hs.size());
            hs.writeTo(packet);
            writeVarInt(packet, 1);        // довжина запиту статусу
            writeVarInt(packet, 0);        // пакет status request
            out.write(packet.toByteArray());
            out.flush();

            readVarInt(in);                // довжина пакета
            int id = readVarInt(in);
            if (id != 0) throw new IOException("Unexpected packet " + id);
            int len = readVarInt(in);
            if (len <= 0 || len > 1 << 20) throw new IOException("Bad status length " + len);
            byte[] json = new byte[len];
            in.readFully(json);
            JsonNode players = Json.newMapper().readTree(json).path("players");
            if (!players.has("online")) return null;
            return new Status(players.path("online").asInt(), players.path("max").asInt());
        }
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too long");
    }
}
