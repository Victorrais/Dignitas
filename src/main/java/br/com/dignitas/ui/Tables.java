package br.com.dignitas.ui;

import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/** Atalhos para colunas de tabela que exibem componentes. */
public final class Tables {
    private Tables() {
    }

    public static <S> TableColumn<S, S> column(String title, double prefWidth, Function<S, Node> render) {
        TableColumn<S, S> c = new TableColumn<>(title);
        c.setPrefWidth(prefWidth);
        c.setSortable(false);
        c.setCellValueFactory(p -> new javafx.beans.property.SimpleObjectProperty<>(p.getValue()));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : render.apply(item));
            }
        });
        return c;
    }

    /** Duas linhas: título e subtítulo esmaecido. */
    public static Node twoLines(String title, String subtitle) {
        VBox v = new VBox(2, Ui.label(title, "cell-title"), Ui.label(subtitle, "cell-subtitle"));
        return v;
    }

    public static <S> TableView<S> table() {
        TableView<S> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.fixedCellSizeProperty().bind(Ui.scaleProperty().multiply(58));
        VBox.setVgrow(t, javafx.scene.layout.Priority.ALWAYS);
        return t;
    }
}
