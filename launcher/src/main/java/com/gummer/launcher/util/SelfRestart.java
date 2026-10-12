package com.gummer.launcher.util;

import com.skcraft.launcher.Launcher;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SelfRestart {

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
