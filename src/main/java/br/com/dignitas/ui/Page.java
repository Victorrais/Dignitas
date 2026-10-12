package br.com.dignitas.ui;

import javafx.scene.Node;

/** Uma tela exibida na área central da janela. */
public interface Page {
    Node root();

    /** Chamado sempre que a tela é exibida (recarrega os dados). */
    default void onShow() {
    }

    /** Chamado quando o usuário sai da tela. */
    default void onHide() {
    }
}
