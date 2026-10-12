package br.com.dignitas.ui.pages;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import br.com.dignitas.model.Texts;
import br.com.dignitas.service.Dtos.HistoryRow;
import br.com.dignitas.service.Dtos.PageResult;
import br.com.dignitas.service.Dtos.ResultFilter;
import br.com.dignitas.ui.*;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

public class HistoryPage extends DbPage {
    private static final int PAGE_SIZE = 12;

    private final CustomTextField search = new CustomTextField();
    private final ComboBox<ResultFilter> result = new ComboBox<>();
    private final TableView<HistoryRow> table = Tables.table();
    private final Pager pager = new Pager(this::fetch);
    private final Label subtitle = new Label();
    private int page;

    public HistoryPage(MainWindow app) {
        super(app);
    }

    @Override
    protected Node build() {
        search.setPromptText("Buscar no histórico…");
        search.setLeft(Ui.icon(Feather.SEARCH, 16));
        search.setPrefWidth(380);
        PauseTransition debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> fetch(0));
        search.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        result.getItems().setAll(ResultFilter.values());
        result.setValue(ResultFilter.ALL);
        result.setOnAction(e -> fetch(0));
        HBox toolbar = new HBox(10, search, result);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().setAll(
                Tables.column("Pergunta", 520, r -> Tables.twoLines(Texts.abbreviate(r.statement(), 90),
                        (r.number() == null ? "" : "Questão " + r.number() + " · ") + r.origin())),
                Tables.column("Sua resposta", 110, r -> Ui.label(r.chosenLetter() == null ? "—" : r.chosenLetter(), "cell-title")),
                Tables.column("Resultado", 120, r -> r.correct()
                        ? Ui.badge("Correta", "success") : Ui.badge("Errada", "danger")),
                Tables.column("Data", 150, r -> Ui.label(Ui.format(r.answeredAt()), "cell-subtitle")),
                Tables.column("", 140, r -> {
                    Button b = Ui.button("Ver questão", Feather.EYE, Styles.SMALL, Styles.BUTTON_OUTLINED);
                    b.setOnAction(e -> app.openQuestion(r.questionId()));
                    return b;
                }));
        table.setRowFactory(t -> {
            TableRow<HistoryRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) app.openQuestion(row.getItem().questionId());
            });
            return row;
        });
        table.setPlaceholder(Ui.emptyState(Feather.CLOCK, "Nenhuma resposta ainda",
                "As questões que você responder aparecem aqui."));

        VBox content = new VBox(16, Ui.header("Histórico", subtitle), toolbar, table, pager);
        content.getStyleClass().add("page");
        return content;
    }

    @Override
    protected void load() {
        fetch(page);
    }

    private void fetch(int p) {
        String text = search.getText();
        ResultFilter f = result.getValue();
        Async.run(() -> Services.ATTEMPTS.history(text, f, Math.max(0, p), PAGE_SIZE), this::show);
    }

    private void show(PageResult<HistoryRow> r) {
        page = r.page();
        table.getItems().setAll(r.items());
        pager.update(r);
        subtitle.setText(r.total() + (r.total() == 1 ? " resposta registrada" : " respostas registradas"));
    }
}
