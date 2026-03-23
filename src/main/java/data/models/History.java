package data.models;

import jakarta.persistence.*;

@Entity
public class History {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long idHistory;
    private boolean review=false;

    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "idAlternative")
    private Alternative fkAnswered;

    @ManyToOne()
    @JoinColumn(name = "idQuestions")
    private Question fkQuestion;

    public History() {
    }

    public History(Alternative fkAnswered, Question fkQuestion) {
        this.fkAnswered = fkAnswered;
        this.fkQuestion = fkQuestion;
    }

    public Alternative getFkAnswered() {
        return fkAnswered;
    }

    public Question getFkQuestion() {
        return fkQuestion;
    }

    public long getIdHistory() {
        return idHistory;
    }

    @Override
    public String toString(){
        return "Historico: "+idHistory+"Questão: "+fkQuestion+"Alternativa: "+fkAnswered.toString();
    }
}
