package br.com.dignitas.ui;

import org.junit.jupiter.api.Test;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.*;

class UiMessagesTest {

    private static RuntimeException wrapped(String sqlMessage) {
        // Como o Hibernate entrega: exceção de persistência com a do driver como causa.
        return new RuntimeException("could not execute statement",
                new SQLIntegrityConstraintViolationException(sqlMessage));
    }

    @Test
    void duplicateFileBecomesFriendlyMessage() {
        String msg = Ui.constraintMessage(wrapped(
                "Duplicate entry '4aafd35…' for key 'import_batch.uk_import_file_hash'"));
        assertNotNull(msg);
        assertTrue(msg.startsWith("Este PDF já foi importado"), msg);
    }

    @Test
    void duplicateQuestionAndOtherViolations() {
        assertTrue(Ui.constraintMessage(wrapped("Duplicate entry 'x' for key 'question.uk_question_content_hash'"))
                .startsWith("Já existe uma questão"));
        assertTrue(Ui.constraintMessage(wrapped("Duplicate entry 'x' for key 'outra_chave'"))
                .startsWith("Já existe um registro igual"));
        assertTrue(Ui.constraintMessage(wrapped("Cannot add or update a child row: a foreign key constraint fails"))
                .contains("integridade"));
    }

    @Test
    void otherErrorsAreNotTranslated() {
        assertNull(Ui.constraintMessage(new IllegalStateException("Banco de dados não conectado")));
    }
}
