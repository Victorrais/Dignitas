package br.com.dignitas.ui.pages;

import atlantafx.base.theme.Styles;
import br.com.dignitas.domain.Subject;
import br.com.dignitas.extraction.*;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.Texts;
import br.com.dignitas.service.Dtos.ImportRow;
import br.com.dignitas.ui.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Assistente de importação: 1) arquivo e dados do concurso, 2) processamento, 3) revisão. */
public class ImportPage extends DbPage {
    private final TextField pdfField = new TextField();
    private final TextField keyField = new TextField();
    private final TextField examName = new TextField();
    private final TextField examBoard = new TextField();
    private final TextField examYear = new TextField();
    private final TextField examRole = new TextField();
    private final ComboBox<String> defaultSubject = new ComboBox<>();
    private final ComboBox<OcrMode> mode = new ComboBox<>();
    private final Label ocrInfo = new Label();
    private final Label formError = Ui.wrapped("", "error-text");

    private final ProgressBar progress = new ProgressBar(0);
    private final Label progressText = Ui.label("", "page-subtitle");
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private final StackPane stepHolder = new StackPane();
    private final HBox steps = new HBox();
    private Node formStep;
    private Node progressStep;
    private ReviewPane review;
    private File pdf;
    private File answerKey;

    public ImportPage(MainWindow app) {
        super(app);
    }

    @Override
    protected Node build() {
        steps.getStyleClass().add("steps");
        formStep = Ui.scrollPage(new VBox(20, form()));
        progressStep = progressView();
        VBox.setVgrow(stepHolder, Priority.ALWAYS);
        VBox page = new VBox(16, Ui.header("Importar PDF",
                new Label("Extraia questões de provas em PDF de texto ou digitalizadas")), steps, stepHolder);
        page.setStyle("-fx-padding: 28 32 20 32;");
        showStep(0);
        return page;
    }

    @Override
    protected void load() {
        updateOcrInfo();
        Async.run(() -> Services.QUESTIONS.subjects().stream().map(Subject::getName).toList(),
                names -> {
                    String v = defaultSubject.getEditor().getText();
                    defaultSubject.getItems().setAll(names);
                    defaultSubject.getEditor().setText(v);
                });
    }

    private void showStep(int index) {
        steps.getChildren().clear();
        String[] names = {"Arquivo e concurso", "Leitura do PDF", "Revisão"};
        for (int i = 0; i < names.length; i++) {
            if (i > 0) steps.getChildren().add(new Region() {{
                getStyleClass().add("step-line");
            }});
            Label num = Ui.label(i < index ? "✓" : String.valueOf(i + 1), "step-number");
            HBox step = new HBox(num, Ui.label(names[i], "step-label"));
            step.getStyleClass().add("step");
            if (i == index) step.getStyleClass().add("active");
            if (i < index) step.getStyleClass().add("done");
            steps.getChildren().add(step);
        }
        stepHolder.getChildren().setAll(switch (index) {
            case 0 -> formStep;
            case 1 -> progressStep;
            default -> review;
        });
    }

    // ---------- Passo 1 ----------

    private Node form() {
        pdfField.setEditable(false);
        pdfField.setPromptText("Nenhum arquivo selecionado");
        keyField.setEditable(false);
        keyField.setPromptText("Opcional — permite marcar as respostas automaticamente");
        Button choosePdf = Ui.button("Escolher…", Feather.FILE, Styles.BUTTON_OUTLINED);
        choosePdf.setOnAction(e -> {
            File f = choose("Prova em PDF");
            if (f != null) {
                pdf = f;
                pdfField.setText(f.getAbsolutePath());
                if (examName.getText().isBlank()) examName.setText(guessName(f.getName()));
            }
        });
        Button chooseKey = Ui.button("Escolher…", Feather.FILE, Styles.BUTTON_OUTLINED);
        chooseKey.setOnAction(e -> {
            File f = choose("Gabarito em PDF");
            if (f != null) {
                answerKey = f;
                keyField.setText(f.getAbsolutePath());
            }
        });
        Button clearKey = Ui.iconButton(Feather.X, "Remover gabarito");
        clearKey.setOnAction(e -> {
            answerKey = null;
            keyField.clear();
        });

        examName.setPromptText("Ex.: TJ-SP – Escrevente Técnico Judiciário");
        examBoard.setPromptText("Ex.: VUNESP");
        examYear.setPromptText("2024");
        examRole.setPromptText("Cargo (opcional)");
        defaultSubject.setEditable(true);
        defaultSubject.setPromptText("Usada quando a prova não indicar a disciplina");
        defaultSubject.setMaxWidth(Double.MAX_VALUE);
        mode.getItems().setAll(OcrMode.values());
        mode.setValue(OcrMode.AUTO);
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.setOnAction(e -> updateOcrInfo());
        ocrInfo.setWrapText(true);

        GridPane grid = new GridPane(14, 12);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(150);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);
        int r = 0;
        grid.addRow(r++, Ui.label("Prova (PDF) *", "form-label"), row(pdfField, choosePdf));
        grid.addRow(r++, Ui.label("Gabarito (PDF)", "form-label"), row(keyField, chooseKey, clearKey));
        grid.addRow(r++, Ui.label("Concurso", "form-label"), examName);
        grid.addRow(r++, Ui.label("Banca", "form-label"), row(examBoard, Ui.label("Ano", "form-label"), examYear));
        grid.addRow(r++, Ui.label("Cargo", "form-label"), examRole);
        grid.addRow(r++, Ui.label("Disciplina padrão", "form-label"), defaultSubject);
        grid.addRow(r++, Ui.label("Modo de leitura", "form-label"), mode);
        grid.add(ocrInfo, 1, r++);
        examYear.setPrefWidth(100);
        examYear.setMaxWidth(100);

        Button process = Ui.button("Processar PDF", Feather.ARROW_RIGHT, Styles.ACCENT, Styles.LARGE);
        process.setContentDisplay(ContentDisplay.RIGHT);
        process.setOnAction(e -> start());

        VBox tips = new VBox(6, Ui.label("Como funciona", "card-title"),
                Ui.wrapped("1. O Dignitas lê o texto de cada página. Páginas digitalizadas passam pelo OCR (Tesseract).", "hint"),
                Ui.wrapped("2. As questões são identificadas por padrões comuns às bancas (\"Questão 1\", \"(A)\", \"Texto I\"…), "
                        + "inclusive em provas de duas colunas.", "hint"),
                Ui.wrapped("3. Você revisa e corrige tudo antes de salvar no banco. Nada é gravado antes disso.", "hint"));

        HBox bottom = new HBox(12, formError, Ui.hspacer(), process);
        bottom.setAlignment(Pos.CENTER_LEFT);
        return new VBox(20, Ui.card(grid), Ui.card(tips), bottom);
    }

    private static HBox row(Node... nodes) {
        HBox h = new HBox(8, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nodes[0], Priority.ALWAYS);
        return h;
    }

    private File choose(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        if (pdf != null) fc.setInitialDirectory(pdf.getParentFile());
        return fc.showOpenDialog(Ui.owner());
    }

    private static String guessName(String fileName) {
        return fileName.replaceFirst("(?i)\\.pdf$", "").replaceAll("[_]+", " ").strip();
    }

    private void updateOcrInfo() {
        boolean installed = TessdataManager.isInstalled();
        if (mode.getValue() == OcrMode.TEXT_ONLY) {
            ocrInfo.setText("Páginas digitalizadas serão ignoradas.");
            ocrInfo.setGraphic(Ui.icon(Feather.INFO, 14));
        } else if (installed) {
            ocrInfo.setText("OCR pronto (pacote de português instalado).");
            ocrInfo.setGraphic(Ui.icon(Feather.CHECK_CIRCLE, 14));
        } else {
            ocrInfo.setText("O pacote de OCR ainda não foi baixado; ele será oferecido se for necessário.");
            ocrInfo.setGraphic(Ui.icon(Feather.ALERT_TRIANGLE, 14));
        }
        ocrInfo.getStyleClass().setAll("label", "hint");
    }

    private ExamInfo examInfo() {
        Integer y = null;
        if (!examYear.getText().isBlank()) {
            try {
                y = Integer.parseInt(examYear.getText().strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Ano inválido: " + examYear.getText());
            }
        }
        return new ExamInfo(Texts.blankToNull(examName.getText()), Texts.blankToNull(examBoard.getText()), y,
                Texts.blankToNull(examRole.getText()));
    }

    private void start() {
        formError.setText("");
        if (pdf == null || !pdf.isFile()) {
            formError.setText("Escolha o arquivo PDF da prova.");
            return;
        }
        ExamInfo exam;
        try {
            exam = examInfo();
        } catch (IllegalArgumentException e) {
            formError.setText(e.getMessage());
            return;
        }
        File file = pdf;
        Async.run(() -> Services.IMPORTS.findByFileHash(Texts.sha256(Files.readAllBytes(file.toPath()))),
                (Optional<ImportRow> existing) -> {
                    if (existing.isPresent()) {
                        ImportRow r = existing.get();
                        Ui.info("Arquivo já importado", "Este PDF já foi importado em " + Ui.format(r.importedAt())
                                + " (" + r.questions() + " questões). Para importá-lo de novo, exclua a importação "
                                + "anterior na tela Importações.");
                        return;
                    }
                    askOcrThenRun(exam);
                });
    }

    private void askOcrThenRun(ExamInfo exam) {
        OcrMode m = mode.getValue();
        if (m == OcrMode.TEXT_ONLY || TessdataManager.isInstalled()) {
            run(exam, m != OcrMode.TEXT_ONLY);
            return;
        }
        if (m == OcrMode.ALWAYS) {
            offerOcrDownload(exam, "O modo \"Forçar OCR\" precisa do pacote de idioma português do Tesseract.");
            return;
        }
        // Automático: só pergunta se o PDF tiver páginas digitalizadas.
        File file = pdf;
        Async.run(() -> new ImportPipeline().pagesNeedingOcr(file), scanned -> {
            if (scanned.isEmpty()) {
                run(exam, false);
            } else {
                offerOcrDownload(exam, scanned.size() + " página(s) deste PDF parecem digitalizadas "
                        + (scanned.size() <= 10 ? scanned : "") + " e precisam de OCR.");
            }
        });
    }

    private void offerOcrDownload(ExamInfo exam, String reason) {
        ButtonType download = new ButtonType("Baixar agora", ButtonBar.ButtonData.YES);
        ButtonType without = new ButtonType("Continuar sem OCR", ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, reason + "\n\nPara isso, o Dignitas precisa baixar uma "
                + "única vez o pacote de idioma português do Tesseract (repositório oficial tesseract-ocr no GitHub).",
                download, without, ButtonType.CANCEL);
        a.initOwner(Ui.owner());
        a.setHeaderText("Baixar o pacote de OCR?");
        ButtonType choice = a.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == download) {
            OcrSetup.download(ok -> {
                updateOcrInfo();
                run(exam, ok);
            });
        } else if (choice == without) {
            run(exam, false);
        }
    }

    // ---------- Passo 2 ----------

    private Node progressView() {
        progress.setPrefWidth(520);
        Button cancel = Ui.button("Cancelar", Feather.X, Styles.BUTTON_OUTLINED);
        cancel.setOnAction(e -> cancelled.set(true));
        VBox box = Ui.card(Ui.icon(Feather.CPU, 36), Ui.label("Lendo o PDF…", "card-title"), progress, progressText, cancel);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(640);
        box.setStyle("-fx-padding: 40;");
        StackPane s = new StackPane(box);
        StackPane.setAlignment(box, Pos.TOP_CENTER);
        return s;
    }

    private void run(ExamInfo exam, boolean useOcr) {
        cancelled.set(false);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressText.setText("Abrindo arquivo…");
        showStep(1);
        File file = pdf;
        File key = answerKey;
        OcrMode m = useOcr ? mode.getValue() : OcrMode.TEXT_ONLY;
        String fallbackSubject = Texts.blankToNull(defaultSubject.getEditor().getText());

        Async.run(() -> {
            OcrEngine ocr = useOcr ? new OcrEngine(TessdataManager.dir()) : null;
            ImportPipeline.Result r = new ImportPipeline().run(file, key, m, ocr,
                    (done, total, msg) -> Platform.runLater(() -> {
                        progress.setProgress(total == 0 ? -1 : (double) done / total);
                        progressText.setText(msg);
                    }), cancelled::get);
            List<QuestionDraft> qs = r.questions();
            Set<String> inDb = Services.QUESTIONS.existingHashes(qs.stream().map(QuestionDraft::contentHash).toList());
            for (QuestionDraft q : qs) {
                if (q.getSubjectName() == null) q.setSubjectName(fallbackSubject);
                if (inDb.contains(q.contentHash())) {
                    q.setDuplicate(true);
                    q.setIncluded(false);
                    q.getWarnings().add("Já existe no banco de questões.");
                }
            }
            return r;
        }, r -> {
            try {
                review = new ReviewPane(r, exam, this::finished, this::discard);
                showStep(2);
            } catch (Exception e) {
                Ui.showError(e);
                showStep(0);
            }
        }, err -> {
            showStep(0);
            if (!(err instanceof ImportPipeline.CancellationException)) Ui.showError(err);
        });
    }

    // ---------- Passo 3 ----------

    private void finished() {
        discard();
        app.go(MainWindow.Nav.BANK);
    }

    private void discard() {
        if (review != null) review.close();
        review = null;
        pdf = null;
        answerKey = null;
        pdfField.clear();
        keyField.clear();
        examName.clear();
        examBoard.clear();
        examYear.clear();
        examRole.clear();
        showStep(0);
    }
}
