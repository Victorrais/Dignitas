package br.com.dignitas.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import br.com.dignitas.app.AppConfig;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.persistence.SchemaInstaller;
import br.com.dignitas.service.Dtos.Status;
import br.com.dignitas.ui.pages.*;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import br.com.dignitas.app.AppConfig;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/** Janela principal: barra lateral de navegação + área de conteúdo. */
public class MainWindow {

    public enum Nav {
        DASHBOARD("Início", Feather.HOME, DashboardPage::new),
        PRACTICE("Resolver questões", Feather.EDIT_3, PracticePage::new),
        HISTORY("Histórico", Feather.CLOCK, HistoryPage::new),
        STATS("Estatísticas", Feather.BAR_CHART_2, StatsPage::new),
        BANK("Banco de questões", Feather.DATABASE, BankPage::new),
        IMPORT("Importar PDF", Feather.UPLOAD, ImportPage::new),
        IMPORTS("Importações", Feather.ARCHIVE, ImportsPage::new),
        SETTINGS("Configurações", Feather.SETTINGS, SettingsPage::new);

        final String label;
        final Ikon icon;
        final Function<MainWindow, Page> factory;

        Nav(String label, Ikon icon, Function<MainWindow, Page> factory) {
            this.label = label;
            this.icon = icon;
            this.factory = factory;
        }
    }

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Map<Nav, Page> pages = new EnumMap<>(Nav.class);
    private final Map<Nav, ToggleButton> navButtons = new EnumMap<>(Nav.class);
    private final Region statusDot = new Region();
    private final Label statusText = new Label("Conectando…");
    private Nav current;
    private volatile boolean connected;
    private final javafx.beans.property.BooleanProperty collapsed =
            new javafx.beans.property.SimpleBooleanProperty(AppConfig.get().sidebarCollapsed());
    private VBox side;

    public MainWindow() {
        root.getStyleClass().add("app-root");
        Ui.applyScale(root);
        root.setLeft(sidebar());
        root.setCenter(content);
        collapsed.addListener((o, a, b) -> {
            applyCollapsed(b);
            AppConfig.get().setSidebarCollapsed(b);
            AppConfig.get().saveQuietly();
        });
        applyCollapsed(collapsed.get());
    }

    /** Ctrl+B recolhe a barra lateral; Ctrl + / Ctrl − / Ctrl 0 ajustam o tamanho da interface. */
    public void installShortcuts(javafx.scene.Scene scene) {
        var keys = scene.getAccelerators();
        keys.put(javafx.scene.input.KeyCombination.keyCombination("Shortcut+B"), this::toggleSidebar);
        keys.put(javafx.scene.input.KeyCombination.keyCombination("Shortcut+Equals"), () -> zoom(+0.05));
        keys.put(javafx.scene.input.KeyCombination.keyCombination("Shortcut+Add"), () -> zoom(+0.05));
        keys.put(javafx.scene.input.KeyCombination.keyCombination("Shortcut+Minus"), () -> zoom(-0.05));
        keys.put(javafx.scene.input.KeyCombination.keyCombination("Shortcut+Subtract"), () -> zoom(-0.05));
        keys.put(javafx.scene.input.KeyCombination.keyCombination("Shortcut+Digit0"), () -> setScale(0.85));
    }

    public void toggleSidebar() {
        collapsed.set(!collapsed.get());
    }

    private void zoom(double delta) {
        setScale(Math.round((Ui.scale() + delta) * 100) / 100.0);
    }

    /** Muda a escala da interface (0,70 a 1,50) e grava a preferência. */
    public static void setScale(double scale) {
        double s = Math.max(0.7, Math.min(1.5, scale));
        Ui.scaleProperty().set(s);
        AppConfig.get().setUiScale(s);
        AppConfig.get().saveQuietly();
    }

    private void applyCollapsed(boolean c) {
        side.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), c);
        for (ToggleButton b : navButtons.values()) {
            b.setContentDisplay(c ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY : javafx.scene.control.ContentDisplay.LEFT);
            b.setAlignment(c ? Pos.CENTER : Pos.CENTER_LEFT);
        }
        side.lookupAll(".collapsible").forEach(n -> {
            n.setVisible(!c);
            n.setManaged(!c);
        });
    }

    public Parent root() {
        return root;
    }

    private VBox sidebar() {
        javafx.scene.image.ImageView octopus = new javafx.scene.image.ImageView();
        octopus.imageProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                () -> Ui.appIcon(Ui.darkProperty().get(), 128), Ui.darkProperty()));
        octopus.setPreserveRatio(true);
        octopus.setSmooth(true);
        octopus.fitWidthProperty().bind(Ui.scaleProperty().multiply(40));
        StackPane logo = new StackPane(octopus);
        logo.getStyleClass().addAll("brand-logo", "collapsible");
        VBox names = new VBox(0, Ui.label("Dignitas", "brand-name"), Ui.label("Questões de concursos", "brand-tagline"));
        names.getStyleClass().add("collapsible");
        Button toggle = Ui.iconButton(Feather.SIDEBAR, "Recolher/expandir a barra lateral (Ctrl+B)");
        toggle.setOnAction(e -> toggleSidebar());
        HBox brand = new HBox(toggle, logo, names);
        brand.getStyleClass().add("brand");

        VBox nav = new VBox(2);
        ToggleGroup group = new ToggleGroup();
        nav.getChildren().add(Ui.label("ESTUDAR", "nav-section", "collapsible"));
        for (Nav n : Nav.values()) {
            if (n == Nav.BANK) nav.getChildren().add(Ui.label("GERENCIAR", "nav-section", "collapsible"));
            if (n == Nav.SETTINGS) continue;
            nav.getChildren().add(navButton(n, group));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        statusDot.getStyleClass().add("status-dot");
        statusText.getStyleClass().addAll("hint", "collapsible");
        statusText.setWrapText(true);
        HBox status = new HBox(statusDot, statusText);
        status.getStyleClass().add("connection-status");
        status.setOnMouseClicked(e -> go(Nav.SETTINGS));

        Tooltip.install(status, new Tooltip("Banco de dados"));
        side = new VBox(brand, nav, spacer, navButton(Nav.SETTINGS, group), status);
        side.getStyleClass().add("sidebar");
        side.setSpacing(6);
        return side;
    }

    private ToggleButton navButton(Nav n, ToggleGroup group) {
        ToggleButton b = new ToggleButton(n.label, Ui.icon(n.icon, 18));
        b.getStyleClass().add("nav-item");
        b.setTooltip(new Tooltip(n.label));
        b.setToggleGroup(group);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        // Mantém sempre um item selecionado.
        b.setOnAction(e -> {
            if (!b.isSelected()) b.setSelected(true);
            go(n);
        });
        navButtons.put(n, b);
        return b;
    }

    @SuppressWarnings("unchecked")
    public <T extends Page> T page(Nav nav) {
        return (T) pages.computeIfAbsent(nav, n -> n.factory.apply(this));
    }

    public void go(Nav nav) {
        if (current != null && current != nav) page(current).onHide();
        current = nav;
        navButtons.get(nav).setSelected(true);
        Page p = page(nav);
        content.getChildren().setAll(p.root());
        p.onShow();
    }

    /** Recarrega a tela atual (ex.: após reconectar). */
    public void refresh() {
        if (current != null) page(current).onShow();
    }

    public void openQuestion(long questionId) {
        go(Nav.PRACTICE);
        this.<PracticePage>page(Nav.PRACTICE).openQuestion(questionId);
    }

    public void practice(Status status) {
        this.<PracticePage>page(Nav.PRACTICE).presetStatus(status);
        go(Nav.PRACTICE);
    }

    public boolean isConnected() {
        return connected;
    }

    /** Cria as tabelas se necessário e inicializa o Hibernate com as configurações salvas. */
    public void connect(Runnable onDone) {
        AppConfig cfg = AppConfig.get();
        setStatus(false, "Conectando a " + cfg.dbHost() + "…");
        Async.run(() -> {
            SchemaInstaller.install(cfg.jdbcUrl(), cfg.dbUser(), cfg.dbPassword());
            Jpa.init(cfg);
            return true;
        }, ok -> {
            setStatus(true, "MySQL conectado · " + cfg.dbName());
            if (onDone != null) onDone.run();
            refresh();
        }, err -> {
            Jpa.shutdown();
            Throwable root = err;
            while (root.getCause() != null) root = root.getCause();
            setStatus(false, "MySQL desconectado");
            System.err.println("Falha ao conectar: " + root);
            if (onDone != null) onDone.run();
            refresh();
        });
    }

    public void markConnected(String label) {
        setStatus(true, label);
        refresh();
    }

    private void setStatus(boolean online, String text) {
        connected = online;
        statusDot.getStyleClass().remove("online");
        if (online) statusDot.getStyleClass().add("online");
        statusText.setText(text);
    }

    public static void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        Ui.darkProperty().set(dark);
    }
}
