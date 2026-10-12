package br.com.dignitas;

import br.com.dignitas.app.AppConfig;
import br.com.dignitas.domain.Alternative;
import br.com.dignitas.domain.Question;
import br.com.dignitas.extraction.ImportPipeline;
import br.com.dignitas.extraction.OcrMode;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.persistence.SchemaInstaller;
import br.com.dignitas.service.Dtos.*;
import br.com.dignitas.service.ImportService;
import br.com.dignitas.service.QuestionService;
import br.com.dignitas.service.StatsService;

import java.io.File;
import java.util.Map;

/**
 * Verificação de integração contra um MySQL real: aplica o schema, importa uma prova com gabarito
 * pelo mesmo serviço da tela de importação e confere o que foi gravado.
 * Uso: {@code MySqlImportCheck <prova.pdf> <gabarito.pdf> <banca> <ano> <concurso>}, com
 * DIGNITAS_DB_USER e DIGNITAS_DB_PASSWORD no ambiente (host/porta/banco: localhost:3306/dignitasdb).
 */
public final class MySqlImportCheck {
    private MySqlImportCheck() {
    }

    public static void main(String[] args) throws Exception {
        String user = System.getenv("DIGNITAS_DB_USER");
        String password = System.getenv("DIGNITAS_DB_PASSWORD");
        String url = AppConfig.jdbcUrl("localhost", 3306, "dignitasdb");

        SchemaInstaller.install(url, user, password);
        Jpa.init(Map.of("jakarta.persistence.jdbc.url", url,
                "jakarta.persistence.jdbc.user", user,
                "jakarta.persistence.jdbc.password", password));
        try {
            ImportService imports = new ImportService();
            QuestionService questions = new QuestionService();
            File pdf = new File(args[0]);
            ImportPipeline.Result r = new ImportPipeline().run(pdf, new File(args[1]), OcrMode.TEXT_ONLY, null,
                    (d, t, m) -> {
                    }, () -> false);
            imports.findByFileHash(r.fileHash()).ifPresent(old -> {
                System.out.println("Importação anterior encontrada (" + old.questions() + " questões): removendo.");
                imports.delete(old.id());
            });

            SaveSummary s = imports.save(new ExamInfo(args[4], args[2], Integer.parseInt(args[3]), null),
                    pdf.getName(), r.fileHash(), r.pageCount(), r.ocrPages(), r.questions());
            System.out.println("Extraídas: " + r.questions().size() + " | salvas: " + s.saved()
                    + " | duplicadas ignoradas: " + s.skippedDuplicates());

            ImportRow row = imports.findByFileHash(r.fileHash()).orElseThrow();
            System.out.println("Importação no banco: " + row.fileName() + " · " + row.origin() + " · " + row.questions() + " questões");

            PageResult<QuestionRow> page = questions.search(new QuestionFilter(null, null, args[2], null, null,
                    Status.ALL, Sort.NUMBER, false), 0, 500);
            int withAnswer = 0, withBase = 0, baseImages = 0, altImages = 0, annulled = 0;
            for (QuestionRow qr : page.items()) {
                Question q = questions.loadFull(qr.id());
                if (q.isAnnulled() || q.correctAlternative().isPresent()) withAnswer++;
                if (q.isAnnulled()) annulled++;
                if (q.getBaseText() != null) {
                    withBase++;
                    if (q.getBaseText().getImage() != null) baseImages++;
                }
                for (Alternative a : q.getAlternatives()) if (a.getImage() != null) altImages++;
            }
            System.out.println("Lidas do banco: " + page.total() + " | com resposta: " + withAnswer + " (anuladas: " + annulled
                    + ") | com texto-base: " + withBase + " | textos-base com imagem: " + baseImages
                    + " | alternativas com imagem: " + altImages);
            System.out.println("Disciplinas: " + questions.subjects());
            Overview o = new StatsService().overview();
            System.out.println("Banco total: " + o.questions() + " questões, " + o.imports() + " importações");
        } finally {
            Jpa.shutdown();
        }
    }
}
