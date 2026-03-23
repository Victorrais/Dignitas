package data.models;

import jakarta.persistence.*;

@Entity
public class Alternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long idAlternative;
    @Column(nullable = false)
    private String statement;
    @Column(nullable = false, name = "is_answer")
    private boolean isAnswer;
    @ManyToOne
    @JoinColumn(name = "idQuestions", nullable = false)
    private Question question;

    public long getIdAlternative() {
        return idAlternative;
    }

    public Question getIdQuestion() {
        return question;
    }

    public void setIdQuestion(Question idQuestion) {
        this.question = idQuestion;
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
    @Override
    public String toString() {
        return statement.trim();
    }
}
