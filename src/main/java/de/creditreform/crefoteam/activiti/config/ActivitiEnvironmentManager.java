package de.creditreform.crefoteam.activiti.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Verwaltet Activiti-Umgebungen aus *-activiti.properties-Dateien
 * im Arbeitsverzeichnis.
 * <p>
 * Dateiformat: local-activiti.properties, dev-activiti.properties, ...
 * Properties: activiti.url, activiti.user, activiti.password, activiti.env.name
 */
public class ActivitiEnvironmentManager {

    private static final String FILE_SUFFIX = "-activiti.properties";
    private static final ActivitiEnvironment DEFAULT = new ActivitiEnvironment(
            "default",
            "http://localhost:9090",
            "kermit",
            "kermit",
            "LOCAL"
    );

    private static volatile ActivitiEnvironment current = DEFAULT;

    private ActivitiEnvironmentManager() {}

    /**
     * Gibt alle Umgebungsnamen (Datei-Praefix) im Arbeitsverzeichnis zurueck.
     * Beispiel: "local-activiti.properties" -> "local"
     */
    public static List<String> findEnvironmentNames() {
        List<String> names = new ArrayList<>();
        File dir = new File(System.getProperty("user.dir"));
        File[] files = dir.listFiles((d, name) -> name.endsWith(FILE_SUFFIX));
        if (files != null) {
            for (File f : files) {
                String name = f.getName().replace(FILE_SUFFIX, "");
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Laedt eine Umgebung aus der entsprechenden Properties-Datei.
     * Gibt die Default-Umgebung zurueck, wenn die Datei nicht gefunden wird.
     */
    public static ActivitiEnvironment load(String name) {
        File file = new File(System.getProperty("user.dir"), name + FILE_SUFFIX);
        if (!file.exists()) {
            return DEFAULT;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            String url     = props.getProperty("activiti.url",      DEFAULT.getUrl());
            String user    = props.getProperty("activiti.user",     DEFAULT.getUser());
            String pass    = props.getProperty("activiti.password", DEFAULT.getPassword());
            String envName = props.getProperty("activiti.env.name", name.toUpperCase());
            return new ActivitiEnvironment(name, url, user, pass, envName);
        } catch (Exception e) {
            return DEFAULT;
        }
    }

    public static ActivitiEnvironment getCurrent() {
        return current;
    }

    public static void setCurrent(ActivitiEnvironment env) {
        current = env != null ? env : DEFAULT;
    }

    public static ActivitiEnvironment getDefault() {
        return DEFAULT;
    }
}
