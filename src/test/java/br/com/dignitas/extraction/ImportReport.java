package br.com.dignitas.extraction;

import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.AlternativeDraft;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Ferramenta de desenvolvimento: roda a importação (sem OCR) e grava um relatório legível de cada
 * questão, com as imagens extraídas. Uso: {@code ImportReport <prova.pdf> <gabarito.pdf|-> <pasta>}.
 */
public final class ImportReport {
    private ImportReport() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[2]);
        File img = new File(out, "img");
        img.mkdirs();
        ImportPipeline.Result r = new ImportPipeline().run(new File(args[0]), "-".equals(args[1]) ? null : new File(args[1]),
                OcrMode.TEXT_ONLY, null, (d, t, m) -> {
                }, () -> false);
        Map<BaseTextDraft, Integer> bases = new IdentityHashMap<>();
        try (PrintWriter w = new PrintWriter(new File(out, "report.txt"), StandardCharsets.UTF_8)) {
            w.printf("%d questões, %d páginas, gabarito aplicado a %d%n", r.questions().size(), r.pageCount(), r.answersApplied());
            r.warnings().forEach(x -> w.println("AVISO: " + x));
            int i = 0;
            for (QuestionDraft q : r.questions()) {
                i++;
                String tag = String.format("%03d-q%s", i, q.getNumber());
                w.printf("%n===== #%d  QUESTÃO %s  [%s]  conf=%.2f%s%s%n", i, q.getNumber(), q.getSubjectName(), q.getConfidence(),
                        q.isAnnulled() ? " ANULADA" : "", q.validate().isEmpty() ? "" : " INVÁLIDA " + q.validate());
                BaseTextDraft b = q.getBaseText();
                if (b != null) {
                    Integer id = bases.get(b);
                    boolean first = id == null;
                    if (first) bases.put(b, id = bases.size() + 1);
                    w.printf("  TEXTO-BASE #%d%s%s%n", id, b.getImage() != null ? " [imagem]" : "", first ? "" : " (compartilhado)");
                    if (first) {
                        w.println("    " + abbreviate(b.getContent(), 300));
                        if (b.getSource() != null) w.println("    FONTE: " + b.getSource());
                        if (b.getImage() != null) Files.write(new File(img, "base" + id + ".png").toPath(), b.getImage());
                    }
                }
                if (q.getImage() != null) {
                    w.println("  [imagem do enunciado]");
                    Files.write(new File(img, tag + ".png").toPath(), q.getImage());
                }
                w.println("  ENUNCIADO: " + abbreviate(q.getStatement(), 400));
                for (AlternativeDraft a : q.getAlternatives()) {
                    w.printf("   %s%s %s%s%n", a.isCorrect() ? "*" : " ", a.getLetter(), abbreviate(a.getText(), 160),
                            a.getImage() != null ? " [imagem]" : "");
                    if (a.getImage() != null) Files.write(new File(img, tag + "-" + a.getLetter() + ".png").toPath(), a.getImage());
                }
                q.getWarnings().forEach(x -> w.println("  ! " + x));
            }
        }
        System.out.println("ok: " + r.questions().size() + " questões");
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        String one = s.replace("\n", " ⏎ ");
        return one.length() <= max ? one : one.substring(0, max) + "…";
    }
}
