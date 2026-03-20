package de.creditreform.crefoteam.activiti.config;

/**
 * Datenmodell fuer eine Activiti-Umgebung (URL, Credentials, ENV_NAME).
 */
public class ActivitiEnvironment {

    private final String name;
    private final String url;
    private final String user;
    private final String password;
    private final String envName;

    public ActivitiEnvironment(String name, String url, String user, String password, String envName) {
        this.name = name;
        this.url = url;
        this.user = user;
        this.password = password;
        this.envName = envName;
    }

    public String getName()     { return name; }
    public String getUrl()      { return url; }
    public String getUser()     { return user; }
    public String getPassword() { return password; }
    public String getEnvName()  { return envName; }

    @Override
    public String toString() { return name; }
}
