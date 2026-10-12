package br.com.dignitas.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Monta PDFs de teste escrevendo cada linha numa posição fixa. */
final class TestPdf {
    record Item(int page, float x, float y, String text, boolean bold) {
    }

    /** Retângulo colorido desenhado como imagem rasterizada (simula figuras). */
    record Picture(int page, float x, float y, float w, float h, Color color) {
    }

    private final List<Picture> pictures = new ArrayList<>();

    TestPdf image(int page, float x, float yFromTop, float w, float h, Color color) {
        pictures.add(new Picture(page, x, yFromTop, w, h, color));
        pages = Math.max(pages, page + 1);
        return this;
    }

    private final List<Item> items = new ArrayList<>();
    private int pages = 1;

    TestPdf text(int page, float x, float yFromTop, String text) {
        items.add(new Item(page, x, yFromTop, text, false));
        pages = Math.max(pages, page + 1);
        return this;
    }

    TestPdf bold(int page, float x, float yFromTop, String text) {
        items.add(new Item(page, x, yFromTop, text, true));
        pages = Math.max(pages, page + 1);
        return this;
    }

    /** Escreve um bloco de linhas a partir de y, avançando 14 pt por linha. Retorna o próximo y. */
    float block(int page, float x, float y, String... lines) {
        for (String l : lines) {
            text(page, x, y, l);
            y += 14;
        }
        return y;
    }

    File save(File dir, String name) throws IOException {
        File f = new File(dir, name);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        try (PDDocument doc = new PDDocument()) {
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    for (Picture pic : pictures) {
                        if (pic.page() != p) continue;
                        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = img.createGraphics();
                        g.setColor(pic.color());
                        g.fillRect(0, 0, 40, 30);
                        g.dispose();
                        PDImageXObject x = LosslessFactory.createFromImage(doc, img);
                        cs.drawImage(x, pic.x(), PDRectangle.A4.getHeight() - pic.y() - pic.h(), pic.w(), pic.h());
                    }
                    for (Item it : items) {
                        if (it.page() != p) continue;
                        cs.beginText();
                        cs.setFont(it.bold() ? bold : regular, 10);
                        cs.newLineAtOffset(it.x(), PDRectangle.A4.getHeight() - it.y());
                        cs.showText(it.text());
                        cs.endText();
                    }
                }
            }
            doc.save(f);
        }
        return f;
    }
}
