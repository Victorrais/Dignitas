package br.com.dignitas.ui;

import atlantafx.base.theme.Styles;
import br.com.dignitas.domain.Subject;
import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.AlternativeDraft;
import br.com.dignitas.model.Texts;
import br.com.dignitas.service.DuplicateQuestionException;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Formulário de edição de questão, usado no Banco de Questões e na revisão da importação. */
public final class QuestionEditor {

    private final QuestionDraft draft;
    private final boolean showExam;
    private final Supplier<byte[]> cropper;

    private final TextField examName = new TextField();
    private final TextField examBoard = new TextField();
    private final TextField examYear = new TextField();
    private final TextField examRole = new TextField();
    private final TextField number = new TextField();
    private final ComboBox<String> subject = new ComboBox<>();
    private final TextArea baseText = new TextArea();
    private final TextField baseSource = new TextField();
    private final TextArea statement = new TextArea();
    private final CheckBox trueFalse = new CheckBox("Questão de Certo/Errado");
    private final CheckBox annulled = new CheckBox("Questão anulada");
    private final VBox altBox = new VBox(10);
    private final Button addAlt = Ui.button("Adicionar alternativa", Feather.PLUS, Styles.FLAT);
    private final ToggleGroup correctGroup = new ToggleGroup();
    private final List<AltRow> rows = new ArrayList<>();
    private final ImageView preview = new ImageView();
    private final ImageView basePreview = new ImageView();
    private final Label errors = Ui.wrapped("", "error-text");
    private byte[] image;
    private byte[] baseImage;

    private static final class AltRow {
        final Label letter;
        final TextArea text;
        final RadioButton correct;
        final Button remove;
        final HBox box;
        final ImageView thumb = new ImageView();
        byte[] image;

        AltRow(Label letter, TextArea text, RadioButton correct, Button remove, HBox box) {
            this.letter = letter;
            this.text = text;
            this.correct = correct;
            this.remove = remove;
            this.box = box;
        }

        void setImage(byte[] data) {
            image = data;
            thumb.setImage(Ui.image(data));
            thumb.setVisible(data != null);
            thumb.setManaged(data != null);
        }
    }

    private QuestionEditor(QuestionDraft draft, boolean showExam, List<String> subjects, Supplier<byte[]> cropper) {
        this.draft = draft;
        this.showExam = showExam;
        this.cropper = cropper;
        subject.getItems().setAll(subjects);
        image = draft.getImage();
        baseImage = draft.getBaseText() == null ? null : draft.getBaseText().getImage();
    }

    // ---------- Pontos de entrada ----------

    /** Edita o rascunho no lugar. Retorna true se o usuário confirmou. */
    public static boolean edit(QuestionDraft draft, boolean showExam, List<String> subjects, Supplier<byte[]> cropper) {
        return new QuestionEditor(draft, showExam, subjects, cropper).show(draft.getId() == null && showExam
                ? "Nova questão" : "Editar questão" + (draft.getNumber() == null ? "" : " " + draft.getNumber()));
    }

    /** Carrega uma questão do banco, abre o editor e salva. */
    public static void editPersisted(long id, Runnable onSaved) {
        Async.run(() -> new Object[]{Services.QUESTIONS.toDraft(Services.QUESTIONS.loadFull(id)), subjectNames()},
                r -> {
                    QuestionDraft d = (QuestionDraft) r[0];
                    @SuppressWarnings("unchecked") List<String> subjects = (List<String>) r[1];
                    if (edit(d, true, subjects, null)) save(d, onSaved);
                });
    }

    public static void createNew(Runnable onSaved) {
        Async.run(QuestionEditor::subjectNames, subjects -> {
            QuestionDraft d = new QuestionDraft();
            for (char c = 'A'; c <= 'E'; c++) d.addAlternative(String.valueOf(c), "", false);
            if (edit(d, true, subjects, null)) save(d, onSaved);
        });
    }

    private static List<String> subjectNames() {
        return Services.QUESTIONS.subjects().stream().map(Subject::getName).toList();
    }

    private static void save(QuestionDraft d, Runnable onSaved) {
        Async.run(() -> Services.QUESTIONS.save(d), id -> onSaved.run(), err -> {
            if (err instanceof DuplicateQuestionException) {
                Ui.info("Questão duplicada", err.getMessage());
            } else {
                Ui.showError(err);
            }
        });
    }

    // ---------- Formulário ----------

    private boolean show(String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(Ui.owner());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        pane.getStylesheets().add(getClass().getResource("/css/dignitas.css").toExternalForm());
        Ui.applyScale(pane);

        ScrollPane sp = new ScrollPane(form());
        sp.setFitToWidth(true);
        sp.prefWidthProperty().bind(Ui.scaleProperty().multiply(900));
        sp.prefHeightProperty().bind(Ui.scaleProperty().multiply(760));
        sp.getStyleClass().add("page-scroll");
        pane.setContent(sp);

        Button ok = (Button) pane.lookupButton(pane.getButtonTypes().getFirst());
        ok.getStyleClass().add(Styles.ACCENT);
        ok.addEventFilter(ActionEvent.ACTION, e -> {
            List<String> problems = collect(new QuestionDraft()).validate();
            if (!problems.isEmpty()) {
                errors.setText(String.join("\n", problems));
                e.consume();
            }
        });

        boolean confirmed = dialog.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent();
        if (confirmed) apply();
        return confirmed;
    }

    private Node form() {
        VBox f = new VBox(14);
        f.setStyle("-fx-padding: 0.286em 1.143em 1.143em 0.286em;");

        if (showExam) {
            ExamInfo e = draft.getExam();
            examName.setText(e == null ? "" : nz(e.name()));
            examBoard.setText(e == null ? "" : nz(e.board()));
            examYear.setText(e == null || e.year() == null ? "" : e.year().toString());
            examRole.setText(e == null ? "" : nz(e.role()));
            examName.setPromptText("Ex.: TJ-SP – Escrevente");
            examBoard.setPromptText("Ex.: VUNESP");
            examYear.setPromptText("2024");
            examRole.setPromptText("Cargo");
            examYear.setPrefWidth(90);
            HBox exam = new HBox(10, field("Concurso", examName), field("Banca", examBoard),
                    field("Ano", examYear), field("Cargo", examRole));
            HBox.setHgrow(exam.getChildren().getFirst(), Priority.ALWAYS);
            f.getChildren().add(exam);
        }

        number.setText(draft.getNumber() == null ? "" : draft.getNumber().toString());
        number.setPrefWidth(90);
        subject.setEditable(true);
        subject.setValue(draft.getSubjectName());
        subject.setPromptText("Ex.: Língua Portuguesa");
        subject.setMaxWidth(Double.MAX_VALUE);
        HBox meta = new HBox(10, field("Nº", number), field("Disciplina", subject));
        HBox.setHgrow(meta.getChildren().get(1), Priority.ALWAYS);
        f.getChildren().add(meta);

        BaseTextDraft bt = draft.getBaseText();
        baseText.setText(bt == null ? "" : nz(bt.getContent()));
        baseText.setWrapText(true);
        baseText.setPrefRowCount(6);
        baseText.setPromptText("Texto de apoio (opcional)");
        baseSource.setText(bt == null ? "" : nz(bt.getSource()));
        baseSource.setPromptText("Fonte: \"Disponível em…\", \"(Adaptado)\"");
        f.getChildren().addAll(field("Texto-base", baseText), baseImageSection(), baseSource,
                Ui.wrapped("Alterações no texto-base valem para todas as questões que o compartilham.", "hint"));

        statement.setText(draft.getStatement());
        statement.setWrapText(true);
        statement.setPrefRowCount(4);
        f.getChildren().add(field("Enunciado", statement));

        trueFalse.setSelected(draft.isTrueFalse());
        trueFalse.setOnAction(e -> {
            List<AlternativeDraft> alts = new ArrayList<>();
            if (trueFalse.isSelected()) {
                alts.add(new AlternativeDraft("C", "Certo", false));
                alts.add(new AlternativeDraft("E", "Errado", false));
            } else {
                for (char c = 'A'; c <= 'E'; c++) alts.add(new AlternativeDraft(String.valueOf(c), "", false));
            }
            setAlternatives(alts);
        });
        setAlternatives(draft.getAlternatives());
        addAlt.setOnAction(e -> {
            addRow(new AlternativeDraft("", "", false));
            relabel();
        });
        Label altTitle = Ui.label("Alternativas", "form-label");
        HBox altHeader = new HBox(12, altTitle, Ui.hspacer(), trueFalse);
        altHeader.setAlignment(Pos.CENTER_LEFT);
        f.getChildren().addAll(altHeader, Ui.wrapped("Selecione o botão da alternativa correta.", "hint"), altBox, addAlt);

        annulled.setSelected(draft.isAnnulled());
        f.getChildren().add(annulled);

        f.getChildren().add(imageSection());
        f.getChildren().add(errors);
        return f;
    }

    private Node imageSection() {
        preview.setPreserveRatio(true);
        preview.fitWidthProperty().bind(Ui.scaleProperty().multiply(420));
        preview.setImage(Ui.image(image));
        Button choose = Ui.button("Anexar imagem…", Feather.IMAGE, Styles.BUTTON_OUTLINED);
        choose.setOnAction(e -> {
            byte[] data = chooseImage();
            if (data != null) setImage(data);
        });
        Button crop = Ui.button("Recortar da página do PDF", Feather.CROP, Styles.BUTTON_OUTLINED);
        crop.setVisible(cropper != null);
        crop.setManaged(cropper != null);
        crop.setOnAction(e -> {
            byte[] data = cropper.get();
            if (data != null) setImage(data);
        });
        Button remove = Ui.button("Remover", Feather.TRASH_2, Styles.FLAT, Styles.DANGER);
        remove.setOnAction(e -> setImage(null));
        remove.disableProperty().bind(preview.imageProperty().isNull());
        return new VBox(8, Ui.label("Imagem (figura, gráfico, tabela)", "form-label"),
                new HBox(8, choose, crop, remove), preview);
    }

    private Node baseImageSection() {
        basePreview.setPreserveRatio(true);
        basePreview.fitWidthProperty().bind(Ui.scaleProperty().multiply(320));
        basePreview.setImage(Ui.image(baseImage));
        Button choose = Ui.button("Imagem do texto-base…", Feather.IMAGE, Styles.BUTTON_OUTLINED, Styles.SMALL);
        choose.setOnAction(e -> {
            byte[] data = chooseImage();
            if (data != null) {
                baseImage = data;
                basePreview.setImage(Ui.image(data));
            }
        });
        Button remove = Ui.button("Remover", Feather.TRASH_2, Styles.FLAT, Styles.DANGER, Styles.SMALL);
        remove.setOnAction(e -> {
            baseImage = null;
            basePreview.setImage(null);
        });
        remove.disableProperty().bind(basePreview.imageProperty().isNull());
        return new VBox(6, new HBox(8, choose, remove), basePreview);
    }

    /** Abre o seletor de arquivos e lê a imagem escolhida (ou null). */
    private static byte[] chooseImage() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg"));
        File file = fc.showOpenDialog(Ui.owner());
        if (file == null) return null;
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException ex) {
            Ui.showError(ex);
            return null;
        }
    }

    private void setImage(byte[] data) {
        image = data;
        preview.setImage(Ui.image(data));
    }

    private void setAlternatives(List<AlternativeDraft> alts) {
        rows.clear();
        altBox.getChildren().clear();
        correctGroup.getToggles().clear();
        alts.forEach(this::addRow);
        relabel();
    }

    private void addRow(AlternativeDraft a) {
        Label letter = Ui.label(a.getLetter(), "alt-letter");
        TextArea text = new TextArea(a.getText());
        text.setWrapText(true);
        text.setPrefRowCount(2);
        HBox.setHgrow(text, Priority.ALWAYS);
        RadioButton correct = new RadioButton("Correta");
        correct.setToggleGroup(correctGroup);
        correct.setSelected(a.isCorrect());
        Button remove = Ui.iconButton(Feather.X, "Remover alternativa", Styles.DANGER);
        Button pickImage = Ui.iconButton(Feather.IMAGE, "Anexar imagem à alternativa");
        Button dropImage = Ui.iconButton(Feather.SLASH, "Remover imagem da alternativa");
        VBox content = new VBox(6, text);
        HBox.setHgrow(content, Priority.ALWAYS);
        HBox box = new HBox(10, letter, content, new VBox(6, correct, new HBox(2, pickImage, dropImage, remove)));
        box.getStyleClass().add("alt-row");
        AltRow row = new AltRow(letter, text, correct, remove, box);
        row.thumb.setPreserveRatio(true);
        row.thumb.fitWidthProperty().bind(Ui.scaleProperty().multiply(220));
        content.getChildren().add(row.thumb);
        row.setImage(a.getImage());
        pickImage.setOnAction(e -> {
            byte[] data = chooseImage();
            if (data != null) row.setImage(data);
        });
        dropImage.setOnAction(e -> row.setImage(null));
        dropImage.disableProperty().bind(row.thumb.imageProperty().isNull());
        remove.setOnAction(e -> {
            rows.remove(row);
            altBox.getChildren().remove(box);
            correctGroup.getToggles().remove(correct);
            relabel();
        });
        rows.add(row);
        altBox.getChildren().add(box);
    }

    private void relabel() {
        boolean tf = trueFalse.isSelected();
        for (int i = 0; i < rows.size(); i++) {
            AltRow r = rows.get(i);
            r.letter.setText(tf ? (i == 0 ? "C" : "E") : String.valueOf((char) ('A' + i)));
            r.text.setEditable(!tf);
            r.text.setPrefRowCount(tf ? 1 : 2);
            r.remove.setVisible(!tf);
        }
        addAlt.setDisable(tf || rows.size() >= 5);
    }

    /** Copia os campos do formulário (exceto texto-base e imagem) para {@code target}. */
    private QuestionDraft collect(QuestionDraft target) {
        target.setStatement(statement.getText());
        target.getAlternatives().clear();
        for (AltRow r : rows) {
            target.addAlternative(r.letter.getText(), r.text.getText(), r.correct.isSelected()).setImage(r.image);
        }
        target.setAnnulled(annulled.isSelected());
        target.setTrueFalse(trueFalse.isSelected());
        return target;
    }

    private void apply() {
        collect(draft);
        try {
            draft.setNumber(number.getText().isBlank() ? null : Integer.parseInt(number.getText().strip()));
        } catch (NumberFormatException ignored) {
            // mantém o número anterior
        }
        String subj = subject.getEditor() != null ? subject.getEditor().getText() : subject.getValue();
        draft.setSubjectName(Texts.blankToNull(subj));

        BaseTextDraft current = draft.getBaseText();
        if (baseText.getText().isBlank() && baseImage == null) {
            draft.setBaseText(null);
        } else if (current != null) {
            current.setContent(baseText.getText());
            current.setSource(baseSource.getText());
            current.setImage(baseImage);
            current.getFigures().clear();
        } else {
            BaseTextDraft b = new BaseTextDraft(null, baseText.getText(), baseSource.getText());
            b.setImage(baseImage);
            draft.setBaseText(b);
        }
        draft.setImage(image);

        if (showExam) {
            Integer y = null;
            try {
                if (!examYear.getText().isBlank()) y = Integer.parseInt(examYear.getText().strip());
            } catch (NumberFormatException ignored) {
                // ano inválido: ignorado
            }
            ExamInfo info = new ExamInfo(Texts.blankToNull(examName.getText()), Texts.blankToNull(examBoard.getText()),
                    y, Texts.blankToNull(examRole.getText()));
            draft.setExam(info.isEmpty() ? null : info);
        }
    }

    private static VBox field(String label, Node control) {
        VBox v = new VBox(6, Ui.label(label, "form-label"), control);
        if (control instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
