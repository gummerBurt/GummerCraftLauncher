package com.gummer.launcher.util;

import com.skcraft.launcher.Launcher;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SelfRestart {

    /**
     * Перезапуск лаунчера після самооновлення: запускає новий процес бутстрапера (exe Launch4j або jar бутстрапера) у тій самій робочій папці
     * (портативний режим визначається по portable.txt у ній) і завершує цей. Бутстрапер стартує найновіший jar з папки launcher.
     *
     * @return false - не вдалося визначити, чим запускати (наприклад, лаунчер запущено напряму без бутстрапера); тоді гравцю треба перезапустити вручну
     */
    public static boolean relaunchBootstrap() {
        java.util.List<String> command = bootstrapCommand();
        if (command == null) return false;
        try {
            new ProcessBuilder(command).directory(new File(System.getProperty("user.dir"))).start();
        } catch (IOException e) {
            Logger.getLogger(SelfRestart.class.getName()).log(Level.WARNING, "Failed to relaunch " + command, e);
            return false;
        }
        System.exit(0);
        return true;
    }

    private static java.util.List<String> bootstrapCommand() {
        String exe = System.getProperty("launch4j.exefile"); // Launch4j сам виставляє шлях до exe
        if (exe != null && new File(exe).isFile()) return java.util.Collections.singletonList(exe);

        // інакше перший елемент classpath - exe/jar бутстрапера (сам лаунчер підвантажений бутстрапером окремим класлоадером з папки launcher)
        String first = System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(File.pathSeparator))[0];
        File entry = new File(first);
        if (first.isEmpty() || !entry.isFile()) return null;
        try {
            File self = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (self.getAbsoluteFile().equals(entry.getAbsoluteFile())) return null; // лаунчер без бутстрапера - нема що перезапускати
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
        String name = first.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".exe")) return java.util.Collections.singletonList(entry.getAbsolutePath());
        if (name.endsWith(".jar")) {
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            return java.util.Arrays.asList(javaBin, "-jar", entry.getAbsolutePath());
        }
        return null;
    }

    public static void restartRequiredDialog(Class<?> clazz, String title, String message) {
        try {
            int response = JOptionPane.showConfirmDialog(null, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            switch (response) {
                case JOptionPane.NO_OPTION:
                    break;
                case JOptionPane.YES_OPTION:
                    restartProcess();
                    break;
                case JOptionPane.CLOSED_OPTION:
                    break;
                default:
                    break;
            }
        } catch (IOException | URISyntaxException ex) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Failed To Restart", ex);
        }
    }

    public static void restartProcess() throws IOException, URISyntaxException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        File currentJar = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        /* is it a jar file? */
        if(!currentJar.getName().endsWith(".jar")) {
            return;
        }

        /* Build command: java -jar application.jar */
        String[] command = { javaBin, "-jar", currentJar.getPath() };
        //Runtime.getRuntime().exec(command);
        System.exit(0);
    }
}
