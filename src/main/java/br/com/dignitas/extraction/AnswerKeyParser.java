package br.com.dignitas.extraction;

import br.com.dignitas.model.QuestionDraft;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê gabaritos nos dois formatos mais comuns:
 * <ul>
 *   <li>pares na mesma linha: "01 - A   02 - C", "1. E", "3 ANULADA";</li>
 *   <li>tabela: uma linha só com números seguida de uma linha só com respostas.</li>
 * </ul>
 * Respostas "X", "*", "NULA" ou "ANULADA" marcam a questão como anulada.
 * Se houver mais de um gabarito (tipos de prova), vale a primeira ocorrência de cada número.
 */
public class AnswerKeyParser {
    public static final String ANNULLED = "X";

    private static final Pattern PAIR = Pattern.compile(
            "(?<![\\d/])(\\d{1,3})\\s*[-–—:.)]?\\s*(ANULADA|Anulada|anulada|NULA|[A-E]|X|\\*)(?![\\p{L}\\d])");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d{1,3}");
    private static final Pattern ANSWER_TOKEN = Pattern.compile("(?i)[A-E]|X|\\*|ANULADA|NULA");

    public Map<Integer, String> parse(List<PageContent> pages) {
        List<String> lines = new ArrayList<>();
        for (PageContent p : pages) {
            for (TextLine l : p.lines()) lines.add(l.text().strip());
        }
        return parseLines(lines);
    }

    public Map<Integer, String> parseLines(List<String> lines) {
        Map<Integer, String> key = new LinkedHashMap<>();
        boolean[] used = new boolean[lines.size()];

        for (int i = 0; i + 1 < lines.size(); i++) {
            List<String> nums = tokens(lines.get(i), NUMBER_TOKEN);
            if (nums == null || nums.size() < 3) continue;
            for (int j = i + 1; j < Math.min(lines.size(), i + 3); j++) {
                List<String> answers = tokens(lines.get(j), ANSWER_TOKEN);
                if (answers != null && answers.size() == nums.size()) {
                    for (int k = 0; k < nums.size(); k++) {
                        key.putIfAbsent(Integer.parseInt(nums.get(k)), normalize(answers.get(k)));
                    }
                    used[i] = used[j] = true;
                    break;
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            if (used[i]) continue;
            Matcher m = PAIR.matcher(lines.get(i));
            while (m.find()) {
                key.putIfAbsent(Integer.parseInt(m.group(1)), normalize(m.group(2)));
            }
        }
        return key;
    }

    /** Todos os tokens da linha casam com o padrão? Retorna-os, ou null. */
    private static List<String> tokens(String line, Pattern p) {
        String[] parts = line.strip().split("[\\s|;,]+");
        List<String> out = new ArrayList<>();
        for (String s : parts) {
            if (s.isEmpty()) continue;
            if (!p.matcher(s).matches()) return null;
            out.add(s);
        }
        return out;
    }

    private static String normalize(String answer) {
        String a = answer.toUpperCase(Locale.ROOT);
        return a.length() == 1 && a.charAt(0) >= 'A' && a.charAt(0) <= 'E' ? a : ANNULLED;
    }

    /** Aplica o gabarito aos rascunhos. Retorna quantas questões receberam resposta. */
    public static int apply(Map<Integer, String> key, List<QuestionDraft> drafts) {
        int applied = 0;
        for (QuestionDraft d : drafts) {
            if (d.getNumber() == null) continue;
            String answer = key.get(d.getNumber());
            if (answer == null) {
                d.getWarnings().add("Questão não encontrada no gabarito.");
                continue;
            }
            if (ANNULLED.equals(answer)) {
                d.setAnnulled(true);
                applied++;
            } else if (d.setAnswerLetter(answer)) {
                applied++;
            } else {
                d.getWarnings().add("Gabarito indica " + answer + ", mas essa alternativa não foi encontrada.");
            }
        }
        return applied;
    }
}
