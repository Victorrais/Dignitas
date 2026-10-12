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
import javafx.scene.control.Label;
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

    public MainWindow() {
        root.getStyleClass().add("app-root");
        root.setLeft(sidebar());
        root.setCenter(content);
    }

    public Parent root() {
        return root;
    }

    private VBox sidebar() {
        StackPane logo = new StackPane(Ui.icon(Feather.BOOK_OPEN, 18));
        logo.getStyleClass().add("brand-logo");
        VBox names = new VBox(0, Ui.label("Dignitas", "brand-name"), Ui.label("Questões de concursos", "brand-tagline"));
        HBox brand = new HBox(logo, names);
        brand.getStyleClass().add("brand");

        VBox nav = new VBox(2);
        ToggleGroup group = new ToggleGroup();
        nav.getChildren().add(Ui.label("ESTUDAR", "nav-section"));
        for (Nav n : Nav.values()) {
            if (n == Nav.BANK) nav.getChildren().add(Ui.label("GERENCIAR", "nav-section"));
            if (n == Nav.SETTINGS) continue;
            nav.getChildren().add(navButton(n, group));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        statusDot.getStyleClass().add("status-dot");
        statusText.getStyleClass().add("hint");
        statusText.setWrapText(true);
        HBox status = new HBox(statusDot, statusText);
        status.getStyleClass().add("connection-status");
        status.setOnMouseClicked(e -> go(Nav.SETTINGS));

        VBox side = new VBox(brand, nav, spacer, navButton(Nav.SETTINGS, group), status);
        side.getStyleClass().add("sidebar");
        side.setSpacing(6);
        return side;
    }

    private ToggleButton navButton(Nav n, ToggleGroup group) {
        ToggleButton b = new ToggleButton(n.label, Ui.icon(n.icon, 18));
        b.getStyleClass().add("nav-item");
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
    }
}
