package br.com.dignitas.ui;

import atlantafx.base.theme.Styles;
import br.com.dignitas.service.Dtos.PageResult;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.function.IntConsumer;

/** Barra "Página anterior · Página X de Y · Próxima página". */
public class Pager extends HBox {
    private final Button prev = Ui.button("Página anterior", Feather.CHEVRON_LEFT, Styles.BUTTON_OUTLINED);
    private final Button next = Ui.button("Próxima página", Feather.CHEVRON_RIGHT, Styles.BUTTON_OUTLINED);
    private final Label info = Ui.label("", "page-subtitle");
    private int page;

    public Pager(IntConsumer goTo) {
        getStyleClass().add("pager");
        next.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        prev.setOnAction(e -> goTo.accept(page - 1));
        next.setOnAction(e -> goTo.accept(page + 1));
        getChildren().addAll(prev, Ui.hspacer(), info, Ui.hspacer(), next);
    }

    public void update(PageResult<?> r) {
        page = r.page();
        info.setText("Página " + (r.page() + 1) + " de " + r.pages() + "  ·  " + r.total() + " itens");
        prev.setDisable(r.page() <= 0);
        next.setDisable(r.page() + 1 >= r.pages());
    }
}
