package br.com.dignitas.extraction;

/**
 * Uma linha (ou trecho de linha) de texto com sua posição na página, em pontos,
 * com origem no canto superior esquerdo. Vem do PDFBox (texto nativo) ou do Tesseract (OCR).
 *
 * @param confidence 0–100; 100 para texto nativo do PDF
 */
public record TextLine(String text, double x, double y, double width, double height,
                       double fontSize, boolean bold, int page, double confidence) {

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public TextLine withText(String newText) {
        return new TextLine(newText, x, y, width, height, fontSize, bold, page, confidence);
    }
}
