package data.models;

import jakarta.persistence.*;

@Entity
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idQuestion;

    @Column(nullable = false)
    private String statement, baseText, font;

}
