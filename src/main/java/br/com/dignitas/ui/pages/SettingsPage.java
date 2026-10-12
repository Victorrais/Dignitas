package br.com.dignitas.ui.pages;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import br.com.dignitas.app.AppConfig;
import br.com.dignitas.extraction.TessdataManager;
import br.com.dignitas.persistence.SchemaInstaller;
import br.com.dignitas.service.SampleData;
import br.com.dignitas.ui.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.kordamp.ikonli.feather.Feather;

import java.io.File;
import java.io.IOException;

public class SettingsPage implements Page {
    private final MainWindow app;
    private final AppConfig cfg = AppConfig.get();
    private final TextField host = new TextField();
    private final TextField port = new TextField();
    private final TextField database = new TextField();
    private final TextField user = new TextField();
    private final PasswordField password = new PasswordField();
    private final RadioButton modoLocal = new RadioButton("Banco local: portátil, não precisa instalar nada");
    private final RadioButton modoMysql = new RadioButton("Servidor MySQL");
    private final GridPane mysqlGrid = new GridPane(12, 10);
    private final Button test = Ui.button("Testar conexão", Feather.ACTIVITY, Styles.BUTTON_OUTLINED);
    private final Label dbStatus = new Label();
    private final Label ocrStatus = new Label();
    private final Button sample = Ui.button("Inserir questões de exemplo", Feather.PLUS, Styles.BUTTON_OUTLINED);
    private final Node root;

    public SettingsPage(MainWindow app) {
        this.app = app;
        root = Ui.scrollPage(new VBox(20,
                Ui.header("Configurações", new Label("Banco de dados, OCR e aparência")),
                databaseCard(), ocrCard(), appearanceCard()));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShow() {
        host.setText(cfg.dbHost());
        port.setText(Integer.toString(cfg.dbPort()));
        database.setText(cfg.dbName());
        user.setText(cfg.dbUser());
        password.setText(cfg.dbPassword());
        (cfg.localDatabase() ? modoLocal : modoMysql).setSelected(true);
        atualizarModo();
        updateDbStatus();
        updateOcrStatus();
    }

    // ---------- Banco ----------

    private Node databaseCard() {
        ToggleGroup modo = new ToggleGroup();
        modoLocal.setToggleGroup(modo);
        modoMysql.setToggleGroup(modo);
        modo.selectedToggleProperty().addListener((obs, antes, agora) -> atualizarModo());
        Label localHint = Ui.wrapped("Os dados ficam num arquivo em " + cfg.localDatabaseFile().getParent()
                + ". Bom para testar ou usar em um só computador.", "hint");

        GridPane grid = mysqlGrid;
        grid.addRow(0, Ui.label("Servidor", "form-label"), host, Ui.label("Porta", "form-label"), port);
        grid.addRow(1, Ui.label("Banco", "form-label"), database);
        grid.addRow(2, Ui.label("Usuário", "form-label"), user, Ui.label("Senha", "form-label"), password);
        GridPane.setHgrow(host, Priority.ALWAYS);
        GridPane.setHgrow(user, Priority.ALWAYS);
        port.setPrefWidth(90);
        host.setPrefWidth(260);

        test.setOnAction(e -> {
            dbStatus.setText("Testando…");
            String url = urlFromForm();
            String u = user.getText(), p = password.getText();
            Async.run(() -> {
                SchemaInstaller.testConnection(url, u, p);
                return true;
            }, ok -> showDb(true, "Conexão bem-sucedida."), err -> showDb(false, rootMessage(err)));
        });
        Button save = Ui.button("Salvar e conectar", Feather.CHECK, Styles.ACCENT);
        save.setOnAction(e -> {
            if (modoMysql.isSelected()) {
                int portNumber;
                try {
                    portNumber = Integer.parseInt(port.getText().strip());
                } catch (NumberFormatException ex) {
                    showDb(false, "Porta inválida.");
                    return;
                }
                cfg.setDatabase(host.getText().strip(), portNumber, database.getText().strip(), user.getText().strip(),
                        password.getText());
            }
            cfg.setLocalDatabase(modoLocal.isSelected());
            if (!persist()) return;
            dbStatus.setText("Conectando…");
            app.connect(this::updateDbStatus);
        });
        sample.setOnAction(e -> Async.run(() -> SampleData.insert(Services.QUESTIONS),
                n -> Ui.info("Questões de exemplo", n == 0 ? "As questões de exemplo já estavam no banco."
                        : n + " questões de exemplo foram inseridas.")));
        HBox actions = new HBox(10, test, save, Ui.hspacer(), sample);
        actions.setAlignment(Pos.CENTER_LEFT);
        dbStatus.setWrapText(true);

        return Ui.card(Ui.label("Banco de dados", "card-title"),
                Ui.wrapped("As tabelas são criadas automaticamente ao conectar (script resources/sql/schema.sql). "
                        + "O banco é criado se ainda não existir.", "hint"),
                modoLocal, localHint, modoMysql, grid, actions, dbStatus);
    }

    /** Os campos do MySQL só valem no modo servidor. */
    private void atualizarModo() {
        boolean mysql = modoMysql.isSelected();
        mysqlGrid.setDisable(!mysql);
        test.setDisable(!mysql);
    }

    private String urlFromForm() {
        int p;
        try {
            p = Integer.parseInt(port.getText().strip());
        } catch (NumberFormatException e) {
            p = 3306;
        }
        return AppConfig.jdbcUrl(host.getText().strip(), p, database.getText().strip());
    }

    private void updateDbStatus() {
        String conectado = cfg.localDatabase()
                ? "Usando o banco local (" + cfg.localDatabaseFile().getParent() + ")."
                : "Conectado a " + cfg.dbHost() + "/" + cfg.dbName() + ".";
        String desconectado = cfg.localDatabase()
                ? "Não foi possível abrir o banco local. Feche outras janelas do Dignitas e tente de novo."
                : "Desconectado. Confira se o MySQL está em execução e se usuário e senha estão corretos.";
        showDb(app.isConnected(), app.isConnected() ? conectado : desconectado);
    }

    private void showDb(boolean ok, String text) {
        dbStatus.setText(text);
        dbStatus.setGraphic(Ui.icon(ok ? Feather.CHECK_CIRCLE : Feather.ALERT_CIRCLE, 16));
        dbStatus.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
        dbStatus.getStyleClass().add(ok ? Styles.SUCCESS : Styles.DANGER);
        sample.setDisable(!app.isConnected());
    }

    // ---------- OCR ----------

    private Node ocrCard() {
        Button download = Ui.button("Baixar pacote de português", Feather.DOWNLOAD, Styles.ACCENT);
        download.setOnAction(e -> OcrSetup.download(ok -> updateOcrStatus()));
        Button choose = Ui.button("Usar outra pasta…", Feather.FOLDER, Styles.BUTTON_OUTLINED);
        choose.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Pasta que contém por.traineddata");
            File dir = dc.showDialog(Ui.owner());
            if (dir == null) return;
            cfg.setTessdataDir(dir.toPath());
            persist();
            updateOcrStatus();
        });
        Button reset = Ui.button("Restaurar padrão", Feather.ROTATE_CCW, Styles.FLAT);
        reset.setOnAction(e -> {
            cfg.setTessdataDir(null);
            persist();
            updateOcrStatus();
        });
        ocrStatus.setWrapText(true);
        return Ui.card(Ui.label("OCR (Tesseract)", "card-title"),
                Ui.wrapped("O OCR lê provas digitalizadas (PDF de imagem). PDFs com texto são lidos diretamente, "
                        + "sem OCR, o que é mais rápido e preciso.", "hint"),
                ocrStatus, new HBox(10, download, choose, reset));
    }

    private void updateOcrStatus() {
        boolean ok = TessdataManager.isInstalled();
        ocrStatus.setText(ok ? "Pacote de português instalado em " + TessdataManager.dir()
                : "Pacote de português não encontrado em " + TessdataManager.dir() + ".");
        ocrStatus.setGraphic(Ui.icon(ok ? Feather.CHECK_CIRCLE : Feather.ALERT_TRIANGLE, 16));
        ocrStatus.getStyleClass().removeAll(Styles.SUCCESS, Styles.WARNING);
        ocrStatus.getStyleClass().add(ok ? Styles.SUCCESS : Styles.WARNING);
    }

    // ---------- Aparência ----------

    private Node appearanceCard() {
        ToggleSwitch dark = new ToggleSwitch("Tema escuro");
        dark.setSelected(cfg.darkTheme());
        dark.selectedProperty().addListener((o, a, b) -> {
            MainWindow.applyTheme(b);
            cfg.setDarkTheme(b);
            persist();
        });
        ComboBox<Integer> size = new ComboBox<>();
        size.getItems().setAll(70, 75, 80, 85, 90, 100, 110, 125, 150);
        size.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer v) {
                return v == null ? "" : v + "%";
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        });
        size.setValue((int) Math.round(Ui.scale() * 100));
        // Atalhos de teclado também mudam a escala: mantém a lista sincronizada.
        Ui.scaleProperty().addListener((o, a, b) -> size.setValue((int) Math.round(b.doubleValue() * 100)));
        size.setOnAction(e -> {
            if (size.getValue() != null) MainWindow.setScale(size.getValue() / 100.0);
        });
        HBox sizeRow = new HBox(10, Ui.label("Tamanho da interface", "form-label"), size);
        sizeRow.setAlignment(Pos.CENTER_LEFT);
        return Ui.card(Ui.label("Aparência", "card-title"), dark, sizeRow,
                Ui.wrapped("Atalhos: Ctrl + e Ctrl − ajustam o tamanho, Ctrl 0 volta ao padrão (85%), "
                        + "Ctrl+B recolhe a barra lateral.", "hint"),
                Ui.wrapped("Configurações salvas em " + cfg.dataDir(), "hint"));
    }

    private boolean persist() {
        try {
            cfg.save();
            return true;
        } catch (IOException e) {
            Ui.showError(e);
            return false;
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable r = e;
        while (r.getCause() != null) r = r.getCause();
        return r.getMessage() == null ? r.toString() : r.getMessage();
    }
}
