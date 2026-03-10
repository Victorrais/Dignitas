package data.models;

import jakarta.persistence.*;

@Entity
public class History {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHistory;
    private boolean review;

    @OneToOne
    @JoinColumn(name = "idAlternative")
    private Alternative fkAnswered;

    @OneToOne
    @JoinColumn(name = "idQuestions")
    private Question fkQuestions;

}
