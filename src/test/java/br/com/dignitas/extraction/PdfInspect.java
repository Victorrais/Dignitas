package br.com.dignitas.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ferramenta de desenvolvimento: renderiza as páginas e despeja linhas (com posição) e figuras
 * encontradas, para estudar o layout de uma prova. Uso: {@code PdfInspect <pdf> <pasta> [dpi]}.
 */
public final class PdfInspect {
    private PdfInspect() {
    }

    public static void main(String[] args) throws Exception {
        File pdf = new File(args[0]);
        File out = new File(args[1]);
        float dpi = args.length > 2 ? Float.parseFloat(args[2]) : 60;
        out.mkdirs();
        PdfTextExtractor extractor = new PdfTextExtractor();
        PdfImageLocator images = new PdfImageLocator();
        try (PDDocument doc = Loader.loadPDF(pdf);
             PrintWriter w = new PrintWriter(new File(out, "dump.txt"), StandardCharsets.UTF_8)) {
            PDFRenderer r = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                if (dpi > 0) ImageIO.write(r.renderImageWithDPI(i, dpi, ImageType.RGB), "png", new File(out, "p" + (i + 1) + ".png"));
                PageContent p = extractor.extract(doc, i);
                w.printf("=== PÁGINA %d (%.0fx%.0f) ===%n", i + 1, p.width(), p.height());
                for (ImageBox b : images.locate(doc, i)) {
                    w.printf("  [%s x=%.0f y=%.0f w=%.0f h=%.0f]%n", b.raster() ? "IMG" : "VEC", b.x(), b.y(), b.width(), b.height());
                }
                List<TextLine> lines = LayoutAnalyzer.readingOrder(p.lines(), p.width());
                for (TextLine l : lines) {
                    w.printf("%5.0f %5.0f %4.0f %4.1f%s | %s%n", l.x(), l.y(), l.width(), l.fontSize(), l.bold() ? "B" : " ", l.text());
                }
            }
        }
        System.out.println("ok");
    }
}
