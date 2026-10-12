/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

//import org.apache.commons.lang3.RegExUtils; // turn off for now to strip extra deps

public class StringUtils {

    public static boolean containsIgnoreCase(String string, String searchString) {
        return string.toLowerCase().contains(searchString.toLowerCase());
    }

    public static boolean startsWithIgnoreCase(String string, String searchString) {
        return string.toLowerCase().startsWith(searchString.toLowerCase());
    }

    /**
     * Removes non-printable characters (excluding new line and carriage return) in the provided {@link java.lang.String String}.
     *
     * @param string The {@link java.lang.String String} to filter.
     * @return The filtered {@link java.lang.String String}.
     */
//    public static String filter(String string) {
//        return RegExUtils.replaceAll(string, "[^\\x20-\\x7E\\x0A\\x0D]", "");
//    }
}
