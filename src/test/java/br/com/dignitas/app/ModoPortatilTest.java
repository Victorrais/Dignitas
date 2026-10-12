package br.com.dignitas.app;

import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.persistence.SchemaInstaller;
import br.com.dignitas.service.QuestionService;
import br.com.dignitas.service.SampleData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Modo portátil: banco local H2 por padrão em instalações novas, sem afetar quem já usa MySQL. */
class ModoPortatilTest {

    @TempDir
    Path dir;

    @AfterEach
    void fechar() {
        Jpa.shutdown();
    }

    @Test
    void instalacaoNovaUsaBancoLocal() {
        AppConfig cfg = new AppConfig(dir);
        assertTrue(cfg.localDatabase());
        assertEquals("org.h2.Driver", cfg.jdbcDriver());
        assertTrue(cfg.connectionUrl().startsWith("jdbc:h2:file:"));
    }

    @Test
    void configuracaoAntigaComMysqlContinuaNoMysql() throws IOException {
        Files.writeString(dir.resolve("config.properties"), "db.host=localhost\ndb.user=root\n");
        AppConfig cfg = new AppConfig(dir);
        assertFalse(cfg.localDatabase());
        assertEquals("com.mysql.cj.jdbc.Driver", cfg.jdbcDriver());
        assertTrue(cfg.connectionUrl().startsWith("jdbc:mysql://localhost:3306/dignitasdb"));
    }

    @Test
    void escolhaExplicitaPrevalece() throws IOException {
        AppConfig cfg = new AppConfig(dir);
        cfg.setDatabase("servidor", 3306, "dignitasdb", "root", "x");
        cfg.setLocalDatabase(true);
        cfg.save();
        assertTrue(new AppConfig(dir).localDatabase(), "modo local salvo deve valer mesmo com MySQL preenchido");
    }

    @Test
    void bancoLocalAbreComEsquemaEGuardaDados() throws Exception {
        AppConfig cfg = new AppConfig(dir);
        assertFalse(cfg.localDatabaseExists());
        SchemaInstaller.install(cfg.connectionUrl(), cfg.connectionUser(), cfg.connectionPassword());
        Jpa.init(cfg);
        QuestionService questoes = new QuestionService();
        assertEquals(5, SampleData.insert(questoes));
        assertEquals(5, questoes.count());
        Jpa.shutdown();

        assertTrue(cfg.localDatabaseExists(), "o arquivo do banco local deve existir depois de aberto");
        Jpa.init(cfg); // reabrir: os dados continuam lá
        assertEquals(5, new QuestionService().count());
    }
}
