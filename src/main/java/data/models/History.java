package data.models;
public class History {

    private long idHistory;
    private boolean review=false;

    private Alternative fkAnswered;


    private long fkQuestion;

    public History(Alternative fkAnswered, long fkQuestion) {
        this.fkAnswered = fkAnswered;
        this.fkQuestion = fkQuestion;
    }
    @Override
    public String toString(){
        return "Historico: "+idHistory+"Questão: "+fkQuestion+"Alternativa: "+fkAnswered.toString();
    }
}
