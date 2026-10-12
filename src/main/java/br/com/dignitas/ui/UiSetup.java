package br.com.dignitas.ui;

import javafx.stage.Window;

/** Ponte para a camada {@code app} configurar a janela dona dos diálogos. */
public final class UiSetup {
    private UiSetup() {
    }

    public static void setOwner(Window w) {
        Ui.setOwner(w);
    }
}
