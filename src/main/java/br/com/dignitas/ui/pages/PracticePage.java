package br.com.dignitas.ui.pages;

import atlantafx.base.theme.Styles;
import br.com.dignitas.domain.Alternative;
import br.com.dignitas.domain.BaseText;
import br.com.dignitas.domain.Question;
import br.com.dignitas.domain.Subject;
import br.com.dignitas.service.Dtos.AnswerResult;
import br.com.dignitas.service.Dtos.QuestionFilter;
import br.com.dignitas.service.Dtos.Sort;
import br.com.dignitas.service.Dtos.Status;
import br.com.dignitas.ui.*;
import br.com.dignitas.app.AppConfig;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PracticePage extends DbPage {
    private final ComboBox<Subject> subject = Combos.filter("Todas as disciplinas", Subject::getName);
    private final ComboBox<String> board = Combos.filter("Todas as bancas", s -> s);
    private final ComboBox<Integer> year = Combos.filter("Todos os anos", String::valueOf);
    private final ComboBox<Status> status = new ComboBox<>();
    private final Label subtitle = new Label();
    private final VBox holder = new VBox();

    private List<Long> queue;
    private int position;
    private Question question;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final List<HBox> altButtons = new ArrayList<>();
    private int selectedAlt = -1;
    private boolean answered;
    private Long explicitQuestion;
    private Button answerButton;
    /** Preferências de exibição do material de apoio (valem para todas as questões e ficam salvas). */
    private final BooleanProperty showText = new SimpleBooleanProperty(AppConfig.get().showSupportText());
    private final BooleanProperty showImages = new SimpleBooleanProperty(AppConfig.get().showSupportImages());
    private Button nextButton;

    public PracticePage(MainWindow app) {
        super(app);
        status.getItems().setAll(Status.values());
        status.setValue(Status.UNANSWERED);
        showText.addListener((o, a, b) -> {
            AppConfig.get().setShowSupportText(b);
            AppConfig.get().saveQuietly();
        });
        showImages.addListener((o, a, b) -> {
            AppConfig.get().setShowSupportImages(b);
            AppConfig.get().saveQuietly();
        });
    }

    /** Usado pelos atalhos do Início ("Refazer as que errei" etc.). */
    public void presetStatus(Status s) {
        status.setValue(s);
        queue = null;
    }

    @Override
    protected Node build() {
        Button apply = Ui.button("Aplicar filtros", Feather.FILTER, Styles.ACCENT);
        apply.setOnAction(e -> applyFilter(true));
        for (ComboBox<?> c : List.of(subject, board, year, status)) c.setPrefWidth(190);
        HBox filters = new HBox(10, subject, board, year, status, apply);
        filters.setAlignment(Pos.CENTER_LEFT);
        VBox filterCard = Ui.card(filters);
        filterCard.setStyle("-fx-padding: 1em 1.143em 1em 1.143em;");

        VBox page = new VBox(20, Ui.header("Resolver questões", subtitle), filterCard, holder);
        ScrollPane sp = Ui.scrollPage(page);
        sp.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        return sp;
    }

    @Override
    protected void load() {
        Async.run(() -> new Object[]{Services.QUESTIONS.subjects(), Services.QUESTIONS.boards(), Services.QUESTIONS.years()},
                r -> {
                    @SuppressWarnings("unchecked") List<Subject> s = (List<Subject>) r[0];
                    @SuppressWarnings("unchecked") List<String> b = (List<String>) r[1];
                    @SuppressWarnings("unchecked") List<Integer> y = (List<Integer>) r[2];
                    Combos.setOptions(subject, s);
                    Combos.setOptions(board, b);
                    Combos.setOptions(year, y);
                    if (queue == null) applyFilter(false);
                });
    }

    private QuestionFilter filter() {
        return new QuestionFilter(null, subject.getValue() == null ? null : subject.getValue().getId(),
                board.getValue(), year.getValue(), null, status.getValue(), Sort.RECENT, true);
    }

    /**
     * Monta a fila de questões do filtro. Se foi o usuário quem pediu, mostra a primeira; se foi o
     * carregamento automático e uma questão específica está aberta ("Ver questão"), mantém essa questão.
     */
    private void applyFilter(boolean userAction) {
        if (userAction) explicitQuestion = null;
        Async.run(() -> Services.QUESTIONS.ids(filter()), ids -> {
            queue = new ArrayList<>(ids);
            Collections.shuffle(queue);
            position = 0;
            subtitle.setText(ids.size() + (ids.size() == 1 ? " questão encontrada" : " questões encontradas"));
            if (explicitQuestion == null) showCurrent();
        });
    }

    public void openQuestion(long id) {
        explicitQuestion = id;
        Async.run(() -> Services.QUESTIONS.loadFull(id), q -> {
            if (Long.valueOf(id).equals(explicitQuestion)) render(q); // ignora respostas atrasadas
        });
    }

    private void showCurrent() {
        if (queue == null || position >= queue.size()) {
            Button restart = Ui.button("Recomeçar", Feather.REFRESH_CW, Styles.ACCENT);
            restart.setOnAction(e -> applyFilter(true));
            Button all = Ui.button("Ver todas as questões", Feather.LIST, Styles.BUTTON_OUTLINED);
            all.setOnAction(e -> {
                status.setValue(Status.ALL);
                applyFilter(true);
            });
            boolean empty = queue == null || queue.isEmpty();
            holder.getChildren().setAll(Ui.card(Ui.emptyState(empty ? Feather.SEARCH : Feather.AWARD,
                    empty ? "Nenhuma questão para este filtro" : "Você chegou ao fim desta lista!",
                    empty ? "Ajuste os filtros acima ou importe novas provas."
                            : "Todas as questões do filtro foram exibidas. Recomece ou escolha outro filtro.",
                    restart, all)));
            return;
        }
        long id = queue.get(position);
        Async.run(() -> Services.QUESTIONS.loadFull(id), q -> {
            if (explicitQuestion == null) render(q); // uma questão aberta pelo usuário tem prioridade
        });
    }

    private void next() {
        explicitQuestion = null;
        position++;
        showCurrent();
    }

    // ---------- Exibição da questão ----------

    private void render(Question q) {
        question = q;
        answered = false;
        altButtons.clear();
        selectedAlt = -1;

        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        if (q.getNumber() != null) meta.getChildren().add(Ui.badge("Questão " + q.getNumber(), "accent"));
        if (q.getExam() != null) meta.getChildren().add(Ui.badge(q.getExam().label(), null));
        if (q.getSubject() != null) meta.getChildren().add(Ui.badge(q.getSubject().getName(), null));
        if (q.isAnnulled()) meta.getChildren().add(Ui.badge("Anulada", "warning"));
        if (queue != null && explicitQuestion == null && !queue.isEmpty()) {
            meta.getChildren().add(Ui.label((position + 1) + " de " + queue.size(), "hint"));
        }
        ToggleButton flag = new ToggleButton(null, Ui.icon(Feather.BOOKMARK, 16));
        flag.getStyleClass().addAll(Styles.BUTTON_ICON);
        flag.setSelected(q.isFlagged());
        flag.setTooltip(new Tooltip("Marcar para revisar depois"));
        flag.setOnAction(e -> {
            boolean f = flag.isSelected();
            Async.run(() -> {
                Services.QUESTIONS.setFlagged(q.getId(), f);
                return f;
            }, x -> {
            });
        });
        Button edit = Ui.iconButton(Feather.EDIT_2, "Editar questão");
        edit.setOnAction(e -> QuestionEditor.editPersisted(q.getId(), () -> openQuestion(q.getId())));
        meta.getChildren().add(Ui.hspacer());
        BaseText support = q.getBaseText();
        boolean hasText = support != null && support.getContent() != null && !support.getContent().isBlank();
        boolean hasImage = q.getImage() != null || support != null && support.getImage() != null;
        if (hasText) meta.getChildren().add(supportToggle(Feather.FILE_TEXT, "texto de apoio", showText));
        if (hasImage) meta.getChildren().add(supportToggle(Feather.IMAGE, "imagens de apoio", showImages));
        meta.getChildren().addAll(edit, flag);

        VBox card = Ui.card(meta);
        card.setSpacing(16);

        BaseText bt = q.getBaseText();
        if (bt != null) {
            Label title = Ui.label("TEXTO DE APOIO", "nav-section");
            title.setStyle("-fx-padding: 0;");
            VBox base = new VBox(title);
            BooleanBinding anyVisible = Bindings.createBooleanBinding(() -> false);
            if (bt.getContent() != null && !bt.getContent().isBlank()) {
                Node text = Ui.wrapped(bt.getContent(), "base-text-body");
                bindVisible(text, showText);
                base.getChildren().add(text);
                anyVisible = anyVisible.or(showText);
            }
            if (bt.getImage() != null) {
                Node img = picture(bt.getImage(), card, 76);
                bindVisible(img, showImages);
                base.getChildren().add(img);
                anyVisible = anyVisible.or(showImages);
            }
            if (bt.getSource() != null) {
                Node source = Ui.wrapped(bt.getSource(), "source");
                bindVisible(source, anyVisible);
                base.getChildren().add(source);
            }
            BooleanBinding shown = anyVisible;
            title.textProperty().bind(Bindings.when(shown).then("TEXTO DE APOIO")
                    .otherwise("TEXTO DE APOIO (oculto · clique para mostrar)"));
            title.setCursor(javafx.scene.Cursor.HAND);
            title.setOnMouseClicked(e -> {
                if (!shown.get()) {
                    showText.set(true);
                    showImages.set(true);
                }
            });
            base.getStyleClass().add("base-text");
            card.getChildren().add(base);
        }
        if (q.getImage() != null) {
            Node img = picture(q.getImage(), card, 44);
            bindVisible(img, showImages);
            card.getChildren().add(img);
        }
        if (!q.getStatement().isBlank()) card.getChildren().add(Ui.wrapped(q.getStatement(), "statement"));

        VBox alts = new VBox(10);
        for (Alternative a : q.getAlternatives()) {
            alts.getChildren().add(altButton(a));
        }
        card.getChildren().add(alts);

        VBox feedback = new VBox();
        answerButton = Ui.button("Responder", Feather.CHECK, Styles.ACCENT);
        answerButton.setDisable(true);
        answerButton.setOnAction(e -> answer(feedback));
        Button skip = Ui.button("Pular", Feather.SKIP_FORWARD, Styles.BUTTON_OUTLINED);
        skip.setOnAction(e -> next());
        nextButton = Ui.button("Próxima questão", Feather.ARROW_RIGHT, Styles.ACCENT);
        nextButton.setContentDisplay(ContentDisplay.RIGHT);
        nextButton.setVisible(false);
        nextButton.setManaged(false);
        nextButton.setOnAction(e -> next());
        HBox actions = new HBox(10, answerButton, skip, Ui.hspacer(), Ui.label("Atalhos: A–E e Enter", "hint"), nextButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        skip.visibleProperty().bind(nextButton.visibleProperty().not());
        skip.managedProperty().bind(skip.visibleProperty());
        answerButton.visibleProperty().bind(nextButton.visibleProperty().not());
        answerButton.managedProperty().bind(answerButton.visibleProperty());

        card.getChildren().addAll(feedback, actions);
        holder.getChildren().setAll(card);
    }

    /** Alternativa como cartão clicável (um HBox quebra o texto longo corretamente). */
    /** Imagem no tamanho natural, reduzida para caber na largura do cartão. */
    /** Botão de ícone que mostra/oculta parte do material de apoio. */
    private static ToggleButton supportToggle(org.kordamp.ikonli.Ikon icon, String what, BooleanProperty visible) {
        ToggleButton t = new ToggleButton(null, Ui.icon(icon, 16));
        t.getStyleClass().addAll(Styles.BUTTON_ICON);
        t.selectedProperty().bindBidirectional(visible);
        Tooltip tip = new Tooltip();
        tip.textProperty().bind(Bindings.when(visible).then("Ocultar " + what).otherwise("Mostrar " + what));
        t.setTooltip(tip);
        return t;
    }

    private static void bindVisible(Node n, javafx.beans.value.ObservableBooleanValue visible) {
        n.visibleProperty().bind(visible);
        n.managedProperty().bind(visible);
    }

    private static Node picture(byte[] data, VBox card, double margin) {
        ImageView iv = new ImageView(Ui.image(data));
        iv.setPreserveRatio(true);
        iv.fitWidthProperty().bind(Ui.imageWidth(iv.getImage(), card.widthProperty().subtract(margin)));
        Ui.zoomOnClick(iv);
        iv.getStyleClass().add("question-image");
        StackPane frame = new StackPane(iv);
        frame.setAlignment(Pos.CENTER_LEFT);
        return frame;
    }

    private HBox altButton(Alternative a) {
        Label letter = Ui.label(a.getLetter(), "alt-letter");
        VBox body = new VBox(8);
        if (!a.getStatement().isBlank()) {
            Label text = Ui.wrapped(a.getStatement(), "alt-text");
            text.setMaxWidth(Double.MAX_VALUE);
            body.getChildren().add(text);
        }
        if (a.getImage() != null) {
            ImageView iv = new ImageView(Ui.image(a.getImage()));
            iv.setPreserveRatio(true);
            iv.fitWidthProperty().bind(Ui.imageWidth(iv.getImage(), Ui.scaleProperty().multiply(480)));
            // Ampliar sem marcar a alternativa: o clique na imagem não chega ao cartão.
            Ui.zoomOnClick(iv);
            iv.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);
            body.getChildren().add(iv);
        }
        body.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(body, Priority.ALWAYS);
        HBox b = new HBox(14, letter, body);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().add("alt-button");
        b.setUserData(a);
        int index = altButtons.size();
        b.setOnMouseClicked(e -> select(index));
        altButtons.add(b);
        return b;
    }

    private void select(int index) {
        if (answered) return;
        selectedAlt = index;
        for (int i = 0; i < altButtons.size(); i++) altButtons.get(i).pseudoClassStateChanged(SELECTED, i == index);
        answerButton.setDisable(false);
    }

    private void answer(VBox feedback) {
        if (selectedAlt < 0 || answered) return;
        Alternative chosen = (Alternative) altButtons.get(selectedAlt).getUserData();
        answered = true;
        answerButton.setDisable(true);
        Async.run(() -> Services.ATTEMPTS.answer(question.getId(), chosen.getId()), r -> showResult(r, chosen, feedback));
    }

    private void showResult(AnswerResult r, Alternative chosen, VBox feedback) {
        for (HBox b : altButtons) {
            Alternative a = (Alternative) b.getUserData();
            b.setCursor(javafx.scene.Cursor.DEFAULT);
            if (a.getLetter().equals(r.correctLetter())) b.getStyleClass().add("correct");
            else if (a == chosen) b.getStyleClass().add("wrong");
        }
        String text;
        String kind;
        if (question.isAnnulled()) {
            text = "Questão anulada: qualquer resposta é considerada correta.";
            kind = "warning";
        } else if (r.correct()) {
            text = "Resposta correta! Muito bem.";
            kind = "success";
        } else {
            text = "Resposta errada. A alternativa correta é a " + r.correctLetter() + ".";
            kind = "danger";
        }
        Label msg = Ui.label(text, "badge", kind);
        msg.setGraphic(Ui.icon(r.correct() ? Feather.CHECK_CIRCLE : Feather.X_CIRCLE, 16));
        msg.setStyle("-fx-font-size: 1em; -fx-padding: 0.714em 1.143em 0.714em 1.143em; -fx-background-radius: 8;");
        feedback.getChildren().setAll(msg);
        nextButton.setVisible(true);
        nextButton.setManaged(true);
        nextButton.requestFocus();
    }

    private void onKey(KeyEvent e) {
        if (question == null || e.getTarget() instanceof TextInputControl) return;
        KeyCode k = e.getCode();
        int idx = -1;
        if (k.isLetterKey()) idx = k.getName().charAt(0) - 'A';
        if (k.isDigitKey()) idx = k.getName().charAt(k.getName().length() - 1) - '1';
        if (question.getAlternatives().size() == 2 && (k == KeyCode.C || k == KeyCode.E)) idx = k == KeyCode.C ? 0 : 1;
        if (!answered && idx >= 0 && idx < altButtons.size()) {
            select(idx);
            e.consume();
        } else if (k == KeyCode.ENTER) {
            if (answered) next();
            else if (answerButton != null && !answerButton.isDisabled()) answerButton.fire();
            e.consume();
        }
    }
}
