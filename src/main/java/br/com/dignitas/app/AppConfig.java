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

    /**
     * Banco local (H2 embutido, num arquivo da pasta de dados) ou servidor MySQL.
     * Instalações novas começam no banco local, que não exige instalar nada; quem já
     * tinha um MySQL configurado (db.host salvo) continua nele.
     */
    public boolean localDatabase() {
        String mode = props.getProperty("db.mode");
        if (mode != null) return !"mysql".equals(mode);
        return !props.containsKey("db.host");
    }

    public void setLocalDatabase(boolean local) {
        props.setProperty("db.mode", local ? "local" : "mysql");
    }

    /** Arquivo do banco local, sem a extensão .mv.db que o H2 acrescenta. */
    public Path localDatabaseFile() {
        return dir.resolve("banco").resolve("dignitas");
    }

    public boolean localDatabaseExists() {
        return Files.exists(dir.resolve("banco").resolve("dignitas.mv.db"));
    }

    /** URL JDBC efetiva, conforme o modo escolhido. */
    public String connectionUrl() {
        if (!localDatabase()) return jdbcUrl();
        String file = localDatabaseFile().toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + file + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    }

    public String connectionUser() {
        return localDatabase() ? "sa" : dbUser();
    }

    public String connectionPassword() {
        return localDatabase() ? "" : dbPassword();
    }

    public String jdbcDriver() {
        return localDatabase() ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver";
    }

    /** Rótulo curto do banco em uso, para a barra de status. */
    public String databaseLabel() {
        return localDatabase() ? "Banco local" : "MySQL · " + dbName();
    }

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

    /** Escala da interface (1.0 = 14 px de texto base). */
    public double uiScale() {
        try {
            return Math.max(0.7, Math.min(1.5, Double.parseDouble(props.getProperty("ui.scale", "0.85"))));
        } catch (NumberFormatException e) {
            return 0.85;
        }
    }

    public void setUiScale(double scale) {
        props.setProperty("ui.scale", Double.toString(scale));
    }

    public boolean sidebarCollapsed() {
        return Boolean.parseBoolean(props.getProperty("ui.sidebarCollapsed", "false"));
    }

    public void setSidebarCollapsed(boolean collapsed) {
        props.setProperty("ui.sidebarCollapsed", Boolean.toString(collapsed));
    }

    /** Mostrar o texto do material de apoio ao resolver questões. */
    public boolean showSupportText() {
        return Boolean.parseBoolean(props.getProperty("ui.showSupportText", "true"));
    }

    public void setShowSupportText(boolean show) {
        props.setProperty("ui.showSupportText", Boolean.toString(show));
    }

    /** Mostrar imagens do material de apoio e do enunciado ao resolver questões. */
    public boolean showSupportImages() {
        return Boolean.parseBoolean(props.getProperty("ui.showSupportImages", "true"));
    }

    public void setShowSupportImages(boolean show) {
        props.setProperty("ui.showSupportImages", Boolean.toString(show));
    }

    /** Grava sem interromper a interface em caso de falha (preferências não críticas). */
    public void saveQuietly() {
        try {
            save();
        } catch (IOException e) {
            System.err.println("Não foi possível salvar as preferências: " + e.getMessage());
        }
    }
}
