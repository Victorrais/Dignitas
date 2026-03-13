package data;

import data.models.ContentBlock;
import data.models.Question;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;


import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class pdfParser {
    private static final String PATH= "src/main/resources/testPage.pdf";
    private PDDocument doc;


    public String loadDoc()throws IOException {
        try{
            doc = Loader.loadPDF(
                    new RandomAccessReadBufferedFile(PATH)
            );
        } catch (Exception e){
            throw new RuntimeException(e);
        }
        Splitter splitter = new Splitter();
        List<PDDocument> list=splitter.split(doc);

        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(list.get(1));
    }
}
