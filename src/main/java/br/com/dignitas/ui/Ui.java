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
import java.util.Optional;

/** Pequenos construtores de componentes usados por todas as telas. */
public final class Ui {
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static Window owner;

    private Ui() {
    }

    static void setOwner(Window w) {
        owner = w;
    }

    public static Window owner() {
        return owner;
    }

    public static FontIcon icon(Ikon ikon, int size) {
        FontIcon i = new FontIcon(ikon);
        i.setIconSize(size);
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

    public static boolean confirm(String title, String text, String okText) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, new ButtonType(okText, ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(title);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get().getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }

    public static void info(String title, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        a.initOwner(owner);
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
