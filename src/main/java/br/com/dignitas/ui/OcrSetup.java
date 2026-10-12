package br.com.dignitas.ui;

import br.com.dignitas.extraction.TessdataManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

/** Diálogo de download do pacote de idioma do OCR. */
public final class OcrSetup {
    private OcrSetup() {
    }

    /** Baixa o modelo mostrando o progresso. {@code onDone} recebe true em caso de sucesso. */
    public static void download(java.util.function.Consumer<Boolean> onDone) {
        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setPrefWidth(380);
        Label text = Ui.wrapped("Baixando o pacote de idioma português do Tesseract (tessdata_fast). "
                + "Isso só é necessário uma vez.", "page-subtitle");
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(Ui.owner());
        Ui.applyScale(dialog.getDialogPane());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Preparando o OCR");
        dialog.setHeaderText("Preparando o OCR");
        dialog.getDialogPane().setContent(new VBox(12, text, bar));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Button close = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        close.setDisable(true);
        dialog.show();

        Async.run(() -> {
            TessdataManager.download(p -> Platform.runLater(() -> bar.setProgress(p < 0 ? -1 : p)));
            return true;
        }, ok -> {
            dialog.close();
            onDone.accept(true);
        }, err -> {
            dialog.close();
            Ui.showError(new RuntimeException("Não foi possível baixar o pacote de OCR. Verifique a conexão "
                    + "com a internet ou baixe manualmente " + TessdataManager.DOWNLOAD_URL
                    + " e aponte a pasta em Configurações.", err));
            onDone.accept(false);
        });
    }
}
