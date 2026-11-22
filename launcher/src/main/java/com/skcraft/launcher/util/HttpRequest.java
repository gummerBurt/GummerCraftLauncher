/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skcraft.concurrency.ProgressObservable;
import lombok.Data;
import lombok.Getter;
import lombok.extern.java.Log;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import com.gummer.launcher.shared.Toolbox;
import com.gummer.launcher.shared.TrustedTls;
import java.util.*;

import static com.gummer.launcher.shared.HttpUtils.reformat;
import static com.skcraft.launcher.LauncherUtils.checkInterrupted;
import static org.apache.commons.io.IOUtils.closeQuietly;

/**
 * A simple fluent interface for performing HTTP requests that uses
 * {@link java.net.HttpURLConnection} or {@link javax.net.ssl.HttpsURLConnection}.
 */
@Log
public class HttpRequest implements Closeable, ProgressObservable {

    // 1.32.2: було 10 хв читання і без таймауту з'єднання: файл, на якому зв'язок завис, тримав потік завантаження
    // до 10 хв на кожну спробу (у вікні «0,0 МБ/с»). Тепер зависле з'єднання рветься і файл качається заново.
    private static final int CONNECT_TIMEOUT = 1000 * 20;
    private static final int READ_TIMEOUT = 1000 * 60;
    private static final int READ_BUFFER_SIZE = 1024 * 8;

    static {
        // 22.11.2026: пул з'єднань keep-alive (див. close()): 16 на сервер, бо завантажувачі качають у 6+ потоків
        if (System.getProperty("http.maxConnections") == null) System.setProperty("http.maxConnections", "16");
    }

    private final ObjectMapper mapper = com.gummer.launcher.util.Json.newMapper();
    private final Map<String, String> headers = new HashMap<String, String>();
    private String method;
    @Getter
    private final URL url;
    private String contentType;
    private byte[] body;
    private HttpURLConnection conn;
    private InputStream inputStream;
    private int redirectCount;

    private PartialDownloadInfo resumeInfo = null;
    private long contentLength = -1;
    private long readBytes = 0;
    /** 22.11.2026: тіло відповіді прочитане до кінця, тож з'єднання можна віддати назад для наступних запитів. */
    private boolean fullyRead;

    /**
     * Create a new HTTP request.
     *
     * @param method the method
     * @param url    the URL
     */
    private HttpRequest(String method, URL url) {
        this.method = method;
        this.url = url;
    }

    /**
     * Set the content body to a JSON object with the content type of "application/json".
     *
     * @param object the object to serialize as JSON
     * @return this object
     * @throws java.io.IOException if the object can't be mapped
     */
    public HttpRequest bodyJson(Object object) throws IOException {
        contentType = "application/json";
        body = mapper.writeValueAsBytes(object);
        return this;
    }

    /**
     * Submit form data.
     *
     * @param form the form
     * @return this object
     */
    public HttpRequest bodyForm(Form form) {
        contentType = "application/x-www-form-urlencoded";
        body = form.toString().getBytes();
        return this;
    }

    /**
     * Add a header.
     *
     * @param key   the header key
     * @param value the header value
     * @return this object
     */
    private static final ThreadLocal<Boolean> CACHE_ONLY = new ThreadLocal<Boolean>();

    /**
     * 16.11.2026: у цьому потоці кожен запит одразу падає з IOException, тож код, що має запасну копію на диску
     * (списки версій, packages_cache.json), бере її без мережі. Знімати в finally.
     */
    public static void setCacheOnly(boolean on) {
        if (on) CACHE_ONLY.set(Boolean.TRUE);
        else CACHE_ONLY.remove();
    }

    public HttpRequest header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    /**
     * Execute the request.
     * <p/>
     * After execution, {@link #close()} should be called.
     *
     * @return this object
     * @throws java.io.IOException on I/O error
     */
    public HttpRequest execute() throws IOException {
        if (Boolean.TRUE.equals(CACHE_ONLY.get())) {
            // 16.11.2026: швидкий прохід списку збірок лише з кешів на диску (InstanceList.createEnumerator(true))
            throw new IOException("Cache-only pass, network skipped: " + url);
        }
        boolean successful = false;

        try {
            if (conn != null) {
                throw new IllegalArgumentException("Connection already executed");
            }

            conn = this.runRequest(url);

            inputStream = isSuccessCode() ? conn.getInputStream() : conn.getErrorStream();

            successful = true;
        } finally {
            if (!successful) {
                close();
            }
        }

        return this;
    }

    private HttpURLConnection runRequest(URL url) throws IOException {
        if (redirectCount > 20) {
            throw new IOException("Too many redirects!");
        }

        // Old Java 8 installs miss newer root CAs; add bundled ones on top of the JVM trust store
        // (chains are still fully validated, see TrustedTls).
        TrustedTls.install();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        //HttpURLConnection conn = (HttpURLConnection) reformat(url).openConnection();
        conn.setRequestProperty("User-Agent", Toolbox.USER_AGENT);
        conn.setInstanceFollowRedirects(false);

        if (body != null) {
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Content-Length", Integer.toString(body.length));
            conn.setDoInput(true);
        }

        if (resumeInfo != null) {
            conn.setRequestProperty("Range", String.format("bytes=%d-", resumeInfo.currentLength));
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }

        conn.setRequestMethod(method);
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);

        conn.connect();

        if (body != null) {
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.write(body);
            out.flush();
            out.close();
        }

        switch (conn.getResponseCode()) {
            case HttpURLConnection.HTTP_SEE_OTHER:
                method = "GET";
                body = null;
            case HttpURLConnection.HTTP_MOVED_PERM:
            case HttpURLConnection.HTTP_MOVED_TEMP:
            case HttpURLConnection.HTTP_ACCEPTED:
            case 307:
            case 308:
                String location = conn.getHeaderField("Location");
                redirectCount++;

                return runRequest(new URL(this.url, location));
            default:
                break;
        }

        return conn;
    }

    /**
     * Require that the response code is one of the given response codes.
     *
     * @param codes a list of codes
     * @return this object
     * @throws java.io.IOException if there is an I/O error or the response code is not expected
     */
    public HttpRequest expectResponseCode(int... codes) throws IOException {
        int responseCode = getResponseCode();

        for (int code : codes) {
            if (code == responseCode) {
                return this;
            }
        }

        if (resumeInfo != null && responseCode == 206) {
            // Allow 206 Partial Content for resumed requests
            return this;
        }

        close();
        throw new IOException("Did not get expected response code, got " + responseCode + " for " + url);
    }

    /**
     * Continue if the response code matches, otherwise call the provided function
     * to generate an exception.
     *
     * @param code HTTP status code to continue on.
     * @param onError Function invoked when the code does not match, should return an error that will be thrown.
     * @return this object if successful
     * @throws Exception either an {@link IOException} on I/O error or a user-defined {@link Exception} subclass
     *         if the code does not match.
     */
    public <E extends Exception> HttpRequest expectResponseCodeOr(int code, HttpFunction<HttpRequest, E> onError)
            throws E, IOException, InterruptedException {
        int responseCode = getResponseCode();

        if (code == responseCode) return this;

        E exc = onError.call(this);
        close();
        throw exc;
    }

    /**
     * Continue if the content type matches, otherwise throw an exception
     *
     * @param expectedTypes Expected content-type(s)
     * @return this object
     * @throws IOException Unexpected content-type or other error
     */
    public HttpRequest expectContentType(String... expectedTypes) throws IOException {
        if (conn == null) throw new IllegalArgumentException("No connection has been made!");

        String contentType = conn.getHeaderField("Content-Type");
        for (String expectedType : expectedTypes) {
            if (expectedType.equals(contentType)) {
                return this;
            }
        }

        close();
        throw new IOException(String.format("Did not get expected content type '%s', instead got '%s'.",
                String.join(" | ", expectedTypes), contentType));
    }

    /**
     * Get the response code.
     *
     * @return the response code
     * @throws java.io.IOException on I/O error
     */
    public int getResponseCode() throws IOException {
        if (conn == null) {
            throw new IllegalArgumentException("No connection has been made");
        }

        return conn.getResponseCode();
    }

    /**
     * Check if the response code indicates a successful request.
     * @return True if response code is 2xx, false otherwise.
     * @throws IOException on I/O error getting the response code.
     */
    public boolean isSuccessCode() throws IOException {
        int code = getResponseCode();
        return code >= 200 && code < 300;
    }

    /**
     * Get the input stream.
     *
     * @return the input stream
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * Check if a connection was ever made
     *
     * @return True if a connection is available, false otherwise
     */
    public boolean isConnected() {
        return conn != null;
    }

    /**
     * Buffer the returned response.
     *
     * @return the buffered response
     * @throws java.io.IOException  on I/O error
     * @throws InterruptedException on interruption
     */
    public BufferedResponse returnContent() throws IOException, InterruptedException {
        if (inputStream == null) {
            throw new IllegalArgumentException("No input stream available");
        }

        try {
            // 22.11.2026: блоками, а не по байту (кожен read() по байту йшов через синхронізований потік з'єднання)
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[READ_BUFFER_SIZE];
            int n;
            while ((n = inputStream.read(buf)) != -1) {
                checkInterrupted();
                bos.write(buf, 0, n);
                readBytes += n;
            }
            fullyRead = true;
            return new BufferedResponse(bos.toByteArray());
        } finally {
            close();
        }
    }

    /**
     * Save the result to a file.
     *
     * @param file the file
     * @return this object
     * @throws java.io.IOException  on I/O error
     * @throws InterruptedException on interruption
     */
    public HttpRequest saveContent(File file) throws IOException, InterruptedException {
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        boolean shouldAppend = resumeInfo != null && getResponseCode() == 206;

        try {
            fos = new FileOutputStream(file, shouldAppend);
            bos = new BufferedOutputStream(fos);

            saveContent(bos);
        } finally {
            closeQuietly(bos);
            closeQuietly(fos);
        }

        return this;
    }

    /**
     * Save the result to an output stream.
     *
     * @param out the output stream
     * @return this object
     * @throws java.io.IOException  on I/O error
     * @throws InterruptedException on interruption
     */
    public HttpRequest saveContent(OutputStream out) throws IOException, InterruptedException {
        BufferedInputStream bis;

        try {
            String field = conn.getHeaderField("Content-Length");
            if (field != null) {
                long len = Long.parseLong(field);
                if (len >= 0) { // Let's just not deal with really big numbers
                    contentLength = len;
                }
            }
        } catch (NumberFormatException e) {
        }

        try {
            bis = new BufferedInputStream(inputStream);

            byte[] data = new byte[READ_BUFFER_SIZE];
            int len = 0;
            while ((len = bis.read(data, 0, READ_BUFFER_SIZE)) >= 0) {
                out.write(data, 0, len);
                readBytes += len;
                checkInterrupted();
            }

            if (contentLength >= 0 && contentLength != readBytes) {
                throw new IOException(String.format("Connection closed with %d bytes transferred, expected %d",
                        readBytes, contentLength));
            }
            fullyRead = true;
        } finally {
            close();
        }

        return this;
    }

    public Optional<PartialDownloadInfo> canRetryPartial() {
        if (conn == null) {
            return Optional.empty();
        }

        if ("bytes".equals(conn.getHeaderField("Accept-Ranges"))) {
            // 14.11.2026: при другому обриві (уже в докачці) зміщення рахується від початку файлу, а не від початку
            // докачки. Раніше бралось лише readBytes поточного запиту: Range просив не той шматок, він дописувався
            // в кінець, і файл виходив зіпсованим. Повна довжина теж з першого запиту (у відповіді 206 лише залишок).
            boolean resumed;
            try {
                resumed = resumeInfo != null && conn.getResponseCode() == 206;
            } catch (IOException e) {
                return Optional.empty();
            }
            long offset = resumed ? resumeInfo.currentLength : 0;
            long expected = resumed ? resumeInfo.expectedLength : contentLength;
            return Optional.of(new PartialDownloadInfo(expected, offset + readBytes));
        }

        return Optional.empty();
    }

    public HttpRequest setResumeInfo(PartialDownloadInfo info) {
        this.resumeInfo = info;

        return this;
    }

    public boolean isResumedRequest() {
        return resumeInfo != null;
    }

    /** Скільки байтів цього запиту вже прочитано (1.32: швидкість у вікні прогресу). */
    public long getReadBytes() {
        return readBytes;
    }

    /** 17.11.2026: повна довжина відповіді з заголовка, -1 поки невідома (прогрес завантаження модпака). */
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public double getProgress() {
        if (contentLength >= 0) {
            return readBytes / (double) contentLength;
        } else {
            return -1;
        }
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    /**
     * 22.11.2026 (власник у брата: збірки Modrinth качались ~0,1 МБ/с, наші ~10 МБ/с): раніше тут завжди був
     * {@code disconnect()}, і кожен файл відкривав нове з'єднання з новим TLS-рукостисканням. Збірки Modrinth, ваніль і
     * завантажувачі це тисячі дрібних файлів (ресурси гри з resources.download.minecraft.net, моди з Modrinth), тож
     * більшість часу йшла на рукостискання, а антивірус з перевіркою HTTPS (AVG, Avast) робив кожне ще довшим. Тепер,
     * якщо відповідь прочитана до кінця, лише закриваємо потік: Java повертає з'єднання в пул і бере його для
     * наступного файлу з того самого сервера. Обрив, помилка чи недочитана відповідь: як раніше, {@code disconnect()}.
     */
    public void close() throws IOException {
        if (conn == null) return;
        if (fullyRead && inputStream != null) {
            try {
                inputStream.close();
                return;
            } catch (IOException e) {
                // нижче disconnect
            }
        }
        conn.disconnect();
    }

    /**
     * Perform a GET request.
     *
     * @param url the URL
     * @return a new request object
     */
    public static HttpRequest get(URL url) {
        return request("GET", url);
    }

    /**
     * Perform a POST request.
     *
     * @param url the URL
     * @return a new request object
     */
    public static HttpRequest post(URL url) {
        return request("POST", url);
    }

    /**
     * Perform a PUT request.
     *
     * @param url the URL
     * @return a new request object
     */
    public static HttpRequest put(URL url) {
        return request("PUT", url);
    }

    /**
     * Perform a request.
     *
     * @param method the method
     * @param url    the URL
     * @return a new request object
     */
    public static HttpRequest request(String method, URL url) {
        return new HttpRequest(method, url);
    }

    /**
     * Create a new {@link java.net.URL} and throw a {@link RuntimeException} if the URL
     * is not valid.
     *
     * @param url the url
     * @return a URL object
     * @throws RuntimeException if the URL is invalid
     */
    public static URL url(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Used with {@link #bodyForm(Form)}.
     */
    public final static class Form {
        public final List<String> elements = new ArrayList<String>();

        private Form() {
        }

        /**
         * Add a key/value to the form.
         *
         * @param key   the key
         * @param value the value
         * @return this object
         */
        public Form add(String key, String value) {
            try {
                elements.add(URLEncoder.encode(key, "UTF-8") +
                        "=" + URLEncoder.encode(value, "UTF-8"));
                return this;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (String element : elements) {
                if (first) {
                    first = false;
                } else {
                    builder.append("&");
                }
                builder.append(element);
            }
            return builder.toString();
        }

        /**
         * Create a new form.
         *
         * @return a new form
         */
        public static Form form() {
            return new Form();
        }
    }

    /**
     * Used to buffer the response in memory.
     */
    public class BufferedResponse {
        private final byte[] data;

        private BufferedResponse(byte[] data) {
            this.data = data;
        }

        /**
         * Return the result as bytes.
         *
         * @return the data
         */
        public byte[] asBytes() {
            return data;
        }

        /**
         * Return the result as a string.
         *
         * @param encoding the encoding
         * @return the string
         * @throws java.io.IOException on I/O error
         */
        public String asString(String encoding) throws IOException {
            return new String(data, encoding);
        }

        /**
         * Return the result as an instance of the given class that has been
         * deserialized from a JSON payload.
         *
         * @param cls the class
         * @return the object
         * @throws java.io.IOException on I/O error
         */
        public <T> T asJson(Class<T> cls) throws IOException {
            return mapper.readValue(asString("UTF-8"), cls);
        }

        /**
         * Return the result as an instance of the given type that has been
         * deserialized from a JSON payload.
         *
         * @param type the type reference
         * @return the object
         * @throws java.io.IOException on I/O error
         */
        public <T> T asJson(TypeReference<T> type) throws IOException {
            return mapper.readValue(asString("UTF-8"), type);
        }

        /**
         * Return the result as an instance of the given class that has been
         * deserialized from a XML payload.
         *
         * @return the object
         * @throws java.io.IOException on I/O error
         */
        @SuppressWarnings("unchecked")
        public <T> T asXml(Class<T> cls) throws IOException {
            try {
                JAXBContext context = JAXBContext.newInstance(cls);
                Unmarshaller um = context.createUnmarshaller();
                return (T) um.unmarshal(new ByteArrayInputStream(data));
            } catch (JAXBException e) {
                throw new IOException(e);
            }
        }

        /**
         * Save the result to a file.
         *
         * @param file the file
         * @return this object
         * @throws java.io.IOException  on I/O error
         * @throws InterruptedException on interruption
         */
        public BufferedResponse saveContent(File file) throws IOException, InterruptedException {
            FileOutputStream fos = null;
            BufferedOutputStream bos = null;

            file.getParentFile().mkdirs();

            try {
                fos = new FileOutputStream(file);
                bos = new BufferedOutputStream(fos);

                saveContent(bos);
            } finally {
                closeQuietly(bos);
                closeQuietly(fos);
            }

            return this;
        }

        /**
         * Save the result to an output stream.
         *
         * @param out the output stream
         * @return this object
         * @throws java.io.IOException  on I/O error
         * @throws InterruptedException on interruption
         */
        public BufferedResponse saveContent(OutputStream out) throws IOException, InterruptedException {
            out.write(data);

            return this;
        }
    }

    @Data
    public static class PartialDownloadInfo {
        private final long expectedLength;
        private final long currentLength;
    }

}
