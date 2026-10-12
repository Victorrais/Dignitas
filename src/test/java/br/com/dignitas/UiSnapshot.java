package br.com.dignitas;

import br.com.dignitas.app.AppConfig;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.ui.MainWindow;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ferramenta de desenvolvimento: renderiza telas do app fora da tela (sem abrir janela) e salva PNGs.
 * Usa o MySQL local (DIGNITAS_DB_USER / DIGNITAS_DB_PASSWORD).
 * Uso: {@code UiSnapshot <pasta> <idQuestao>...}; cada id gera a tela "Resolver questões" daquela questão.
 */
public final class UiSnapshot {
    private UiSnapshot() {
    }

    public static void main(String[] args) throws Exception {
        String h2Pdf = System.getenv("DIGNITAS_SNAPSHOT_H2_PDF");
        if (h2Pdf != null) {
            // Banco em memória com uma prova importada pelo pipeline real (não toca no MySQL).
            String url = "jdbc:h2:mem:snap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
            java.sql.Connection keep = java.sql.DriverManager.getConnection(url, "sa", "");
            br.com.dignitas.persistence.SchemaInstaller.install(keep);
            Jpa.init(Map.of("jakarta.persistence.jdbc.driver", "org.h2.Driver", "jakarta.persistence.jdbc.url", url,
                    "jakarta.persistence.jdbc.user", "sa", "jakarta.persistence.jdbc.password", ""));
            String[] files = h2Pdf.split(";");
            var r = new br.com.dignitas.extraction.ImportPipeline().run(new File(files[0]),
                    files.length > 1 ? new File(files[1]) : null, br.com.dignitas.extraction.OcrMode.TEXT_ONLY, null,
                    (d, t, m) -> {
                    }, () -> false);
            new br.com.dignitas.service.ImportService().save(new br.com.dignitas.model.ExamInfo("Simulado", "Teste", 2025, null),
                    "simulado.pdf", r.fileHash(), r.pageCount(), 0, r.questions());
        } else {
            String url = AppConfig.jdbcUrl("localhost", 3306, "dignitasdb");
            Jpa.init(Map.of("jakarta.persistence.jdbc.url", url,
                    "jakarta.persistence.jdbc.user", System.getenv("DIGNITAS_DB_USER"),
                    "jakarta.persistence.jdbc.password", System.getenv("DIGNITAS_DB_PASSWORD")));
        }
        File out = new File(args[0]);
        out.mkdirs();

        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);

        AtomicReference<MainWindow> window = new AtomicReference<>();
        AtomicReference<Scene> scene = new AtomicReference<>();
        runFx(() -> {
            MainWindow.applyTheme(AppConfig.get().darkTheme());
            MainWindow w = new MainWindow();
            Scene s = new Scene(w.root(), 1600, 1000);
            if ("1".equals(System.getenv("SNAP_COLLAPSE"))) w.toggleSidebar();
            s.getStylesheets().add(Objects.requireNonNull(UiSnapshot.class.getResource("/css/dignitas.css")).toExternalForm());
            new Stage().setScene(s); // não é exibida
            w.markConnected("MySQL (snapshot)");
            window.set(w);
            scene.set(s);
        });

        snapshot(scene.get(), () -> window.get().go(MainWindow.Nav.BANK), new File(out, "banco.png"));
        for (int i = 1; i < args.length; i++) {
            long id = Long.parseLong(args[i]);
            snapshot(scene.get(), () -> window.get().openQuestion(id), new File(out, "questao-" + id + ".png"));
        }
        Jpa.shutdown();
        Platform.exit();
        System.out.println("ok");
    }

    private static void snapshot(Scene scene, Runnable action, File file) throws Exception {
        runFx(action);
        Thread.sleep(6000); // carregamento assíncrono (Async) e layout
        AtomicReference<WritableImage> img = new AtomicReference<>();
        runFx(() -> {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            img.set(scene.getRoot().snapshot(null, null));
        });
        ImageIO.write(SwingFXUtils.fromFXImage(img.get(), null), "png", file);
    }

    private static void runFx(Runnable r) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } finally {
                done.countDown();
            }
        });
        done.await();
    }
}
