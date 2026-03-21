package data.models;

import jakarta.persistence.*;

@Entity
public class History {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long idHistory;
    private boolean review=false;

    @OneToOne
    @JoinColumn(name = "idAlternative")
    private Alternative fkAnswered;

    @OneToOne
    @JoinColumn(name = "idQuestions")
    private Question fkQuestion;

    public History(Alternative fkAnswered, Question fkQuestion) {
        this.fkAnswered = fkAnswered;
        this.fkQuestion = fkQuestion;
    }
    @Override
    public String toString(){
        return "Historico: "+idHistory+"Questão: "+fkQuestion+"Alternativa: "+fkAnswered.toString();
    }
}
