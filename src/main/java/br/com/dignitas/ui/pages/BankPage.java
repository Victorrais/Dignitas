package br.com.dignitas.ui.pages;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import br.com.dignitas.domain.Subject;
import br.com.dignitas.model.Texts;
import br.com.dignitas.service.Dtos.*;
import br.com.dignitas.ui.*;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;

public class BankPage extends DbPage {
    private static final int PAGE_SIZE = 12;

    private final CustomTextField search = new CustomTextField();
    private final ComboBox<Subject> subject = Combos.filter("Todas as disciplinas", Subject::getName);
    private final ComboBox<String> board = Combos.filter("Todas as bancas", s -> s);
    private final ComboBox<Integer> year = Combos.filter("Todos os anos", String::valueOf);
    private final ComboBox<Status> status = new ComboBox<>();
    private final ComboBox<Sort> sort = new ComboBox<>();
    private final TableView<QuestionRow> table = Tables.table();
    private final Pager pager = new Pager(this::fetch);
    private final Label subtitle = new Label();
    private int page;
    private boolean loadingOptions;

    public BankPage(MainWindow app) {
        super(app);
    }

    @Override
    protected Node build() {
        Button importPdf = Ui.button("Importar PDF", Feather.UPLOAD, Styles.BUTTON_OUTLINED);
        importPdf.setOnAction(e -> app.go(MainWindow.Nav.IMPORT));
        Button create = Ui.button("Nova questão", Feather.PLUS, Styles.ACCENT);
        create.setOnAction(e -> QuestionEditor.createNew(this::load));

        search.setPromptText("Buscar questão…");
        search.setLeft(Ui.icon(Feather.SEARCH, 16));
        search.setPrefWidth(280);
        PauseTransition debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> fetch(0));
        search.textProperty().addListener((o, a, b) -> debounce.playFromStart());

        status.getItems().setAll(Status.values());
        status.setValue(Status.ALL);
        sort.getItems().setAll(Sort.values());
        sort.setValue(Sort.RECENT);
        for (ComboBox<?> c : List.of(subject, board, year, status, sort)) {
            c.setOnAction(e -> {
                if (!loadingOptions) fetch(0);
            });
            c.setPrefWidth(190);
        }
        FlowPane toolbar = new FlowPane(10, 10, search, subject, board, year, status, sort);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().setAll(
                Tables.column("Questão", 460, r -> {
                    Node n = Tables.twoLines(Texts.abbreviate(r.statement(), 80),
                            r.number() == null ? "Sem número" : "Questão " + r.number());
                    if (r.annulled() || r.flagged()) {
                        HBox h = new HBox(8, n);
                        h.setAlignment(Pos.CENTER_LEFT);
                        if (r.annulled()) h.getChildren().add(Ui.badge("Anulada", "warning"));
                        if (r.flagged()) h.getChildren().add(Ui.icon(Feather.BOOKMARK, 14));
                        return h;
                    }
                    return n;
                }),
                Tables.column("Disciplina", 170, r -> Ui.label(r.subject() == null ? "—" : r.subject(), "cell-subtitle")),
                Tables.column("Origem", 200, r -> Ui.label(r.origin(), "cell-subtitle")),
                Tables.column("Última modificação", 170, r -> Ui.label(Ui.format(r.updatedAt()), "cell-subtitle")),
                Tables.column("", 130, this::actions));
        table.setRowFactory(t -> {
            TableRow<QuestionRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) QuestionEditor.editPersisted(row.getItem().id(), this::load);
            });
            return row;
        });
        table.setPlaceholder(Ui.emptyState(Feather.DATABASE, "Nenhuma questão encontrada",
                "Ajuste a busca e os filtros, importe um PDF ou cadastre uma questão manualmente."));

        VBox content = new VBox(16, Ui.header("Banco de questões", subtitle, importPdf, create), toolbar, table, pager);
        content.getStyleClass().add("page");
        return content;
    }

    private Node actions(QuestionRow r) {
        Button play = Ui.iconButton(Feather.PLAY, "Resolver esta questão");
        play.setOnAction(e -> app.openQuestion(r.id()));
        Button edit = Ui.iconButton(Feather.EDIT_2, "Editar questão");
        edit.setOnAction(e -> QuestionEditor.editPersisted(r.id(), this::load));
        Button delete = Ui.iconButton(Feather.TRASH_2, "Excluir questão", Styles.DANGER);
        delete.setOnAction(e -> {
            if (Ui.confirm("Excluir questão?", "A questão e todas as respostas registradas para ela serão removidas. "
                    + "Esta ação não pode ser desfeita.", "Excluir")) {
                Async.run(() -> {
                    Services.QUESTIONS.delete(r.id());
                    return true;
                }, x -> load());
            }
        });
        HBox h = new HBox(2, play, edit, delete);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    @Override
    protected void load() {
        Async.run(() -> new Object[]{Services.QUESTIONS.subjects(), Services.QUESTIONS.boards(), Services.QUESTIONS.years()},
                r -> {
                    loadingOptions = true;
                    @SuppressWarnings("unchecked") List<Subject> s = (List<Subject>) r[0];
                    @SuppressWarnings("unchecked") List<String> b = (List<String>) r[1];
                    @SuppressWarnings("unchecked") List<Integer> y = (List<Integer>) r[2];
                    Combos.setOptions(subject, s);
                    Combos.setOptions(board, b);
                    Combos.setOptions(year, y);
                    loadingOptions = false;
                    fetch(page);
                });
    }

    private void fetch(int p) {
        QuestionFilter f = new QuestionFilter(search.getText(),
                subject.getValue() == null ? null : subject.getValue().getId(),
                board.getValue(), year.getValue(), null, status.getValue(), sort.getValue(), false);
        Async.run(() -> {
            PageResult<QuestionRow> r = Services.QUESTIONS.search(f, Math.max(0, p), PAGE_SIZE);
            // Se a página ficou vazia após uma exclusão, volta uma.
            if (r.items().isEmpty() && r.page() > 0) r = Services.QUESTIONS.search(f, r.pages() - 1, PAGE_SIZE);
            return r;
        }, r -> {
            page = r.page();
            table.getItems().setAll(r.items());
            pager.update(r);
            subtitle.setText(r.total() + (r.total() == 1 ? " questão" : " questões"));
        });
    }
}
