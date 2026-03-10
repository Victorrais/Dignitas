package data.models;

import jakarta.persistence.*;

@Entity
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idQuestion;

    @Column(nullable = false)
    private String statement, baseText, font;

    public Long getId() {
        return idQuestion;
    }

    public void createQuestion(ContentBlock block){
        switch (block.toString()){
            case "Font" ->
                    font=block.blockFormat();
            case "Statement" ->
                    statement=block.blockFormat();
            case "text" ->
                    baseText=block.blockFormat();
        }
    }

    @Override
    public String toString(){
        return "\n"+baseText+"\n"+
                font+"\n\n"+
                statement;
    }

}
