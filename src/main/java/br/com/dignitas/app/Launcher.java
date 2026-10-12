package br.com.dignitas.app;

import javafx.application.Application;

/**
 * Ponto de entrada do jar. Não estende {@link Application} para que o JavaFX
 * possa ser carregado a partir do classpath (jar único gerado pelo shade).
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(DignitasApp.class, args);
    }
}
