package br.com.dignitas.ui.pages;

import br.com.dignitas.service.Dtos.CategoryStat;
import br.com.dignitas.service.Dtos.DailyStat;
import br.com.dignitas.service.Dtos.Overview;
import br.com.dignitas.ui.*;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class StatsPage extends DbPage {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private final VBox body = new VBox(20);

    public StatsPage(MainWindow app) {
        super(app);
    }

    private record Data(Overview overview, List<CategoryStat> subjects, List<CategoryStat> boards, List<DailyStat> daily) {
    }

    @Override
    protected Node build() {
        return Ui.scrollPage(new VBox(20, Ui.header("Estatísticas", new Label("Seu desempenho nas questões resolvidas")), body));
    }

    @Override
    protected void load() {
        Async.run(() -> new Data(Services.STATS.overview(), Services.STATS.bySubject(), Services.STATS.byBoard(),
                Services.STATS.daily(30)), this::render);
    }

    private void render(Data d) {
        Overview o = d.overview();
        if (o.attempts() == 0) {
            body.getChildren().setAll(Ui.card(Ui.emptyState(Feather.BAR_CHART_2, "Ainda não há dados",
                    "Resolva algumas questões para acompanhar sua evolução por disciplina, banca e dia.")));
            return;
        }
        HBox cards = new HBox(16,
                number("Respostas", Long.toString(o.attempts())),
                number("Acertos", Long.toString(o.correctAttempts())),
                number("Erros", Long.toString(o.attempts() - o.correctAttempts())),
                number("Taxa de acerto", Ui.percent(o.accuracy())));
        cards.getChildren().forEach(c -> HBox.setHgrow(c, Priority.ALWAYS));

        HBox charts = new HBox(20, chartCard("Acerto por disciplina", bars(d.subjects())),
                chartCard("Acerto por banca", bars(d.boards())));
        charts.getChildren().forEach(c -> {
            HBox.setHgrow(c, Priority.ALWAYS);
            ((VBox) c).setPrefWidth(1);
        });

        body.getChildren().setAll(cards, charts, chartCard("Respostas nos últimos 30 dias", daily(d.daily())));
    }

    private static VBox number(String label, String value) {
        VBox v = Ui.card(Ui.label(value, "stat-value"), Ui.label(label, "stat-label"));
        v.getStyleClass().add("stat-card");
        v.setPrefWidth(1);
        return v;
    }

    private static VBox chartCard(String title, Chart chart) {
        chart.setAnimated(false);
        chart.setLegendVisible(chart instanceof AreaChart);
        chart.setPrefHeight(300);
        return Ui.card(Ui.label(title, "card-title"), chart);
    }

    private static BarChart<String, Number> bars(List<CategoryStat> stats) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis(0, 100, 20);
        y.setLabel("% de acerto");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        stats.stream().limit(8).forEach(c -> s.getData().add(new XYChart.Data<>(
                c.name() + " (" + c.attempts() + ")", Math.round(c.accuracy() * 100))));
        chart.getData().add(s);
        // Poucas categorias: barras mais estreitas.
        chart.setCategoryGap(s.getData().size() <= 2 ? 160 : s.getData().size() <= 4 ? 60 : 24);
        return chart;
    }

    private static AreaChart<String, Number> daily(List<DailyStat> days) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Questões");
        AreaChart<String, Number> chart = new AreaChart<>(x, y);
        XYChart.Series<String, Number> total = new XYChart.Series<>();
        total.setName("Respondidas");
        XYChart.Series<String, Number> correct = new XYChart.Series<>();
        correct.setName("Acertos");
        for (DailyStat d : days) {
            total.getData().add(new XYChart.Data<>(DAY.format(d.day()), d.attempts()));
            correct.getData().add(new XYChart.Data<>(DAY.format(d.day()), d.correct()));
        }
        chart.getData().addAll(List.of(total, correct));
        chart.setCreateSymbols(false);
        return chart;
    }
}
