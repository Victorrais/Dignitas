package br.com.dignitas;

import br.com.dignitas.app.DignitasApp;
import br.com.dignitas.domain.Question;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.persistence.SchemaInstaller;
import br.com.dignitas.service.AttemptService;
import br.com.dignitas.service.Dtos.QuestionFilter;
import br.com.dignitas.service.QuestionService;
import br.com.dignitas.service.SampleData;
import javafx.application.Application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

/**
 * Abre o Dignitas com um banco H2 em memória (mesmo schema.sql) e dados de exemplo,
 * para desenvolver a interface sem um servidor MySQL. Uso:
 * {@code mvnw exec:java -Dexec.mainClass=br.com.dignitas.DevLauncher -Dexec.classpathScope=test}
 */
public final class DevLauncher {
    private DevLauncher() {
    }

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:mem:dignitas;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Connection keepAlive = DriverManager.getConnection(url, "sa", "");
        SchemaInstaller.install(keepAlive);
        Jpa.init(Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", url,
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", ""));

        QuestionService questions = new QuestionService();
        SampleData.insert(questions);
        AttemptService attempts = new AttemptService();
        List<Long> ids = questions.ids(QuestionFilter.all());
        for (int i = 0; i < 3; i++) {
            Question q = questions.loadFull(ids.get(i));
            attempts.answer(q.getId(), q.getAlternatives().get(i == 1 ? 0 : 2).getId());
        }

        Application.launch(DignitasApp.class, args);
        keepAlive.close();
    }
}
