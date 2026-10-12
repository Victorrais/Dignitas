package br.com.dignitas.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extrai o texto nativo de uma página com a posição de cada linha.
 * <p>
 * Em vez de usar o texto corrido do {@link PDFTextStripper} (que mistura colunas lado a lado),
 * coletamos os glifos e montamos as linhas, quebrando-as onde há um espaço horizontal grande
 * (calha entre colunas). A ordem de leitura é decidida depois, pelo {@link LayoutAnalyzer}.
 */
public class PdfTextExtractor {

    public PageContent extract(PDDocument doc, int pageIndex) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle box = page.getCropBox();
        boolean rotated = page.getRotation() % 180 != 0;
        double width = rotated ? box.getHeight() : box.getWidth();
        double height = rotated ? box.getWidth() : box.getHeight();

        GlyphCollector collector = new GlyphCollector();
        collector.setStartPage(pageIndex + 1);
        collector.setEndPage(pageIndex + 1);
        collector.getText(doc);
        return new PageContent(pageIndex, width, height, buildLines(collector.glyphs, pageIndex), false);
    }

    /** Coleta os glifos horizontais, descartando duplicatas usadas para simular negrito. */
    private static final class GlyphCollector extends PDFTextStripper {
        final List<TextPosition> glyphs = new ArrayList<>();

        GlyphCollector() throws IOException {
            setSortByPosition(false);
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            if (t.getDir() != 0f) return; // texto vertical (margens, carimbos)
            String u = t.getUnicode();
            if (u == null || u.isEmpty()) return;
            int from = Math.max(0, glyphs.size() - 40);
            for (int i = glyphs.size() - 1; i >= from; i--) {
                TextPosition o = glyphs.get(i);
                if (u.equals(o.getUnicode())
                        && Math.abs(o.getXDirAdj() - t.getXDirAdj()) < Math.max(1f, t.getWidthDirAdj() * 0.3f)
                        && Math.abs(o.getYDirAdj() - t.getYDirAdj()) < 1f) {
                    return;
                }
            }
            glyphs.add(t);
        }
    }

    static List<TextLine> buildLines(List<TextPosition> glyphs, int page) {
        List<TextPosition> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator.comparingDouble(TextPosition::getYDirAdj).thenComparingDouble(TextPosition::getXDirAdj));

        List<TextLine> lines = new ArrayList<>();
        List<TextPosition> row = new ArrayList<>();
        double rowY = Double.NaN;
        for (TextPosition g : sorted) {
            double tolerance = Math.max(1.5, size(g) * 0.35);
            if (!row.isEmpty() && g.getYDirAdj() - rowY > tolerance) {
                splitRow(row, page, lines);
                row.clear();
            }
            if (row.isEmpty()) rowY = g.getYDirAdj();
            row.add(g);
        }
        if (!row.isEmpty()) splitRow(row, page, lines);
        return lines;
    }

    private static double size(TextPosition g) {
        double s = g.getFontSizeInPt();
        if (s <= 1) s = g.getHeightDir() * 1.4;
        return s <= 1 ? 10 : s;
    }

    /** Quebra uma linha visual em trechos separados por espaços grandes e monta o texto. */
    private static void splitRow(List<TextPosition> row, int page, List<TextLine> out) {
        row.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
        StringBuilder text = new StringBuilder();
        List<TextPosition> segment = new ArrayList<>();
        TextPosition prev = null;
        for (TextPosition g : row) {
            String u = g.getUnicode();
            boolean blank = u.isBlank();
            if (prev != null) {
                double gap = g.getXDirAdj() - (prev.getXDirAdj() + prev.getWidthDirAdj());
                double fs = size(g);
                if (gap > Math.max(1.8 * fs, 14)) {
                    emit(segment, text, page, out);
                    segment.clear();
                    text.setLength(0);
                } else if (!blank && gap > fs * 0.18 && !text.isEmpty() && text.charAt(text.length() - 1) != ' ') {
                    text.append(' ');
                }
            }
            if (blank) {
                if (!text.isEmpty() && text.charAt(text.length() - 1) != ' ') text.append(' ');
            } else {
                text.append(u);
                segment.add(g);
            }
            prev = g;
        }
        emit(segment, text, page, out);
    }

    private static void emit(List<TextPosition> segment, StringBuilder text, int page, List<TextLine> out) {
        String s = text.toString().strip();
        if (segment.isEmpty() || s.isEmpty()) return;
        double x0 = Double.MAX_VALUE, x1 = 0, top = Double.MAX_VALUE, bottom = 0, fs = 0;
        int bold = 0;
        for (TextPosition g : segment) {
            x0 = Math.min(x0, g.getXDirAdj());
            x1 = Math.max(x1, g.getXDirAdj() + g.getWidthDirAdj());
            double h = Math.max(g.getHeightDir(), size(g) * 0.7);
            top = Math.min(top, g.getYDirAdj() - h);
            bottom = Math.max(bottom, g.getYDirAdj());
            fs = Math.max(fs, size(g));
            if (isBold(g.getFont())) bold++;
        }
        out.add(new TextLine(s, x0, top, x1 - x0, bottom - top, fs, bold * 2 > segment.size(), page, 100));
    }

    private static boolean isBold(PDFont font) {
        if (font == null) return false;
        String name = font.getName();
        if (name != null && name.toLowerCase().contains("bold")) return true;
        PDFontDescriptor d = font.getFontDescriptor();
        return d != null && (d.isForceBold() || d.getFontWeight() >= 600);
    }
}
