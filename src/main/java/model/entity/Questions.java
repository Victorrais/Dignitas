package model.entity;

import jakarta.persistence.*;

@Entity
public class Questions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idQuestion;

    @Column(nullable = false)
    private String statement, subject;

    @OneToMany
    @JoinColumn(name = "idExam")
    private Exam fkExam;
}
