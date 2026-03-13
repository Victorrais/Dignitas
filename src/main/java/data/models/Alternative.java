package data.models;
public class Alternative {


    private long idAlternative;


    private String statement;


    private boolean isAnswer;



    private long idQuestion;

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public long getIdQuestion() {
        return idQuestion;
    }

    public void setIdQuestion(long idQuestion) {
        this.idQuestion = idQuestion;
    }
    @Override
    public String toString() {
        return "\n"+statement;
    }
}
