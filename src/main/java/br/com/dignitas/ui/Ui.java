package br.com.dignitas.ui;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/** Pequenos construtores de componentes usados por todas as telas. */
public final class Ui {
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static Window owner;

    /** Tamanho base do texto em px quando a escala é 1.0. Todo o CSS do app é relativo a ele (em). */
    public static final double BASE_FONT = 14;
    /** Escala atual da interface; telas e diálogos acompanham as mudanças. */
    private static final javafx.beans.property.DoubleProperty SCALE =
            new javafx.beans.property.SimpleDoubleProperty(br.com.dignitas.app.AppConfig.get().uiScale());

    private Ui() {
    }

    /** Tema escuro ativo? Janelas, diálogos e ícones acompanham. */
    private static final javafx.beans.property.BooleanProperty DARK =
            new javafx.beans.property.SimpleBooleanProperty(br.com.dignitas.app.AppConfig.get().darkTheme());
    private static final String CSS = resource("/css/dignitas.css");
    private static final String PALETTE_LIGHT = resource("/css/palette-light.css");
    private static final String PALETTE_DARK = resource("/css/palette-dark.css");
    private static final java.util.Map<String, Image> ICONS = new java.util.HashMap<>();

    private static String resource(String path) {
        return Objects.requireNonNull(Ui.class.getResource(path)).toExternalForm();
    }

    /** Coloca a folha do app e a paleta do tema atual numa lista de estilos, trocando a paleta com o tema. */
    public static void applyPalette(javafx.collections.ObservableList<String> stylesheets) {
        if (!stylesheets.contains(CSS)) stylesheets.add(CSS);
        Runnable sync = () -> {
            stylesheets.removeAll(PALETTE_LIGHT, PALETTE_DARK);
            stylesheets.add(DARK.get() ? PALETTE_DARK : PALETTE_LIGHT);
        };
        sync.run();
        DARK.addListener((o, a, b) -> sync.run());
    }

    public static javafx.beans.property.BooleanProperty darkProperty() {
        return DARK;
    }

    /** Ícone do app (o polvo) na versão do tema: {@code /icons/dignitas-light|dark-<tamanho>.png}. */
    public static Image appIcon(boolean dark, int size) {
        String path = "/icons/dignitas-" + (dark ? "dark" : "light") + "-" + size + ".png";
        return ICONS.computeIfAbsent(path, p -> new Image(Objects.requireNonNull(Ui.class.getResource(p)).toExternalForm()));
    }

    public static java.util.List<Image> appIcons(boolean dark) {
        return java.util.stream.IntStream.of(16, 24, 32, 48, 64, 128, 256).mapToObj(s -> appIcon(dark, s)).toList();
    }

    public static javafx.beans.property.DoubleProperty scaleProperty() {
        return SCALE;
    }

    public static double scale() {
        return SCALE.get();
    }

    /** Aplica (e mantém atualizado) o tamanho de fonte base num nó raiz: janela principal ou diálogo. */
    public static void applyScale(javafx.scene.Parent root) {
        // Identidade visual: folha de estilos do app e paleta do tema (claro/escuro).
        applyPalette(root.getStylesheets());
        root.styleProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> String.format(java.util.Locale.ROOT, "-fx-font-size: %.2fpx;", BASE_FONT * SCALE.get()), SCALE));
    }

    /**
     * Largura de exibição de uma imagem recortada do PDF (150 dpi): reduzida para algo próximo do tamanho
     * impresso e acompanhando a escala da interface, sem passar da largura disponível.
     */
    public static javafx.beans.binding.DoubleBinding imageWidth(javafx.scene.image.Image img,
                                                                 javafx.beans.value.ObservableDoubleValue available) {
        return javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(40, Math.min(img.getWidth() * 0.7 * SCALE.get(), available.get())), SCALE, available);
    }

    static void setOwner(Window w) {
        owner = w;
    }

    public static Window owner() {
        return owner;
    }

    public static FontIcon icon(Ikon ikon, int size) {
        FontIcon i = new FontIcon(ikon);
        // Acompanha a escala da interface (tamanhos pensados para 14 px).
        i.iconSizeProperty().bind(javafx.beans.binding.Bindings.createIntegerBinding(
                () -> (int) Math.round(size * SCALE.get()), SCALE));
        return i;
    }

    public static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    public static Label wrapped(String text, String... styleClasses) {
        Label l = label(text, styleClasses);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static Button button(String text, Ikon ikon, String... styles) {
        Button b = new Button(text, ikon == null ? null : icon(ikon, 16));
        b.getStyleClass().addAll(styles);
        return b;
    }

    public static Button iconButton(Ikon ikon, String tooltip, String... styles) {
        Button b = new Button(null, icon(ikon, 16));
        b.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        b.getStyleClass().addAll(styles);
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(children);
        box.getStyleClass().add("card");
        return box;
    }

    public static Label badge(String text, String kind) {
        Label l = label(text, "badge");
        l.setMinWidth(Region.USE_PREF_SIZE);
        if (kind != null) l.getStyleClass().add(kind);
        return l;
    }

    public static Region hspacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Cabeçalho padrão: título, subtítulo e ações à direita. */
    public static HBox header(String title, Label subtitle, Node... actions) {
        VBox texts = new VBox(2, label(title, "page-title"));
        if (subtitle != null) {
            subtitle.getStyleClass().add("page-subtitle");
            texts.getChildren().add(subtitle);
        }
        HBox h = new HBox(12, texts, hspacer());
        h.getChildren().addAll(actions);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Página rolável com o espaçamento padrão. */
    public static ScrollPane scrollPage(VBox content) {
        content.getStyleClass().add("page");
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    public static VBox emptyState(Ikon ikon, String title, String text, Node... actions) {
        VBox v = new VBox(icon(ikon, 44), label(title, "empty-title"), wrapped(text, "page-subtitle"));
        if (actions.length > 0) {
            HBox row = new HBox(10, actions);
            row.setAlignment(Pos.CENTER);
            v.getChildren().add(row);
        }
        v.getStyleClass().add("empty-state");
        ((Label) v.getChildren().get(2)).setMaxWidth(460);
        ((Label) v.getChildren().get(2)).setAlignment(Pos.CENTER);
        return v;
    }

    public static String format(LocalDateTime t) {
        return t == null ? "—" : DATE_TIME.format(t);
    }

    public static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    public static Image image(byte[] data) {
        return data == null ? null : new Image(new ByteArrayInputStream(data));
    }

    /** Faz a imagem abrir ampliada (tamanho original, com rolagem) ao ser clicada. */
    public static void zoomOnClick(javafx.scene.image.ImageView view) {
        view.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(view, new Tooltip("Clique para ampliar"));
        view.setOnMouseClicked(e -> {
            Image img = view.getImage();
            if (img == null) return;
            javafx.scene.image.ImageView full = new javafx.scene.image.ImageView(img);
            full.setPreserveRatio(true);
            ScrollPane sp = new ScrollPane(new StackPane(full));
            sp.setPannable(true);
            Dialog<Void> d = new Dialog<>();
            d.initOwner(owner);
            d.setTitle("Imagem");
            d.setResizable(true);
            d.getDialogPane().setContent(sp);
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            applyScale(d.getDialogPane());
            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            sp.setPrefSize(Math.min(img.getWidth() + 24, screen.getWidth() * 0.85),
                    Math.min(img.getHeight() + 24, screen.getHeight() * 0.8));
            d.show();
        });
    }

    public static boolean confirm(String title, String text, String okText) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, new ButtonType(okText, ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);
        a.initOwner(owner);
        applyScale(a.getDialogPane());
        a.setTitle(title);
        a.setHeaderText(title);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get().getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }

    public static void info(String title, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        a.initOwner(owner);
        applyScale(a.getDialogPane());
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    /** Mostra um erro de forma legível, com detalhes técnicos recolhidos. */
    public static void showError(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = e.getMessage() != null && !(e.getMessage().contains("Exception")) ? e.getMessage() : root.getMessage();
        if (msg == null) msg = root.getClass().getSimpleName();
        String header = "Algo deu errado";
        String friendly = constraintMessage(e);
        if (friendly != null) {
            msg = friendly;
            header = "Não foi possível salvar";
        }

        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(owner);
        applyScale(a.getDialogPane());
        a.setTitle("Erro");
        a.setHeaderText(header);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        TextArea details = new TextArea(sw.toString());
        details.setEditable(false);
        details.setPrefRowCount(12);
        a.getDialogPane().setExpandableContent(details);
        a.showAndWait();
        e.printStackTrace();
    }

    /**
     * Traduz violações de restrição do banco (registro duplicado, referência inválida) para uma frase
     * compreensível. Retorna null se o erro não for desse tipo.
     */
    static String constraintMessage(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            boolean constraint = t instanceof java.sql.SQLIntegrityConstraintViolationException
                    || t instanceof org.hibernate.exception.ConstraintViolationException;
            if (!constraint) continue;
            String text = String.valueOf(t.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if (text.contains("uk_import_file_hash")) {
                return "Este PDF já foi importado. Para importá-lo de novo, exclua a importação anterior na tela Importações.";
            }
            if (text.contains("uk_question_content_hash")) {
                return "Já existe uma questão com o mesmo enunciado e as mesmas alternativas no banco.";
            }
            if (text.contains("uk_subject_name")) return "Já existe uma disciplina com esse nome.";
            if (text.contains("duplicate")) return "Já existe um registro igual no banco de dados.";
            return "A operação foi recusada pelo banco de dados porque violaria a integridade dos dados "
                    + "(por exemplo, um registro relacionado não existe mais).";
        }
        return null;
    }
}
