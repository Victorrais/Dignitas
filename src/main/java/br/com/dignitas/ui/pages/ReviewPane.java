package br.com.dignitas.ui.pages;

import atlantafx.base.theme.Styles;
import br.com.dignitas.domain.Subject;
import br.com.dignitas.extraction.ImportPipeline;
import br.com.dignitas.extraction.PdfPageRenderer;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.Region;
import br.com.dignitas.model.Texts;
import br.com.dignitas.service.Dtos.SaveSummary;
import br.com.dignitas.ui.Async;
import br.com.dignitas.ui.QuestionEditor;
import br.com.dignitas.ui.Services;
import br.com.dignitas.ui.Ui;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.feather.Feather;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Passo 3 da importação: página do PDF à esquerda, questões extraídas à direita.
 * O usuário corrige, mescla, exclui e escolhe o que salvar.
 */
class ReviewPane extends BorderPane {
    private static final double BASE_DPI = 96;

    private final ImportPipeline.Result result;
    private final ExamInfo exam;
    private final Runnable onSaved;
    private final Runnable onDiscard;
    private final PdfPageRenderer renderer;

    private final ListView<QuestionDraft> list = new ListView<>();
    private final ImageView pageView = new ImageView();
    private final Rectangle highlight = new Rectangle();
    private final Label pageLabel = new Label();
    private final Label selectedCount = new Label();
    private final Map<Integer, Image> cache = new HashMap<>();
    private int page;
    private double zoom = 1.0;
    private ScrollPane viewerScroll;
    private boolean fitPending = true;

    ReviewPane(ImportPipeline.Result result, ExamInfo exam, Runnable onSaved, Runnable onDiscard) throws IOException {
        this.result = result;
        this.exam = exam;
        this.onSaved = onSaved;
        this.onDiscard = onDiscard;
        this.renderer = new PdfPageRenderer(result.file());

        list.getItems().setAll(result.questions());
        list.getStyleClass().add("review-list");
        list.setCellFactory(lv -> new DraftCell());
        list.getSelectionModel().selectedItemProperty().addListener((o, a, q) -> {
            if (q != null && q.getRegion() != null) showPage(q.getRegion().page());
            else updateHighlight();
        });
        list.setPlaceholder(Ui.emptyState(Feather.FILE_MINUS, "Nenhuma questão identificada",
                "Confira os avisos acima. Para PDFs digitalizados, tente o modo \"Forçar OCR\"."));

        setTop(summary());
        SplitPane split = new SplitPane(viewer(), questionsPanel());
        split.setDividerPositions(0.48);
        split.setStyle("-fx-background-color: transparent; -fx-padding: 12 0 12 0;");
        setCenter(split);
        setBottom(footer());

        updateCount();
        showPage(0);
        if (!list.getItems().isEmpty()) list.getSelectionModel().selectFirst();
    }

    void close() {
        try {
            renderer.close();
        } catch (IOException ignored) {
            // nada a fazer
        }
    }

    // ---------- Topo ----------

    private VBox summary() {
        List<QuestionDraft> qs = result.questions();
        long attention = qs.stream().filter(ReviewPane::needsAttention).count();
        long dup = qs.stream().filter(QuestionDraft::isDuplicate).count();
        HBox badges = new HBox(8,
                Ui.badge(qs.size() + " questões identificadas", "accent"),
                Ui.badge(result.pageCount() + " páginas" + (result.ocrPages() > 0 ? " · " + result.ocrPages() + " por OCR" : ""), null),
                Ui.badge("gabarito aplicado a " + result.answersApplied(), result.answersApplied() > 0 ? "success" : null));
        if (attention > 0) badges.getChildren().add(Ui.badge(attention + " precisam de atenção", "warning"));
        if (dup > 0) badges.getChildren().add(Ui.badge(dup + " duplicadas (desmarcadas)", "danger"));
        badges.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, badges);
        for (String w : result.warnings()) {
            Label l = Ui.wrapped(w, "hint");
            l.setGraphic(Ui.icon(Feather.ALERT_TRIANGLE, 14));
            box.getChildren().add(l);
        }
        return box;
    }

    // ---------- Visualizador de página ----------

    private VBox viewer() {
        Button prev = Ui.iconButton(Feather.CHEVRON_LEFT, "Página anterior");
        prev.setOnAction(e -> showPage(page - 1));
        Button next = Ui.iconButton(Feather.CHEVRON_RIGHT, "Próxima página");
        next.setOnAction(e -> showPage(page + 1));
        Button zoomOut = Ui.iconButton(Feather.ZOOM_OUT, "Diminuir zoom");
        zoomOut.setOnAction(e -> setZoom(zoom / 1.25));
        Button zoomIn = Ui.iconButton(Feather.ZOOM_IN, "Aumentar zoom");
        zoomIn.setOnAction(e -> setZoom(zoom * 1.25));
        Button fit = Ui.iconButton(Feather.MAXIMIZE_2, "Ajustar à largura");
        fit.setOnAction(e -> fitWidth());
        HBox toolbar = new HBox(6, prev, pageLabel, next, Ui.hspacer(), zoomOut, zoomIn, fit);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        highlight.getStyleClass().add("region-highlight");
        highlight.setManaged(false);
        highlight.setMouseTransparent(true);
        Pane overlay = new Pane(pageView, highlight);
        ScrollPane sp = new ScrollPane(new Group(overlay));
        sp.getStyleClass().add("page-viewer");
        sp.setPannable(true);
        viewerScroll = sp;
        // Na primeira exibição, ajusta a página à largura do painel.
        sp.viewportBoundsProperty().addListener((o, a, b) -> {
            if (fitPending && b.getWidth() > 50) {
                fitPending = false;
                fitWidth();
            }
        });
        VBox.setVgrow(sp, Priority.ALWAYS);
        VBox box = Ui.card(toolbar, sp);
        box.setStyle("-fx-padding: 12;");
        return box;
    }

    private void fitWidth() {
        double available = viewerScroll.getViewportBounds().getWidth() - 4;
        double pageWidth = renderer.pageWidth(page) * BASE_DPI / 72.0;
        if (available > 50 && pageWidth > 0) setZoom(available / pageWidth);
    }

    private void setZoom(double z) {
        zoom = Math.max(0.5, Math.min(3, z));
        cache.clear();
        showPage(page);
    }

    private void showPage(int p) {
        if (p < 0 || p >= renderer.pageCount()) return;
        page = p;
        pageLabel.setText("Página " + (p + 1) + " de " + renderer.pageCount());
        Image cached = cache.get(p);
        if (cached != null) {
            pageView.setImage(cached);
            updateHighlight();
            return;
        }
        float dpi = (float) (BASE_DPI * zoom);
        Async.run(() -> SwingFXUtils.toFXImage(renderer.render(p, dpi), null), img -> {
            cache.put(p, img);
            if (page == p) {
                pageView.setImage(img);
                updateHighlight();
            }
        });
    }

    private void updateHighlight() {
        QuestionDraft q = list.getSelectionModel().getSelectedItem();
        Region r = q == null ? null : q.getRegion();
        if (r == null || r.page() != page) {
            highlight.setVisible(false);
            return;
        }
        double s = BASE_DPI * zoom / 72.0;
        highlight.setX(r.x0() * s - 4);
        highlight.setY(r.y0() * s - 4);
        highlight.setWidth((r.x1() - r.x0()) * s + 8);
        highlight.setHeight((r.y1() - r.y0()) * s + 8);
        highlight.setVisible(true);
    }

    // ---------- Lista de questões ----------

    private VBox questionsPanel() {
        Button edit = Ui.button("Editar", Feather.EDIT_2, Styles.SMALL);
        edit.setOnAction(e -> editSelected());
        Button merge = Ui.button("Mesclar com a próxima", Feather.GIT_MERGE, Styles.SMALL);
        merge.setOnAction(e -> mergeSelected());
        Button crop = Ui.button("Anexar recorte", Feather.CROP, Styles.SMALL);
        crop.setTooltip(new Tooltip("Anexa como imagem a área destacada da página (figuras, gráficos, tabelas)"));
        crop.setOnAction(e -> cropSelected());
        Button delete = Ui.button("Remover", Feather.TRASH_2, Styles.SMALL, Styles.DANGER);
        delete.setOnAction(e -> {
            QuestionDraft q = list.getSelectionModel().getSelectedItem();
            if (q != null) {
                list.getItems().remove(q);
                updateCount();
            }
        });
        for (Button b : List.of(edit, merge, crop, delete)) {
            b.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        }
        CheckBox all = new CheckBox("Selecionar todas");
        all.setSelected(true);
        all.setOnAction(e -> {
            list.getItems().forEach(q -> q.setIncluded(all.isSelected()));
            list.refresh();
            updateCount();
        });
        FlowPane toolbar = new FlowPane(6, 6, edit, merge, crop, delete, all);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) editSelected();
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox box = Ui.card(toolbar, list);
        box.setStyle("-fx-padding: 12;");
        return box;
    }

    private void editSelected() {
        QuestionDraft q = list.getSelectionModel().getSelectedItem();
        if (q == null) return;
        List<String> subjects;
        try {
            subjects = Services.QUESTIONS.subjects().stream().map(Subject::getName).toList();
        } catch (RuntimeException e) {
            subjects = List.of();
        }
        boolean saved = QuestionEditor.edit(q, false, subjects, q.getRegion() == null ? null : () -> crop(q.getRegion()));
        if (saved) {
            q.setIncluded(true);
            q.getWarnings().removeIf(w -> !w.startsWith("Já existe") && !w.startsWith("Repetida"));
            list.refresh();
            updateCount();
        }
    }

    private void mergeSelected() {
        int i = list.getSelectionModel().getSelectedIndex();
        if (i < 0 || i + 1 >= list.getItems().size()) return;
        QuestionDraft a = list.getItems().get(i);
        QuestionDraft b = list.getItems().get(i + 1);
        a.setStatement((a.getStatement() + "\n" + b.getStatement()).strip());
        if (a.getAlternatives().size() < 2 || a.isTrueFalse() && !b.isTrueFalse()) {
            a.getAlternatives().clear();
            a.getAlternatives().addAll(b.getAlternatives());
            a.setTrueFalse(b.isTrueFalse());
        }
        if (a.getBaseText() == null) a.setBaseText(b.getBaseText());
        if (a.getImage() == null) a.setImage(b.getImage());
        a.getWarnings().clear();
        a.getWarnings().add("Mesclada com a questão seguinte: confira o resultado.");
        list.getItems().remove(i + 1);
        list.refresh();
        updateCount();
    }

    private void cropSelected() {
        QuestionDraft q = list.getSelectionModel().getSelectedItem();
        if (q == null) return;
        if (q.getRegion() == null) {
            Ui.info("Sem região", "Não foi possível localizar esta questão na página. Use \"Editar\" para anexar uma imagem.");
            return;
        }
        byte[] png = crop(q.getRegion());
        if (png != null) {
            q.setImage(png);
            list.refresh();
        }
    }

    private byte[] crop(Region r) {
        try {
            return renderer.cropPng(r, 150);
        } catch (IOException e) {
            Ui.showError(e);
            return null;
        }
    }

    private static boolean needsAttention(QuestionDraft q) {
        return !q.validate().isEmpty() || q.getConfidence() < 0.6 || !q.getWarnings().isEmpty();
    }

    private final class DraftCell extends ListCell<QuestionDraft> {
        @Override
        protected void updateItem(QuestionDraft q, boolean empty) {
            super.updateItem(q, empty);
            setText(null);
            if (empty || q == null) {
                setGraphic(null);
                return;
            }
            CheckBox include = new CheckBox();
            include.setSelected(q.isIncluded());
            include.setOnAction(e -> {
                q.setIncluded(include.isSelected());
                updateCount();
            });

            HBox title = new HBox(6, Ui.label(q.getNumber() == null ? "Sem nº" : "Questão " + q.getNumber(), "cell-title"));
            title.setAlignment(Pos.CENTER_LEFT);
            ((Label) title.getChildren().getFirst()).setStyle("-fx-font-weight: bold;");
            List<String> errors = q.validate();
            if (q.isDuplicate()) title.getChildren().add(Ui.badge("Duplicada", "danger"));
            else if (!errors.isEmpty()) title.getChildren().add(Ui.badge("Incompleta", "danger"));
            else if (needsAttention(q)) title.getChildren().add(Ui.badge("Revisar", "warning"));
            else title.getChildren().add(Ui.badge("OK", "success"));
            if (q.isAnnulled()) title.getChildren().add(Ui.badge("Anulada", "warning"));
            else q.correctAlternative().ifPresentOrElse(
                    a -> title.getChildren().add(Ui.badge("Gabarito " + a.getLetter(), "accent")),
                    () -> title.getChildren().add(Ui.badge("Sem gabarito", "warning")));
            title.getChildren().add(Ui.badge(q.isTrueFalse() ? "Certo/Errado" : q.getAlternatives().size() + " alternativas", null));
            if (q.getBaseText() != null) {
                title.getChildren().add(Ui.badge(q.getBaseText().getImage() != null ? "Texto-base com imagem" : "Texto-base", null));
            }
            if (q.getImage() != null) title.getChildren().add(Ui.badge("Imagem", null));
            if (q.getAlternatives().stream().anyMatch(a -> a.getImage() != null)) {
                title.getChildren().add(Ui.badge("Alternativas com imagem", "accent"));
            }
            if (q.isOcr()) title.getChildren().add(Ui.badge("OCR", null));

            Label snippet = Ui.wrapped(Texts.abbreviate(q.getStatement(), 180), "cell-subtitle");
            snippet.setMaxHeight(36);
            VBox body = new VBox(4, title, snippet);
            if (q.getSubjectName() != null) body.getChildren().add(Ui.label(q.getSubjectName(), "hint"));
            for (String w : q.getWarnings()) {
                Label l = Ui.wrapped(w, "hint");
                l.setGraphic(Ui.icon(Feather.ALERT_TRIANGLE, 12));
                body.getChildren().add(l);
            }
            for (String err : errors) {
                Label l = Ui.wrapped(err, "error-text");
                l.setStyle("-fx-font-size: 12px;");
                body.getChildren().add(l);
            }
            HBox.setHgrow(body, Priority.ALWAYS);
            body.setMaxWidth(Double.MAX_VALUE);
            body.prefWidthProperty().bind(list.widthProperty().subtract(80));
            HBox row = new HBox(10, include, body);
            row.getStyleClass().add("review-cell");
            setGraphic(row);
        }
    }

    // ---------- Rodapé ----------

    private HBox footer() {
        Button discard = Ui.button("Descartar importação", Feather.X, Styles.BUTTON_OUTLINED);
        discard.setOnAction(e -> {
            if (Ui.confirm("Descartar importação?", "As questões extraídas e suas correções serão perdidas.", "Descartar")) {
                onDiscard.run();
            }
        });
        Button save = Ui.button("Salvar questões selecionadas", Feather.SAVE, Styles.ACCENT);
        save.setOnAction(e -> save());
        HBox f = new HBox(12, discard, Ui.hspacer(), selectedCount, save);
        f.setAlignment(Pos.CENTER_LEFT);
        return f;
    }

    private void updateCount() {
        long n = list.getItems().stream().filter(QuestionDraft::isIncluded).count();
        long invalid = list.getItems().stream().filter(q -> q.isIncluded() && !q.validate().isEmpty()).count();
        selectedCount.setText(n + " de " + list.getItems().size() + " selecionadas"
                + (invalid > 0 ? " · " + invalid + " incompletas" : ""));
    }

    private void save() {
        List<QuestionDraft> drafts = List.copyOf(list.getItems());
        long included = drafts.stream().filter(QuestionDraft::isIncluded).count();
        long invalid = drafts.stream().filter(q -> q.isIncluded() && !q.validate().isEmpty()).count();
        if (included == 0) {
            Ui.info("Nada selecionado", "Marque pelo menos uma questão para salvar.");
            return;
        }
        if (invalid > 0 && !Ui.confirm("Há questões incompletas",
                invalid + " questões selecionadas estão incompletas (por exemplo, sem alternativa correta) e serão "
                        + "ignoradas. Deseja salvar as demais?", "Salvar as válidas")) {
            return;
        }
        Async.run(() -> Services.IMPORTS.save(exam, result.file().getName(), result.fileHash(), result.pageCount(),
                result.ocrPages(), drafts), (SaveSummary s) -> {
            Ui.info("Importação concluída", s.saved() + " questões foram salvas no banco."
                    + (s.skippedDuplicates() > 0 ? " " + s.skippedDuplicates() + " duplicadas foram ignoradas." : ""));
            onSaved.run();
        });
    }
}
