package br.com.dignitas.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configurações do usuário, gravadas em {@code %APPDATA%/Dignitas/config.properties}
 * (ou {@code ~/.dignitas} fora do Windows).
 */
public final class AppConfig {
    private static final AppConfig INSTANCE = new AppConfig(defaultDir());

    private final Path dir;
    private final Path file;
    private final Properties props = new Properties();

    AppConfig(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("config.properties");
        load();
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    private static Path defaultDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "Dignitas");
        }
        return Path.of(System.getProperty("user.home"), ".dignitas");
    }

    private void load() {
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Não foi possível ler " + file + ": " + e.getMessage());
            }
        }
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(dir);
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Dignitas");
        }
    }

    public Path dataDir() {
        return dir;
    }

    // ---- Banco de dados ----

    public String dbHost() {
        return props.getProperty("db.host", "localhost");
    }

    public int dbPort() {
        try {
            return Integer.parseInt(props.getProperty("db.port", "3306"));
        } catch (NumberFormatException e) {
            return 3306;
        }
    }

    public String dbName() {
        return props.getProperty("db.name", "dignitasdb");
    }

    public String dbUser() {
        return props.getProperty("db.user", "root");
    }

    public String dbPassword() {
        return props.getProperty("db.password", "");
    }

    public void setDatabase(String host, int port, String name, String user, String password) {
        props.setProperty("db.host", host);
        props.setProperty("db.port", Integer.toString(port));
        props.setProperty("db.name", name);
        props.setProperty("db.user", user);
        props.setProperty("db.password", password);
    }

    public String jdbcUrl() {
        return jdbcUrl(dbHost(), dbPort(), dbName());
    }

    public static String jdbcUrl(String host, int port, String name) {
        return "jdbc:mysql://" + host + ":" + port + "/" + name
                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8"
                + "&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    }

    // ---- OCR ----

    public Path tessdataDir() {
        String custom = props.getProperty("ocr.tessdata");
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom);
        }
        return dir.resolve("tessdata");
    }

    public void setTessdataDir(Path path) {
        if (path == null) {
            props.remove("ocr.tessdata");
        } else {
            props.setProperty("ocr.tessdata", path.toString());
        }
    }

    // ---- Aparência ----

    public boolean darkTheme() {
        return Boolean.parseBoolean(props.getProperty("ui.dark", "false"));
    }

    public void setDarkTheme(boolean dark) {
        props.setProperty("ui.dark", Boolean.toString(dark));
    }
}
