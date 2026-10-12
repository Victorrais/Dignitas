package br.com.dignitas.extraction;

import java.io.File;

/**
 * Gera uma prova e um gabarito de exemplo (duas colunas, texto-base, cabeçalho repetido)
 * para testar manualmente a importação. Uso: {@code SamplePdfs <pasta>}.
 */
public final class SamplePdfs {
    private SamplePdfs() {
    }

    /** Prova no formato ENEM com texto-base em imagem e alternativas em imagem. */
    static void enemStyle(File dir) throws Exception {
        TestPdf pdf = new TestPdf();
        pdf.bold(0, 40, 50, "CIÊNCIAS DA NATUREZA E SUAS TECNOLOGIAS");
        pdf.text(0, 40, 80, "Texto para as Questões de 01 a 02.");
        pdf.image(0, 60, 90, 220, 110, java.awt.Color.LIGHT_GRAY);
        pdf.text(0, 40, 215, "Disponível em: www.exemplo.com.br. Acesso em: 1 jan. 2025.");
        pdf.bold(0, 40, 245, "QUESTÃO 01");
        pdf.block(0, 40, 263, "Com base na figura, o fenômeno representado é a");
        pdf.block(0, 40, 280, "A fotossíntese.", "B respiração celular.", "C fermentação.", "D transpiração.", "E germinação.");
        pdf.bold(0, 40, 370, "QUESTÃO 02");
        pdf.text(0, 40, 388, "Qual gráfico representa corretamente a situação descrita no texto?");
        java.awt.Color[] colors = {new java.awt.Color(0xE57373), new java.awt.Color(0x81C784), new java.awt.Color(0x64B5F6),
                new java.awt.Color(0xFFB74D), new java.awt.Color(0xBA68C8)};
        for (int i = 0; i < 5; i++) {
            float y = 425 + i * 50;
            pdf.text(0, 40, y, String.valueOf((char) ('A' + i)));
            pdf.image(0, 60, y - 18, 120, 40, colors[i]);
        }
        pdf.save(dir, "enem-imagens.pdf");
        TestPdf key = new TestPdf();
        key.block(0, 60, 80, "GABARITO", "01 - A   02 - D");
        key.save(dir, "enem-imagens-gabarito.pdf");
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        TestPdf pdf = new TestPdf();
        for (int p = 0; p < 3; p++) {
            pdf.text(p, 40, 30, "PREFEITURA MUNICIPAL DE EXEMPLO – CONCURSO PÚBLICO 2025 – ANALISTA");
            pdf.text(p, 290, 820, "Página " + (p + 1));
        }
        pdf.bold(0, 40, 60, "LÍNGUA PORTUGUESA");
        float y = pdf.block(0, 40, 90,
                "Leia o texto para responder às questões 1 a 3.",
                "TEXTO I",
                "A leitura amplia horizontes e",
                "desenvolve o pensamento crítico.",
                "Em tempos de informação rápida,",
                "ler com atenção tornou-se uma",
                "habilidade valiosa para estudantes",
                "e profissionais de todas as áreas.");
        y = pdf.block(0, 40, y, "(Adaptado de: Revista Exemplo, 2024)");
        y = pdf.block(0, 40, y + 14,
                "1. Segundo o texto, a leitura:",
                "(A) limita o pensamento.",
                "(B) amplia horizontes.",
                "(C) é dispensável hoje.",
                "(D) prejudica estudantes.",
                "(E) só serve a profissionais.");
        pdf.block(0, 40, y + 14,
                "2. A expressão \"informação rápida\"",
                "refere-se ao contexto:",
                "(A) atual.",
                "(B) medieval.",
                "(C) futuro distante.",
                "(D) da Antiguidade.",
                "(E) rural.");
        pdf.block(0, 320, 90,
                "3. O texto pode ser classificado",
                "como predominantemente:",
                "(A) narrativo.",
                "(B) descritivo.",
                "(C) argumentativo.",
                "(D) injuntivo.",
                "(E) poético.",
                "",
                "4. Assinale a palavra acentuada",
                "corretamente.",
                "(A) ítem",
                "(B) juíz",
                "(C) saúde",
                "(D) caráteres",
                "(E) hífens");
        pdf.bold(1, 40, 60, "RACIOCÍNIO LÓGICO");
        pdf.block(1, 40, 90,
                "5. Se todo A é B e nenhum B é C, então:",
                "(A) algum A é C.",
                "(B) nenhum A é C.",
                "(C) todo C é A.",
                "(D) algum C é B.",
                "(E) todo B é A.",
                "",
                "6. Observe o gráfico a seguir e indique o valor máximo.",
                "(A) 10 (B) 20 (C) 30 (D) 40 (E) 50");
        pdf.save(dir, "prova-exemplo.pdf");

        TestPdf key = new TestPdf();
        key.block(0, 60, 80, "GABARITO DEFINITIVO", "1 2 3 4 5 6", "B A C C B X");
        key.save(dir, "gabarito-exemplo.pdf");
        enemStyle(dir);
        System.out.println("PDFs gerados em " + dir.getAbsolutePath());
    }
}
