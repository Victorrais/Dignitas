package br.com.dignitas.ui.pages;

import atlantafx.base.theme.Styles;
import br.com.dignitas.service.Dtos.ImportRow;
import br.com.dignitas.ui.*;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;

public class ImportsPage extends DbPage {
    private final TableView<ImportRow> table = Tables.table();
    private final Label subtitle = new Label();

    public ImportsPage(MainWindow app) {
        super(app);
    }

    @Override
    protected Node build() {
        Button importPdf = Ui.button("Importar PDF", Feather.UPLOAD, Styles.ACCENT);
        importPdf.setOnAction(e -> app.go(MainWindow.Nav.IMPORT));

        table.getColumns().setAll(
                Tables.column("Arquivo", 300, r -> Tables.twoLines(r.fileName(), r.origin())),
                Tables.column("Questões", 100, r -> Ui.badge(Long.toString(r.questions()), "accent")),
                Tables.column("Páginas", 140, r -> Ui.label(r.pageCount()
                        + (r.ocrPages() > 0 ? "  (" + r.ocrPages() + " por OCR)" : ""), "cell-subtitle")),
                Tables.column("Importado em", 150, r -> Ui.label(Ui.format(r.importedAt()), "cell-subtitle")),
                Tables.column("", 150, r -> {
                    Button del = Ui.button("Excluir", Feather.TRASH_2, Styles.SMALL, Styles.BUTTON_OUTLINED, Styles.DANGER);
                    del.setOnAction(e -> delete(r));
                    return del;
                }));
        table.setPlaceholder(Ui.emptyState(Feather.ARCHIVE, "Nenhuma importação",
                "Os PDFs importados aparecem aqui. Excluir uma importação remove todas as questões dela."));

        VBox content = new VBox(16, Ui.header("Importações", subtitle, importPdf), table);
        content.getStyleClass().add("page");
        return content;
    }

    private void delete(ImportRow r) {
        if (!Ui.confirm("Excluir importação?", "Serão removidas " + r.questions() + " questões de \"" + r.fileName()
                + "\", junto com as respostas registradas para elas. Esta ação não pode ser desfeita.", "Excluir tudo")) {
            return;
        }
        Async.run(() -> {
            Services.IMPORTS.delete(r.id());
            return true;
        }, x -> load());
    }

    @Override
    protected void load() {
        Async.run(Services.IMPORTS::list, (List<ImportRow> rows) -> {
            table.getItems().setAll(rows);
            subtitle.setText(rows.size() + (rows.size() == 1 ? " arquivo importado" : " arquivos importados"));
        });
    }
}
