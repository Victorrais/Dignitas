package br.com.dignitas.extraction;

import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.Region;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Divide as linhas (em ordem de leitura) em questões, sem depender de um modelo fixo de prova.
 * <p>
 * Funciona como uma máquina de estados guiada por padrões comuns às bancas brasileiras:
 * rótulos de questão ("Questão 12", "12.", "12 –"), alternativas ("(A)", "A)", "a."),
 * textos de apoio ("Texto I", "Leia o texto...", "para responder às questões 3 a 5"),
 * fontes ("Disponível em:", "(Adaptado)") e títulos de disciplina. O resultado sempre passa
 * pela tela de revisão, então a meta é acertar a maior parte e sinalizar o que estiver duvidoso.
 */
public class QuestionSegmenter {

    static final Pattern QUESTION_LABEL = Pattern.compile(
            "^(?:QUEST[ÃA]O|Quest[ãa]o|QUEST[ÕO]ES|Item|ITEM)\\s*(?:N[º°o.]*\\s*)?(\\d{1,3})\\b\\s*[.:\\-–—)]?\\s*(.*)$");
    static final Pattern NUMBERED = Pattern.compile("^(\\d{1,3})\\s*[.)\\-–—]\\s*(\\S.*)$");
    static final Pattern NUMBER_ONLY = Pattern.compile("^(\\d{1,3})[.)]?$");
    static final Pattern NUMBER_SPACE = Pattern.compile("^(\\d{1,3})\\s+([A-ZÀ-Ú\"“(].*)$");
    static final Pattern ALTERNATIVE = Pattern.compile("^\\(?([A-Ea-e])\\s*[).\\-–—:]\\s*(.*)$");
    static final Pattern INLINE_ALTERNATIVE = Pattern.compile("(?:^|\\s)\\(?([A-E])\\)\\s*");
    static final Pattern TEXT_LABEL = Pattern.compile("^(?:TEXTO|Texto)\\s+(?:[IVXL]+|\\d{1,2}|[A-Z]{1,3}\\d[A-Z0-9]*)\\b.*$");
    static final Pattern READ_INSTRUCTION = Pattern.compile(
            "(?i)^(?:leia|considere|observe|analise)\\s+(?:atentamente\\s+)?(?:o|a|os|as)\\s+"
                    + "(?:texto|textos|fragmento|trecho|poema|charge|tirinha|gr[áa]fico|tabela|quadro|excerto|figura)\\b.*");
    static final Pattern FOR_QUESTIONS = Pattern.compile(
            "(?i).*\\b(?:para\\s+responder|para\\s+julgar|responda|julgue)\\b.*\\bquest(?:ões|oes|ão|ao)\\b.*");
    static final Pattern RANGE = Pattern.compile(
            "(?i)quest(?:ões|oes)\\s+(?:de\\s+)?(?:n[º°os.]*\\s*)?(\\d{1,3})\\s*(?:a|à|e|até|-|–)\\s*(\\d{1,3})");
    static final Pattern SOURCE = Pattern.compile(
            "(?i)^(?:dispon[íi]vel\\s+em|fonte\\s*:|in\\s*:|\\(?adaptado|\\(fragmento|\\(trecho|\\(com adapta|"
                    + "adaptado de|extra[íi]do de|acesso em).*");
    static final Pattern SOURCE_CONTINUATION = Pattern.compile("(?i).*(acesso em|https?://|www\\.|\\.com|\\.br|\\d{4}\\.?)\\s*\\)?$");
    static final Pattern IMAGE_HINT = Pattern.compile(
            "(?i)\\b(figura|gr[áa]fico|imagem|charge|tirinha|mapa|ilustra[çc][ãa]o|quadro a seguir|tabela a seguir)\\b");
    private static final String[] SUBJECT_KEYWORDS = {
            "lingua", "portugu", "matematic", "raciocinio", "logic", "direito", "legisla", "conhecimentos",
            "informatica", "atualidades", "administra", "contabil", "ingles", "espanhol", "historia", "geografia",
            "fisica", "quimica", "biologia", "etica", "nocoes", "estatistica", "economia", "arquivologia",
            "redacao", "especific", "gerais", "basicos", "saude", "pedagog", "educacao", "financ", "auditoria",
            "seguranca", "ciencias", "tecnologia", "gestao", "politica"};

    private enum Mode {NONE, BASE, STATEMENT, ALTERNATIVES}

    private static final class Open {
        final QuestionDraft draft = new QuestionDraft();
        final StringBuilder statement = new StringBuilder();
        final List<StringBuilder> alternatives = new ArrayList<>();
        final List<TextLine> lines = new ArrayList<>();
        TextLine lastLine;
        boolean useLowercase;

        boolean lowercaseAlternatives() {
            return useLowercase;
        }
    }

    private static final class OpenBase {
        final BaseTextDraft draft = new BaseTextDraft();
        final StringBuilder content = new StringBuilder();
        final StringBuilder source = new StringBuilder();
        Integer from;
        Integer to;
        boolean inSource;
        TextLine lastLine;
    }

    private final List<QuestionDraft> result = new ArrayList<>();
    private Mode mode = Mode.NONE;
    private Open current;
    private OpenBase base;
    private String subject;
    private int lastNumber;
    private boolean multipleChoiceSeen;

    public List<QuestionDraft> segment(List<PageContent> pages) {
        result.clear();
        mode = Mode.NONE;
        current = null;
        base = null;
        subject = null;
        lastNumber = 0;
        multipleChoiceSeen = false;
        for (PageContent p : pages) {
            for (TextLine l : p.lines()) {
                accept(l);
            }
        }
        finish();
        return new ArrayList<>(result);
    }

    private void accept(TextLine line) {
        String t = line.text().strip();
        if (t.isEmpty()) return;

        if (isSubjectHeading(line, t)) {
            finish();
            base = null;
            subject = toTitleCase(t);
            mode = Mode.NONE;
            return;
        }

        if (isBaseTextStart(t)) {
            finish();
            // "Leia o texto para responder às questões 1 a 3" seguido de "TEXTO I": mesmo texto-base.
            if (mode != Mode.BASE || base == null || !base.content.isEmpty()) base = new OpenBase();
            Matcher r = RANGE.matcher(t);
            if (r.find()) {
                base.from = Integer.parseInt(r.group(1));
                base.to = Integer.parseInt(r.group(2));
            }
            mode = Mode.BASE;
            // Rótulos e instruções não fazem parte do texto em si.
            return;
        }

        Matcher q = matchQuestionStart(line, t);
        if (q != null) {
            finish();
            current = new Open();
            current.draft.setNumber(Integer.parseInt(q.group(1)));
            current.draft.setSubjectName(subject);
            mode = Mode.STATEMENT;
            String rest = q.groupCount() >= 2 && q.group(2) != null ? q.group(2).strip() : "";
            if (!rest.isEmpty()) appendToStatement(line.withText(rest));
            current.lines.add(line);
            return;
        }

        if (current != null && (mode == Mode.STATEMENT || mode == Mode.ALTERNATIVES) && tryAlternatives(line, t)) {
            return;
        }

        if (mode == Mode.BASE && base != null && (SOURCE.matcher(t).matches()
                || base.inSource && SOURCE_CONTINUATION.matcher(t).matches())) {
            if (!base.source.isEmpty()) base.source.append(' ');
            base.source.append(t);
            base.inSource = true;
            base.lastLine = line;
            return;
        }

        switch (mode) {
            case BASE -> {
                base.inSource = false;
                join(base.content, base.lastLine, line);
                base.lastLine = line;
            }
            case STATEMENT -> {
                appendToStatement(line);
                current.lines.add(line);
            }
            case ALTERNATIVES -> {
                join(current.alternatives.getLast(), current.lastLine, line);
                current.lastLine = line;
                current.lines.add(line);
            }
            case NONE -> {
                // Capa, instruções gerais etc.: ignorados.
            }
        }
    }

    private void appendToStatement(TextLine line) {
        join(current.statement, current.lastLine, line);
        current.lastLine = line;
    }

    // ---------- Reconhecimento ----------

    private boolean isBaseTextStart(String t) {
        boolean label = TEXT_LABEL.matcher(t).matches() && t.length() <= 60;
        boolean forQuestions = FOR_QUESTIONS.matcher(t).matches() || RANGE.matcher(t).find() && t.length() < 160;
        boolean read = READ_INSTRUCTION.matcher(t).matches();
        if (label || forQuestions) return true;
        // "Leia o texto..." dentro do enunciado de uma questão faz parte do enunciado.
        return read && (mode != Mode.STATEMENT || current == null || !current.alternatives.isEmpty());
    }

    private Matcher matchQuestionStart(TextLine line, String t) {
        Matcher m = QUESTION_LABEL.matcher(t);
        if (m.matches()) return m;

        boolean canStartNumbered = current == null
                || !current.alternatives.isEmpty()
                || (!multipleChoiceSeen && mode == Mode.STATEMENT && current.statement.length() > 20);
        if (!canStartNumbered) return null;

        Integer open = current == null ? null : current.draft.getNumber();
        int previous = open != null ? open : lastNumber;
        for (Pattern p : new Pattern[]{NUMBERED, NUMBER_ONLY, NUMBER_SPACE}) {
            m = p.matcher(t);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                boolean sequential = previous == 0 ? n > 0 && n <= 300 : n == previous + 1;
                if (!sequential) continue;
                if (p == NUMBER_ONLY && !(line.bold() || mode != Mode.STATEMENT || current == null)) continue;
                // "1 Texto..." sem pontuação: na primeira questão, só se for o item 1 ou estiver em negrito.
                if (p == NUMBER_SPACE && previous == 0 && n != 1 && !line.bold()) continue;
                return m;
            }
        }
        return null;
    }

    private boolean tryAlternatives(TextLine line, String t) {
        char expected = (char) ('A' + current.alternatives.size());
        if (expected > 'E') return false;

        // Várias alternativas na mesma linha: "(A) 10 (B) 20 (C) 30".
        Matcher inline = INLINE_ALTERNATIVE.matcher(t);
        List<int[]> marks = new ArrayList<>();
        char want = expected;
        while (inline.find()) {
            if (inline.group(1).charAt(0) == want) {
                marks.add(new int[]{inline.start(), inline.end()});
                want++;
            }
        }
        if (marks.size() >= 2 && marks.getFirst()[0] == 0) {
            for (int i = 0; i < marks.size(); i++) {
                int end = i + 1 < marks.size() ? marks.get(i + 1)[0] : t.length();
                addAlternative(t.substring(marks.get(i)[1], end).strip(), line);
            }
            return true;
        }

        Matcher m = ALTERNATIVE.matcher(t);
        if (!m.matches()) return false;
        char letter = Character.toUpperCase(m.group(1).charAt(0));
        if (letter != expected) return false;
        // "a)" minúsculo só vale se o padrão minúsculo já começou com "a".
        if (Character.isLowerCase(m.group(1).charAt(0)) && expected != 'A' && !current.lowercaseAlternatives()) {
            return false;
        }
        if (Character.isLowerCase(m.group(1).charAt(0)) && expected == 'A') current.useLowercase = true;
        addAlternative(m.group(2).strip(), line);
        return true;
    }

    private void addAlternative(String text, TextLine line) {
        current.alternatives.add(new StringBuilder(text));
        current.lastLine = line;
        current.lines.add(line);
        mode = Mode.ALTERNATIVES;
        multipleChoiceSeen = true;
    }

    private boolean isSubjectHeading(TextLine line, String t) {
        if (t.length() < 4 || t.length() > 70 || t.matches(".*\\d.*") || t.endsWith(".") || t.endsWith(":")) {
            return false;
        }
        if (mode == Mode.STATEMENT && current != null && current.alternatives.isEmpty()) return false;
        long letters = t.chars().filter(Character::isLetter).count();
        long upper = t.chars().filter(Character::isUpperCase).count();
        boolean caps = letters > 0 && upper >= letters * 0.85;
        if (!caps && !line.bold()) return false;
        String n = stripAccents(t).toLowerCase(Locale.ROOT);
        for (String k : SUBJECT_KEYWORDS) {
            if (n.contains(k)) return true;
        }
        return false;
    }

    // ---------- Finalização ----------

    private void finish() {
        if (current == null) return;
        QuestionDraft d = current.draft;
        d.setStatement(current.statement.toString().strip());
        for (int i = 0; i < current.alternatives.size(); i++) {
            d.addAlternative(String.valueOf((char) ('A' + i)), current.alternatives.get(i).toString().strip(), false);
        }
        if (current.alternatives.isEmpty() && !multipleChoiceSeen) {
            d.setTrueFalse(true);
            d.addAlternative("C", "Certo", false);
            d.addAlternative("E", "Errado", false);
        }

        if (base != null && !base.content.isEmpty()) {
            Integer n = d.getNumber();
            boolean inRange = base.from == null || n != null && n >= base.from && n <= base.to;
            if (inRange) {
                base.draft.setContent(base.content.toString().strip());
                base.draft.setSource(base.source.isEmpty() ? null : base.source.toString().strip());
                d.setBaseText(base.draft);
            }
        }

        score(d);
        d.setRegion(region(current.lines));
        d.setOcr(current.lines.stream().anyMatch(l -> l.confidence() < 100));
        result.add(d);
        if (d.getNumber() != null) lastNumber = d.getNumber();
        current = null;
        mode = Mode.NONE;
    }

    private void score(QuestionDraft d) {
        double c = 1.0;
        int alts = d.isTrueFalse() ? 2 : d.getAlternatives().size();
        if (!d.isTrueFalse() && alts < 4) {
            c -= alts < 2 ? 0.5 : 0.25;
            d.getWarnings().add("Foram encontradas " + alts + " alternativas.");
        }
        if (d.getStatement().length() < 15) {
            c -= 0.3;
            d.getWarnings().add("Enunciado muito curto ou ausente.");
        }
        if (lastNumber > 0 && d.getNumber() != null && d.getNumber() != lastNumber + 1) {
            c -= 0.1;
            d.getWarnings().add("Numeração fora de sequência (anterior: " + lastNumber + ").");
        }
        double avgConf = current.lines.stream().mapToDouble(TextLine::confidence).average().orElse(100);
        if (avgConf < 100) {
            c *= 0.5 + 0.5 * avgConf / 100;
            if (avgConf < 75) d.getWarnings().add(String.format("Leitura por OCR com confiança baixa (%.0f%%).", avgConf));
        }
        String all = d.getStatement() + (d.getBaseText() == null ? "" : " " + d.getBaseText().getContent());
        if (IMAGE_HINT.matcher(all).find()) {
            d.getWarnings().add("Pode depender de uma imagem: considere anexar o recorte da página.");
        }
        d.setConfidence(c);
    }

    /** Área da questão na primeira página em que aparece, limitada à coluna do início. */
    private static Region region(List<TextLine> lines) {
        if (lines.isEmpty()) return null;
        TextLine first = lines.getFirst();
        double x0 = first.x(), x1 = first.right(), y0 = first.y(), y1 = first.bottom();
        for (TextLine l : lines) {
            if (l.page() != first.page()) continue;
            boolean sameColumn = l.x() < first.x() + Math.max(first.width(), 200) && l.right() > first.x() - 20;
            if (!sameColumn || l.y() < first.y() - 5) continue;
            x0 = Math.min(x0, l.x());
            x1 = Math.max(x1, l.right());
            y1 = Math.max(y1, l.bottom());
        }
        return new Region(first.page(), x0, y0, x1, y1);
    }

    // ---------- Texto ----------

    /** Junta uma linha ao texto, tratando hifenização e quebras de parágrafo. */
    static void join(StringBuilder sb, TextLine prev, TextLine line) {
        String t = line.text().strip();
        if (sb.isEmpty()) {
            sb.append(t);
            return;
        }
        boolean paragraph = prev != null && prev.page() == line.page()
                && Math.abs(prev.x() - line.x()) < 250
                && line.bottom() - prev.bottom() > Math.max(prev.fontSize(), 6) * 1.75;
        int last = sb.length() - 1;
        if (!paragraph && sb.charAt(last) == '-' && last > 0 && Character.isLetter(sb.charAt(last - 1))
                && !t.isEmpty() && Character.isLowerCase(t.charAt(0))) {
            sb.setLength(last);
            sb.append(t);
        } else {
            sb.append(paragraph ? "\n" : " ").append(t);
        }
    }

    private static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String toTitleCase(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (String w : lower.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            boolean small = w.length() <= 2 || w.equals("das") || w.equals("dos");
            sb.append(small && !sb.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return sb.toString();
    }
}
