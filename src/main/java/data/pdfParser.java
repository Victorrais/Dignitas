package data;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class pdfParser {
    private PDDocument doc;

    public String loadDoc(int p)throws IOException {
        try{
            InputStream is = getClass().getResourceAsStream("/testPage.pdf");
            doc = Loader.loadPDF(is.readAllBytes());
        } catch (Exception e){
            throw new RuntimeException(e);
        }
        Splitter splitter = new Splitter();
        List<PDDocument> list=splitter.split(doc);

        PDFTextStripper stripper = new PDFTextStripper();
        StringBuilder str=new StringBuilder();
        return stripper.getText(list.get(p));
    }
    public String loadDoc()throws IOException {
        return loadDoc(1)+loadDoc(9)+loadDoc(24);
    }
}
