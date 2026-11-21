/*
 * GummerCraft Launcher
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 21.11.2026: з'єднання з клієнтом Discord на цьому ПК (локальний IPC, без мережі і без бібліотек).
 * Кадр: код операції (int LE), довжина (int LE), JSON у UTF-8. Windows: іменований канал
 * {@code \\.\pipe\discord-ipc-N}, працює і на Java 8. Linux і Mac: Unix-сокет {@code discord-ipc-N}
 * у тимчасовій теці (звичайний Discord, Flatpak, Snap); Unix-сокети в Java лише з 16, тому через
 * рефлексію, а на старшій Java статусу просто немає.
 */
final class DiscordIpc implements Closeable {

    private static final int OP_HANDSHAKE = 0, OP_FRAME = 1, OP_CLOSE = 2;
    private static final ObjectMapper JSON = new ObjectMapper();

    private interface Pipe extends Closeable {
        void write(byte[] b) throws IOException;
        void readFully(byte[] b) throws IOException;
    }

    private final Pipe pipe;

    private DiscordIpc(Pipe pipe) {
        this.pipe = pipe;
    }

    /** Знайти запущений Discord і привітатись; null, якщо Discord не запущено (чи Java не вміє Unix-сокети). */
    static DiscordIpc connect(String clientId) {
        for (int i = 0; i < 10; i++) {
            for (Pipe p : candidates(i)) {
                DiscordIpc ipc = new DiscordIpc(p);
                try {
                    ipc.send(OP_HANDSHAKE, "{\"v\":1,\"client_id\":" + JSON.writeValueAsString(clientId) + "}");
                    JsonNode ready = ipc.read();
                    if (ready != null && "READY".equals(ready.path("evt").asText())) return ipc;
                } catch (IOException ignored) {
                }
                ipc.close();
            }
        }
        return null;
    }

    private static List<Pipe> candidates(int n) {
        List<Pipe> out = new ArrayList<>();
        String name = "discord-ipc-" + n;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                final RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\" + name, "rw");
                out.add(new Pipe() {
                    public void write(byte[] b) throws IOException { f.write(b); }
                    public void readFully(byte[] b) throws IOException { f.readFully(b); }
                    public void close() throws IOException { f.close(); }
                });
            } catch (IOException ignored) {
            }
            return out;
        }
        List<String> roots = new ArrayList<>();
        for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(env);
            if (v != null && !v.isEmpty() && !roots.contains(v)) roots.add(v);
        }
        roots.add("/tmp");
        for (String root : roots) {
            for (String sub : new String[]{"", "app/com.discordapp.Discord/", "snap.discord/", ".flatpak/dev.vencord.Vesktop/xdg-run/"}) {
                File f = new File(root, sub + name);
                if (!f.exists()) continue;
                Pipe p = unixSocket(f.getPath());
                if (p != null) out.add(p);
            }
        }
        return out;
    }

    /** Unix-сокет через API Java 16+ (рефлексія, бо лаунчер збирається під Java 8). */
    private static Pipe unixSocket(String path) {
        try {
            Class<?> addrClass = Class.forName("java.net.UnixDomainSocketAddress");
            SocketAddress addr = (SocketAddress) addrClass.getMethod("of", String.class).invoke(null, path);
            Class<?> pf = Class.forName("java.net.StandardProtocolFamily");
            Object unix = pf.getMethod("valueOf", String.class).invoke(null, "UNIX");
            Method open = SocketChannel.class.getMethod("open", Class.forName("java.net.ProtocolFamily"));
            final SocketChannel ch = (SocketChannel) open.invoke(null, unix);
            ch.connect(addr);
            return new Pipe() {
                public void write(byte[] b) throws IOException {
                    ByteBuffer buf = ByteBuffer.wrap(b);
                    while (buf.hasRemaining()) ch.write(buf);
                }
                public void readFully(byte[] b) throws IOException {
                    ByteBuffer buf = ByteBuffer.wrap(b);
                    while (buf.hasRemaining()) {
                        if (ch.read(buf) < 0) throw new IOException("Discord closed the socket");
                    }
                }
                public void close() throws IOException { ch.close(); }
            };
        } catch (Throwable e) {
            return null; // Java до 16 або сокет не відповідає
        }
    }

    /** Надіслати команду і прочитати відповідь (Discord відповідає на кожну, інакше канал колись переповниться). */
    JsonNode command(String json) throws IOException {
        send(OP_FRAME, json);
        JsonNode reply = read();
        if (reply == null) throw new IOException("Discord closed the connection");
        return reply;
    }

    private void send(int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(body.length).put(body);
        pipe.write(buf.array());
    }

    /** Наступний кадр; null, якщо Discord закрив з'єднання. */
    private JsonNode read() throws IOException {
        byte[] head = new byte[8];
        pipe.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int op = h.getInt();
        int len = h.getInt();
        if (len < 0 || len > 1 << 20) throw new IOException("Bad Discord frame length " + len);
        byte[] body = new byte[len];
        pipe.readFully(body);
        if (op == OP_CLOSE) return null;
        return JSON.readTree(body);
    }

    @Override
    public void close() {
        try {
            pipe.close();
        } catch (IOException ignored) {
        }
    }
}
