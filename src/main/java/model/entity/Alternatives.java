package model.entity;

import jakarta.persistence.*;

@Entity
public class Alternatives {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAlternative;

    @Column(nullable = false)
    private String statement;

    @Column(nullable = false, name = "is_answer")
    private boolean isAnswer;

    @OneToMany
    @JoinColumn(name = "idQuestions")
    private Questions idQuestion;


    public Long getIdAlternative() {
        return idAlternative;
    }

    public void setIdAlternative(Long idAlternative) {
        this.idAlternative = idAlternative;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public boolean isAnswer() {
        return isAnswer;
    }

    public void setAnswer(boolean answer) {
        isAnswer = answer;
    }

    public Questions getIdQuestion() {
        return idQuestion;
    }

    public void setIdQuestion(Questions idQuestion) {
        this.idQuestion = idQuestion;
    }
}
