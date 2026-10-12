package br.com.dignitas.app;

import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.ui.MainWindow;
import br.com.dignitas.ui.UiSetup;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DignitasApp extends Application {
    // Referência forte: o JUL descarta configurações de loggers não referenciados.
    private static final Logger HIBERNATE_LOG = Logger.getLogger("org.hibernate");

    @Override
    public void init() {
        HIBERNATE_LOG.setLevel(Level.WARNING);
    }

    @Override
    public void start(Stage stage) {
        MainWindow.applyTheme(AppConfig.get().darkTheme());
        MainWindow window = new MainWindow();

        Scene scene = new Scene(window.root(), 1280, 820);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/dignitas.css")).toExternalForm());
        stage.setTitle("Dignitas — Banco de questões");
        stage.setMinWidth(860);
        stage.setMinHeight(600);
        stage.setScene(scene);
        window.installShortcuts(scene);
        UiSetup.setOwner(stage);
        stage.show();

        window.go(MainWindow.Nav.DASHBOARD);
        if (Jpa.isReady()) {
            // Já inicializado por fora (lançador de desenvolvimento/testes).
            window.markConnected("Banco de desenvolvimento");
        } else {
            window.connect(() -> {
                if (!window.isConnected()) window.go(MainWindow.Nav.SETTINGS);
            });
        }
    }

    @Override
    public void stop() {
        Jpa.shutdown();
    }
}
