package br.com.dignitas.ui.pages;

import atlantafx.base.theme.Styles;
import br.com.dignitas.service.Dtos.CategoryStat;
import br.com.dignitas.service.Dtos.ImportRow;
import br.com.dignitas.service.Dtos.Overview;
import br.com.dignitas.service.Dtos.Status;
import br.com.dignitas.service.SampleData;
import br.com.dignitas.ui.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class DashboardPage extends DbPage {
    private final VBox body = new VBox(20);

    public DashboardPage(MainWindow app) {
        super(app);
    }

    private record Data(Overview overview, List<CategoryStat> subjects, List<ImportRow> imports) {
    }

    @Override
    protected Node build() {
        LocalDate today = LocalDate.now();
        String date = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("pt", "BR"))
                + ", " + Ui.DATE.format(today);
        VBox page = new VBox(20, Ui.header("Bons estudos!", new Label(capitalize(date))), body);
        return Ui.scrollPage(page);
    }

    @Override
    protected void load() {
        Async.run(() -> new Data(Services.STATS.overview(), Services.STATS.bySubject(), Services.IMPORTS.list()),
                this::render);
    }

    private void render(Data d) {
        Overview o = d.overview();
        if (o.questions() == 0) {
            Button importBtn = Ui.button("Importar PDF", Feather.UPLOAD, Styles.ACCENT);
            importBtn.setOnAction(e -> app.go(MainWindow.Nav.IMPORT));
            Button sample = Ui.button("Inserir questões de exemplo", Feather.PLUS, Styles.BUTTON_OUTLINED);
            sample.setOnAction(e -> Async.run(() -> SampleData.insert(Services.QUESTIONS), n -> load()));
            body.getChildren().setAll(Ui.card(Ui.emptyState(Feather.INBOX, "Seu banco de questões está vazio",
                    "Importe a prova de um concurso em PDF (texto ou digitalizada) para começar, "
                            + "ou insira algumas questões de exemplo para conhecer o Dignitas.",
                    importBtn, sample)));
            return;
        }

        HBox stats = new HBox(16,
                stat(Feather.DATABASE, "", Long.toString(o.questions()), "questões no banco"),
                stat(Feather.CHECK_SQUARE, "success", Long.toString(o.answeredQuestions()), "questões respondidas"),
                stat(Feather.TARGET, o.attempts() == 0 ? "" : o.accuracy() >= 0.6 ? "success" : "warning",
                        o.attempts() == 0 ? "—" : Ui.percent(o.accuracy()), "taxa de acerto"),
                stat(Feather.BOOKMARK, "warning", Long.toString(o.flagged()), "marcadas para revisão"));
        stats.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        Button practice = Ui.button("Resolver questões", Feather.PLAY, Styles.ACCENT);
        practice.setOnAction(e -> app.practice(Status.UNANSWERED));
        Button wrong = Ui.button("Refazer as que errei", Feather.ROTATE_CCW, Styles.BUTTON_OUTLINED);
        wrong.setOnAction(e -> app.practice(Status.WRONG));
        Button flagged = Ui.button("Revisar marcadas", Feather.BOOKMARK, Styles.BUTTON_OUTLINED);
        flagged.setOnAction(e -> app.practice(Status.FLAGGED));
        HBox actions = new HBox(10, practice, wrong, flagged);
        VBox cta = Ui.card(Ui.label("Continue de onde parou", "card-title"),
                Ui.wrapped(o.questions() - o.answeredQuestions() + " questões ainda não foram respondidas.", "page-subtitle"),
                actions);

        VBox subjects = Ui.card(Ui.label("Desempenho por disciplina", "card-title"));
        if (d.subjects().isEmpty()) {
            subjects.getChildren().add(Ui.wrapped("Responda algumas questões para ver seu desempenho aqui.", "page-subtitle"));
        }
        d.subjects().stream().limit(6).forEach(s -> subjects.getChildren().add(progressRow(s)));

        VBox imports = Ui.card(Ui.label("Importações recentes", "card-title"));
        if (d.imports().isEmpty()) imports.getChildren().add(Ui.wrapped("Nenhum PDF importado ainda.", "page-subtitle"));
        d.imports().stream().limit(5).forEach(i -> {
            HBox row = new HBox(10, Ui.icon(Feather.FILE_TEXT, 16),
                    new VBox(Ui.label(i.fileName(), "cell-title"), Ui.label(i.origin(), "cell-subtitle")),
                    Ui.hspacer(), Ui.badge(i.questions() + " questões", "accent"));
            row.setAlignment(Pos.CENTER_LEFT);
            imports.getChildren().add(row);
        });
        Button allImports = Ui.button("Ver todas", null, Styles.FLAT);
        allImports.setOnAction(e -> app.go(MainWindow.Nav.IMPORTS));
        imports.getChildren().add(allImports);

        HBox columns = new HBox(20, subjects, imports);
        HBox.setHgrow(subjects, Priority.ALWAYS);
        HBox.setHgrow(imports, Priority.ALWAYS);
        subjects.setPrefWidth(1);
        imports.setPrefWidth(1);

        body.getChildren().setAll(stats, cta, columns);
    }

    private static VBox stat(Ikon icon, String kind, String value, String label) {
        StackPane iconBox = new StackPane(Ui.icon(icon, 18));
        iconBox.getStyleClass().add("stat-icon");
        if (!kind.isEmpty()) iconBox.getStyleClass().add(kind);
        VBox card = Ui.card(iconBox, Ui.label(value, "stat-value"), Ui.label(label, "stat-label"));
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(1);
        return card;
    }

    private static Node progressRow(CategoryStat s) {
        ProgressBar bar = new ProgressBar(s.accuracy());
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(Styles.SMALL);
        if (s.accuracy() >= 0.7) bar.getStyleClass().add(Styles.SUCCESS);
        else if (s.accuracy() < 0.5) bar.getStyleClass().add(Styles.DANGER);
        HBox top = new HBox(Ui.label(s.name(), "cell-title"), Ui.hspacer(),
                Ui.label(Ui.percent(s.accuracy()) + "  ·  " + s.correct() + "/" + s.attempts(), "cell-subtitle"));
        return new VBox(6, top, bar);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
