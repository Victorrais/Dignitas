package br.com.dignitas.ui;

import atlantafx.base.theme.Styles;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.feather.Feather;

/**
 * Tela que depende do banco: mostra um aviso com atalho para as Configurações
 * enquanto o banco de dados não estiver conectado.
 */
public abstract class DbPage implements Page {
    protected final MainWindow app;
    private final StackPane root = new StackPane();
    private Node content;

    protected DbPage(MainWindow app) {
        this.app = app;
    }

    /** Constrói o conteúdo da tela (chamado uma vez). */
    protected abstract Node build();

    /** Recarrega os dados (chamado a cada exibição, com o banco conectado). */
    protected abstract void load();

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShow() {
        if (!app.isConnected()) {
            Button settings = Ui.button("Abrir Configurações", Feather.SETTINGS, Styles.ACCENT);
            settings.setOnAction(e -> app.go(MainWindow.Nav.SETTINGS));
            root.getChildren().setAll(Ui.emptyState(Feather.DATABASE, "Banco de dados desconectado",
                    "Não foi possível abrir o banco de dados. Confira o modo e os dados de conexão nas Configurações.",
                    settings));
            return;
        }
        if (content == null) content = build();
        root.getChildren().setAll(content);
        load();
    }
}
