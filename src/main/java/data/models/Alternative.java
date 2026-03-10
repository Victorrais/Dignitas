package data.models;

import jakarta.persistence.*;

@Entity
public class Alternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAlternative;

    @Column(nullable = false)
    private String statement;

    @Column(nullable = false, name = "is_answer")
    private boolean isAnswer;

    @ManyToOne
    @JoinColumn(name = "idQuestions")
    private Question idQuestion;

    public void setIdQuestion(Question idQuestion) {
        this.idQuestion = idQuestion;
    }
    @Override
    public String toString() {
        return statement;
    }
}
