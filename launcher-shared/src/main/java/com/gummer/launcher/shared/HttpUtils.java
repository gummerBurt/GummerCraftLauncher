package com.gummer.launcher.shared;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class HttpUtils {

    /**
     * URL may contain spaces and other nasties that will cause a failure.
     *
     * @param existing the existing URL to transform
     * @return the new URL, or old one if there was a failure
     */
    public static URL reformat(URL existing) {
        try {
            URL url = new URL(existing.toString());
            URI uri = new URI(
                url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
                url.getPath(), url.getQuery(), url.getRef());
            url = uri.toURL();
            return url;
        } catch (MalformedURLException e) {
            return existing;
        } catch (URISyntaxException e) {
            return existing;
        }
    }

    public static String encode(String string) {
        return string
            .replace(" ", "%20")
            .replace("'", "%27")
            .replace("+", "%2B");
    }
}
