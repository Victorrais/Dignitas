package br.com.dignitas.ui;

import br.com.dignitas.service.AttemptService;
import br.com.dignitas.service.ImportService;
import br.com.dignitas.service.QuestionService;
import br.com.dignitas.service.StatsService;

/** Instâncias únicas dos serviços usados pelas telas. */
public final class Services {
    public static final QuestionService QUESTIONS = new QuestionService();
    public static final AttemptService ATTEMPTS = new AttemptService();
    public static final StatsService STATS = new StatsService();
    public static final ImportService IMPORTS = new ImportService();

    private Services() {
    }
}
