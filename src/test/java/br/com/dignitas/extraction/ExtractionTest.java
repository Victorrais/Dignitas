package br.com.dignitas.extraction;

import br.com.dignitas.model.QuestionDraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionTest {
    @TempDir
    File dir;

    private ImportPipeline.Result run(File pdf, File key) throws Exception {
        return new ImportPipeline().run(pdf, key, OcrMode.TEXT_ONLY, null, (d, t, m) -> {
        }, () -> false);
    }

    private static List<String> letters(QuestionDraft q) {
        return q.getAlternatives().stream().map(QuestionDraft.AlternativeDraft::getLetter).toList();
    }

    @Test
    void singleColumnWithSharedBaseTextHeaderAndAnswerKey() throws Exception {
        TestPdf pdf = new TestPdf();
        for (int p = 0; p < 3; p++) {
            pdf.text(p, 60, 30, "CONCURSO PÚBLICO – PREFEITURA DE EXEMPLO");
            pdf.text(p, 290, 820, String.valueOf(p + 1));
        }
        float y = 80;
        pdf.bold(0, 60, y, "LÍNGUA PORTUGUESA");
        y = pdf.block(0, 60, y + 24,
                "Leia o texto a seguir para responder às questões 1 e 2.",
                "A tecnologia mudou a forma como as pessoas se comunicam no dia a dia, com novas",
                "ferramentas digitais e redes sociais cada vez mais presentes.");
        pdf.text(0, 60, y, "Disponível em: www.exemplo.com.br. Acesso em: 10 jan. 2024.");
        y = pdf.block(0, 60, y + 28,
                "1. De acordo com o texto, a tecnologia:",
                "(A) eliminou a comunicação presencial.",
                "(B) mudou a forma de comunicação das pessoas, tornando-a",
                "mais dinâmica.",
                "(C) não tem relação com redes sociais.",
                "(D) reduziu o uso de ferramentas digitais.",
                "(E) foi abandonada pela sociedade.");
        pdf.block(0, 60, y + 14,
                "2. O termo \"presentes\" refere-se a:",
                "(A) pessoas",
                "(B) ferramentas e redes",
                "(C) formas",
                "(D) dias",
                "(E) textos");
        pdf.bold(1, 60, 80, "RACIOCÍNIO LÓGICO");
        pdf.block(1, 60, 110,
                "3. Quanto é 2 + 3?",
                "a) 4",
                "b) 5",
                "c) 6",
                "d) 7",
                "e) 8");
        pdf.block(2, 60, 80, "4. Questão sem texto-base e sem gabarito correspondente.",
                "(A) um (B) dois (C) três (D) quatro (E) cinco");

        TestPdf key = new TestPdf();
        key.block(0, 60, 80, "GABARITO OFICIAL", "1 2 3", "B B b", "04 - ANULADA");

        ImportPipeline.Result r = run(pdf.save(dir, "prova.pdf"), key.save(dir, "gabarito.pdf"));
        List<QuestionDraft> qs = r.questions();
        assertEquals(List.of(1, 2, 3, 4), qs.stream().map(QuestionDraft::getNumber).toList());
        qs.forEach(q -> assertEquals(List.of("A", "B", "C", "D", "E"), letters(q), "questão " + q.getNumber()));

        QuestionDraft q1 = qs.get(0);
        assertEquals("De acordo com o texto, a tecnologia:", q1.getStatement());
        assertEquals("mudou a forma de comunicação das pessoas, tornando-a mais dinâmica.",
                q1.getAlternatives().get(1).getText());
        assertTrue(q1.getAlternatives().get(1).isCorrect());
        assertNotNull(q1.getBaseText());
        assertTrue(q1.getBaseText().getContent().startsWith("A tecnologia mudou"));
        assertTrue(q1.getBaseText().getSource().startsWith("Disponível em"));
        assertSame(q1.getBaseText(), qs.get(1).getBaseText(), "texto-base compartilhado");
        assertEquals("Língua Portuguesa", q1.getSubjectName());

        QuestionDraft q3 = qs.get(2);
        assertNull(q3.getBaseText(), "fora do intervalo 1–2");
        assertEquals("Raciocínio Lógico", q3.getSubjectName());
        assertEquals("5", q3.getAlternatives().get(1).getText());
        assertTrue(q3.getAlternatives().get(1).isCorrect());

        QuestionDraft q4 = qs.get(3);
        assertEquals("cinco", q4.getAlternatives().get(4).getText());
        assertTrue(q4.isAnnulled());
        assertEquals(4, r.answersApplied());
        qs.forEach(q -> assertTrue(q.validate().isEmpty(), q.getNumber() + ": " + q.validate()));
        qs.forEach(q -> assertFalse(q.getStatement().contains("CONCURSO"), "cabeçalho removido"));
    }

    @Test
    void twoColumnsAreReadLeftThenRight() throws Exception {
        TestPdf pdf = new TestPdf();
        pdf.bold(0, 60, 60, "CONHECIMENTOS GERAIS");
        pdf.block(0, 40, 100,
                "QUESTÃO 11",
                "Qual é a capital do Brasil?",
                "A) São Paulo",
                "B) Brasília",
                "C) Rio de Janeiro",
                "D) Salvador",
                "E) Recife",
                "",
                "QUESTÃO 12",
                "Qual é o maior planeta?");
        pdf.block(0, 320, 100,
                "A) Terra",
                "B) Marte",
                "C) Júpiter",
                "D) Vênus",
                "E) Mercúrio",
                "",
                "QUESTÃO 13",
                "Quantos lados tem um hexágono?",
                "A) 4",
                "B) 5",
                "C) 6",
                "D) 7",
                "E) 8");

        List<QuestionDraft> qs = run(pdf.save(dir, "colunas.pdf"), null).questions();
        assertEquals(List.of(11, 12, 13), qs.stream().map(QuestionDraft::getNumber).toList());
        assertEquals("Qual é o maior planeta?", qs.get(1).getStatement());
        assertEquals("Júpiter", qs.get(1).getAlternatives().get(2).getText());
        assertEquals(5, qs.get(2).getAlternatives().size());
        assertEquals("Conhecimentos Gerais", qs.get(0).getSubjectName());
    }

    @Test
    void trueFalseItemsBecomeCertoErrado() throws Exception {
        TestPdf pdf = new TestPdf();
        float y = pdf.block(0, 60, 80,
                "Texto CB1A1",
                "A água é um recurso natural essencial à vida e deve ser preservada por todos os",
                "cidadãos, empresas e governos.",
                "Julgue os itens a seguir, a respeito do texto CB1A1.");
        pdf.block(0, 60, y + 14,
                "1 A água é um recurso dispensável para a vida humana segundo o texto.",
                "2 O texto atribui a preservação da água apenas aos governos.",
                "3 Segundo o texto, a preservação da água cabe a todos.");
        TestPdf key = new TestPdf();
        key.block(0, 60, 80, "1 E 2 E 3 C");

        List<QuestionDraft> qs = run(pdf.save(dir, "cebraspe.pdf"), key.save(dir, "gab.pdf")).questions();
        assertEquals(3, qs.size());
        qs.forEach(q -> assertTrue(q.isTrueFalse()));
        assertEquals(List.of("C", "E"), letters(qs.get(0)));
        assertTrue(qs.get(2).correctAlternative().orElseThrow().getLetter().equals("C"));
        assertTrue(qs.get(0).correctAlternative().orElseThrow().getLetter().equals("E"));
    }

    @Test
    void answerKeyFormats() {
        Map<Integer, String> key = new AnswerKeyParser().parseLines(List.of(
                "Tipo 1 – Branca",
                "01 - A  02 – C  03: E  04. ANULADA  05 *",
                "6 7 8 9",
                "B D X C",
                "10 A"));
        assertEquals("A", key.get(1));
        assertEquals("C", key.get(2));
        assertEquals("E", key.get(3));
        assertEquals(AnswerKeyParser.ANNULLED, key.get(4));
        assertEquals(AnswerKeyParser.ANNULLED, key.get(5));
        assertEquals("B", key.get(6));
        assertEquals(AnswerKeyParser.ANNULLED, key.get(8));
        assertEquals("C", key.get(9));
        assertEquals("A", key.get(10));
        assertFalse(key.containsKey(1 + 100));
    }

    @Test
    void blankPageIsFlaggedForOcr() throws Exception {
        TestPdf pdf = new TestPdf();
        pdf.text(0, 60, 80, "x");
        ImportPipeline.Result r = new ImportPipeline().run(pdf.save(dir, "scan.pdf"), null, OcrMode.AUTO, null,
                (d, t, m) -> {
                }, () -> false);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("OCR")),
                r.warnings().stream().collect(Collectors.joining("; ")));
    }
}
