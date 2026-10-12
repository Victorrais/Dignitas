package br.com.dignitas.extraction;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Coloca as linhas em ordem de leitura: remove cabeçalhos/rodapés repetidos,
 * detecta páginas em duas colunas e lê a coluna esquerda antes da direita.
 */
public class LayoutAnalyzer {
    private static final double MARGIN_BAND = 0.08;
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "(?i)^(p[áa]g(ina)?\\.?\\s*)?\\d{1,4}(\\s*(de|/)\\s*\\d{1,4})?$");

    public List<PageContent> analyze(List<PageContent> pages) {
        Set<String> repeated = repeatedMarginLines(pages);
        List<PageContent> result = new ArrayList<>();
        for (PageContent p : pages) {
            List<TextLine> kept = new ArrayList<>();
            for (TextLine l : p.lines()) {
                if (inMargin(l, p) && (repeated.contains(key(l.text())) || PAGE_NUMBER.matcher(l.text().strip()).matches())) {
                    continue;
                }
                kept.add(l);
            }
            result.add(p.withLines(readingOrder(kept, p.width())));
        }
        return result;
    }

    private static boolean inMargin(TextLine l, PageContent p) {
        return l.bottom() < p.height() * MARGIN_BAND || l.y() > p.height() * (1 - MARGIN_BAND);
    }

    /** Texto sem dígitos, para que "Página 3" e "Página 4" sejam a mesma chave. */
    private static String key(String s) {
        return s.replaceAll("\\d+", "#").replaceAll("\\s+", " ").strip().toLowerCase();
    }

    private static Set<String> repeatedMarginLines(List<PageContent> pages) {
        if (pages.size() < 3) return Set.of();
        Map<String, Integer> counts = new HashMap<>();
        for (PageContent p : pages) {
            Set<String> onPage = new HashSet<>();
            for (TextLine l : p.lines()) {
                if (inMargin(l, p)) onPage.add(key(l.text()));
            }
            onPage.forEach(k -> counts.merge(k, 1, Integer::sum));
        }
        Set<String> result = new HashSet<>();
        counts.forEach((k, c) -> {
            if (c >= 3 && c >= pages.size() / 2.0) result.add(k);
        });
        return result;
    }

    /** Posição x da calha entre colunas, ou vazio se a página tem uma coluna só. */
    static OptionalDouble findGutter(List<TextLine> lines, double pageWidth) {
        if (lines.size() < 6 || pageWidth <= 0) return OptionalDouble.empty();
        int w = (int) Math.ceil(pageWidth);
        int[] coverage = new int[w + 1];
        for (TextLine l : lines) {
            int a = clamp((int) Math.floor(l.x()), w);
            int b = clamp((int) Math.ceil(l.right()), w);
            for (int i = a; i <= b; i++) coverage[i]++;
        }
        int threshold = Math.max(1, lines.size() / 30);
        int from = (int) (pageWidth * 0.3), to = (int) (pageWidth * 0.7);
        int bestStart = -1, bestLen = 0, runStart = -1;
        for (int i = from; i <= to + 1; i++) {
            boolean empty = i <= to && coverage[i] <= threshold;
            if (empty && runStart < 0) runStart = i;
            if (!empty && runStart >= 0) {
                if (i - runStart > bestLen) {
                    bestLen = i - runStart;
                    bestStart = runStart;
                }
                runStart = -1;
            }
        }
        if (bestLen < 6) return OptionalDouble.empty();
        double gutter = bestStart + bestLen / 2.0;
        long left = lines.stream().filter(l -> l.right() <= gutter).count();
        long right = lines.stream().filter(l -> l.x() >= gutter).count();
        if (left < lines.size() * 0.2 || right < lines.size() * 0.2) return OptionalDouble.empty();
        return OptionalDouble.of(gutter);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    static List<TextLine> readingOrder(List<TextLine> lines, double pageWidth) {
        List<TextLine> byY = new ArrayList<>(lines);
        byY.sort(Comparator.comparingDouble(TextLine::y).thenComparingDouble(TextLine::x));
        OptionalDouble gutter = findGutter(lines, pageWidth);
        if (gutter.isEmpty()) return byY;

        double g = gutter.getAsDouble();
        List<TextLine> out = new ArrayList<>();
        List<TextLine> left = new ArrayList<>();
        List<TextLine> right = new ArrayList<>();
        for (TextLine l : byY) {
            if (l.right() <= g + 2) {
                left.add(l);
            } else if (l.x() >= g - 2) {
                right.add(l);
            } else {
                // Linha que atravessa a calha (título, instrução): fecha o bloco de colunas atual.
                out.addAll(left);
                out.addAll(right);
                left.clear();
                right.clear();
                out.add(l);
            }
        }
        out.addAll(left);
        out.addAll(right);
        return out;
    }
}
