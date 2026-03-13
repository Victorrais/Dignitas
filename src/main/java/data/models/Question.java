package data.models;
public class Question {


    private long idQuestion;


    private String statement, baseText, font;


    public long getIdQuestion() {
        return idQuestion;
    }

    public void setIdQuestion(long idQuestion) {
        this.idQuestion = idQuestion;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getBaseText() {
        return baseText;
    }

    public void setBaseText(String baseText) {
        this.baseText = baseText.replace("\r","\n");
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    @Override
    public String toString(){
        return  "\nQuestão: "+
                idQuestion+"\n"+
                baseText+"\n"+
                font+"\n\n"+
                statement+"\n";
    }

}
