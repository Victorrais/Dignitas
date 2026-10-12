package br.com.dignitas.ui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** ComboBox de filtro em que o item nulo significa "todos". */
public final class Combos {
    private Combos() {
    }

    public static <T> ComboBox<T> filter(String allLabel, Function<T, String> text) {
        ComboBox<T> c = new ComboBox<>();
        StringConverter<T> conv = new StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? allLabel : text.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
        c.setConverter(conv);
        c.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : conv.toString(item));
            }
        });
        c.setButtonCell(c.getCellFactory().call(null));
        c.getItems().add(null);
        c.getSelectionModel().selectFirst();
        return c;
    }

    /** Troca as opções mantendo a seleção atual quando possível. */
    public static <T> void setOptions(ComboBox<T> c, List<T> options) {
        T selected = c.getValue();
        List<T> items = new ArrayList<>();
        items.add(null);
        items.addAll(options);
        c.getItems().setAll(items);
        c.setValue(selected != null && options.contains(selected) ? selected : null);
    }
}
