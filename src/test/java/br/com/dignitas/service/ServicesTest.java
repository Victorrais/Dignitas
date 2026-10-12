package br.com.dignitas.service;

import br.com.dignitas.domain.Alternative;
import br.com.dignitas.domain.Question;
import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.persistence.SchemaInstaller;
import br.com.dignitas.service.Dtos.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Executa o schema.sql real no H2 (modo MySQL) e exercita os serviços via Hibernate. */
class ServicesTest {
    private final QuestionService questions = new QuestionService();
    private final AttemptService attempts = new AttemptService();
    private final ImportService imports = new ImportService();
    private final StatsService stats = new StatsService();
    private Connection keepAlive;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        keepAlive = DriverManager.getConnection(url, "sa", "");
        SchemaInstaller.install(keepAlive);
        Jpa.init(Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", url,
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        Jpa.shutdown();
        keepAlive.close();
    }

    private static QuestionDraft draft(String statement, String subject, int correct) {
        QuestionDraft d = new QuestionDraft();
        d.setStatement(statement);
        d.setSubjectName(subject);
        for (int i = 0; i < 5; i++) d.addAlternative(String.valueOf((char) ('A' + i)), statement + " alt " + i, i == correct);
        return d;
    }

    @Test
    void sampleDataIsInsertedOnceAndLoadsFully() {
        assertEquals(5, SampleData.insert(questions));
        assertEquals(0, SampleData.insert(questions), "segunda inserção deve detectar duplicidade");
        assertEquals(5, questions.count());

        long id = questions.search(QuestionFilter.all(), 0, 10).items().getFirst().id();
        Question q = questions.loadFull(id);
        assertEquals(5, q.getAlternatives().size());
        assertEquals("C", q.correctAlternative().map(Alternative::getLetter).orElseThrow());
        assertNotNull(q.getBaseText());
        assertEquals("Língua Portuguesa", q.getSubject().getName());
        assertEquals("Dignitas", q.getExam().getBoard());
    }

    @Test
    void answeringTwiceKeepsBothAttemptsAndFiltersWork() {
        long id = questions.save(draft("Quanto é 2 + 2?", "Matemática", 1));
        Question q = questions.loadFull(id);
        long wrong = q.getAlternatives().get(0).getId();
        long right = q.getAlternatives().get(1).getId();

        assertFalse(attempts.answer(id, wrong).correct());
        assertFalse(attempts.answer(id, wrong).correct(), "a mesma alternativa pode ser escolhida de novo");
        AnswerResult r = attempts.answer(id, right);
        assertTrue(r.correct());
        assertEquals("B", r.correctLetter());

        assertEquals(3, attempts.history(null, ResultFilter.ALL, 0, 10).total());
        assertEquals(2, attempts.history(null, ResultFilter.WRONG, 0, 10).total());
        assertEquals(1, attempts.history("2 + 2", ResultFilter.CORRECT, 0, 10).total());

        QuestionFilter wrongOnes = new QuestionFilter(null, null, null, null, null, Status.WRONG, Sort.RECENT, false);
        assertEquals(List.of(id), questions.ids(wrongOnes));
        questions.save(draft("Outra questão", "Matemática", 0));
        QuestionFilter unanswered = new QuestionFilter(null, null, null, null, null, Status.UNANSWERED, Sort.RECENT, false);
        assertEquals(1, questions.ids(unanswered).size());

        Overview o = stats.overview();
        assertEquals(2, o.questions());
        assertEquals(1, o.answeredQuestions());
        assertEquals(3, o.attempts());
        assertEquals(1, stats.bySubject().size());
        assertEquals(1, stats.daily(7).getLast().correct());
    }

    @Test
    void editingKeepsHistoryAndRejectsDuplicates() {
        long a = questions.save(draft("Enunciado A", null, 0));
        long b = questions.save(draft("Enunciado B", null, 0));
        Question qa = questions.loadFull(a);
        attempts.answer(a, qa.getAlternatives().get(0).getId());

        QuestionDraft edit = questions.toDraft(qa);
        edit.getAlternatives().removeLast();
        edit.setStatement("Enunciado A (revisado)");
        questions.save(edit);
        assertEquals(4, questions.loadFull(a).getAlternatives().size());
        assertEquals(1, attempts.history(null, ResultFilter.ALL, 0, 10).total());

        QuestionDraft clone = questions.toDraft(questions.loadFull(b));
        clone.setId(null);
        assertThrows(DuplicateQuestionException.class, () -> questions.save(clone));

        questions.delete(a);
        assertEquals(0, attempts.history(null, ResultFilter.ALL, 0, 10).total());
        assertEquals(1, questions.count());
    }

    @Test
    void importSavesSharedBaseTextAndDeleteCascades() {
        BaseTextDraft shared = new BaseTextDraft(null, "Texto I compartilhado", "(Adaptado)");
        QuestionDraft q1 = draft("Primeira", "Português", 0);
        QuestionDraft q2 = draft("Segunda", "Português", 2);
        QuestionDraft dup = draft("Primeira", "Português", 0);
        QuestionDraft excluded = draft("Excluída", "Português", 0);
        excluded.setIncluded(false);
        q1.setBaseText(shared);
        q2.setBaseText(shared);

        ExamInfo exam = new ExamInfo("TJ-SP Escrevente", "VUNESP", 2024, "Escrevente");
        SaveSummary s = imports.save(exam, "prova.pdf", "abc", 10, 2, List.of(q1, q2, dup, excluded));
        assertEquals(2, s.saved());
        assertEquals(1, s.skippedDuplicates());

        List<QuestionRow> rows = questions.search(QuestionFilter.all(), 0, 10).items();
        Question l1 = questions.loadFull(rows.get(0).id());
        Question l2 = questions.loadFull(rows.get(1).id());
        assertEquals(l1.getBaseText().getId(), l2.getBaseText().getId());
        assertEquals(List.of("VUNESP"), questions.boards());
        assertEquals(List.of(2024), questions.years());

        ImportRow row = imports.findByFileHash("abc").orElseThrow();
        assertEquals(2, row.questions());
        attempts.answer(l1.getId(), l1.getAlternatives().getFirst().getId());

        imports.delete(row.id());
        assertEquals(0, questions.count());
        assertTrue(imports.list().isEmpty());
        assertTrue(questions.boards().isEmpty(), "concurso órfão deve ser removido");
        assertEquals(0, stats.overview().attempts());
    }
}
