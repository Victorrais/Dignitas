import data.models.Alternative;
import data.models.Question;
import data.pdfParser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main{

    public void main() throws UnsupportedEncodingException {
        /*
        por nas regras de negócios um sumário de cada questão adicionada pelo
        extrator de questões após o upload da prova
         */
        enum state {
            START,
            TEXT,
            FONT,
            STATEMENT,
            ALTERNATIVE,
            QUESTION
        }
        boolean stop=false;
        var status = state.START;
        List<Alternative> alternativeList=new ArrayList<>();
        List<Question> questionList=new ArrayList<>();
        // Demonstração da extração de questões:
        while (!stop) {
            var parser = new pdfParser();
            var build = new StringBuilder();
            Question currentQuestion=null;
            try {
                var doc =
                        parser.loadDoc();
                String[] lines=doc.split("\\n");
                var question_pattern= Pattern.compile("Quest[ãa]o (\\d+)\\t\\r");
                var alternative_pattern= Pattern.compile("[A-Z]\t");
                int i=0;
                Matcher matcher=null;
                for(String line : lines){
                    line.trim();
                    if(line.isEmpty()) continue;
                    if (status==state.START)matcher = question_pattern.matcher(line);

                    if(status==state.START&&matcher.find()||status==state.QUESTION){
                        currentQuestion = new Question();
                        //setter de id para question porque a demonstraçao não está usando persistencia com auto_increment.
                        i = Integer.parseInt(matcher.group(1));
                        currentQuestion.setIdQuestion(i);
                        status=state.TEXT; //QUESTION
                        if(status!=state.QUESTION)continue;

                    }
                    if (status==state.START) continue;


                    //TEXT
                    if((line.contains("(adaptado).")||line.contains("Disponível em:")||line.contains("(fragmento).")||line.contains("Acesso em:"))){
                        currentQuestion.setBaseText(build.toString().trim());
                        build=new StringBuilder();
                        build.append(line).append(" ");
                        status=state.FONT;
                        continue;
                    }
                    else if (status==state.FONT) {
                        currentQuestion.setFont((build.toString().trim()));

                        status=state.STATEMENT;
                    }

                    matcher=alternative_pattern.matcher(line);
                    if (status==state.STATEMENT){
                        currentQuestion.setStatement((line.toString().trim()).replace("\r","\n"));
                        build=new StringBuilder();
                        status=state.ALTERNATIVE;
                        continue;
                    }
                    if (status==state.ALTERNATIVE&&matcher.find()){
                            Alternative alt = new Alternative();
                            alt.setIdQuestion(i);
                            alt.setStatement(line);
                            alternativeList.add(alt);
                    } else if (status==state.TEXT) build.append(line).append(" ");
                    else {

                        questionList.add(currentQuestion);
                        matcher = question_pattern.matcher(line);
                        if(matcher.find()){
                            status=state.QUESTION;
                        } else{
                            status=state.START;
                        }

                    }
                }
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            stop=true;
        }

        // Demonstração da disposição de questoes
        for(Question q : questionList){
            System.out.println(q);
            for(Alternative a : alternativeList){
                if(a.getIdQuestion()==q.getIdQuestion()){
                    System.out.println(a);
                }
            }
        }
    }
}
