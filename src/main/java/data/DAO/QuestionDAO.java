package data.DAO;

import data.models.JPAUtil;
import data.models.Question;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class QuestionDAO {

    public Question selectQuestionById(long id){//todo: verificar
        EntityManager em = JPAUtil.getCon();
        TypedQuery<Question> q = em.createQuery("SELECT q from Question q where q.idQuestion=:id", Question.class);
        q.setParameter("id",id);
        Question question =q.getSingleResult();


        return question;
    }
    public List<Question> selectAllQuestions( ){
        EntityManager em = JPAUtil.getCon();
        TypedQuery<Question> q = em.createQuery("SELECT q from Question q", Question.class);
        List<Question> questions =q.getResultList();


        return questions;
    }
    public List<Question> selectNewQuestions(){
        /*
        por enquanto o method chama todas as questões presentes no database,
        o que pode ser problemático posteriormente.
         */
        EntityManager em = JPAUtil.getCon();
        TypedQuery<Question> q = em.createQuery("SELECT q from Question q where not exists (select h from History h where h.fkQuestion=q)", Question.class);
        List<Question> questions =q.getResultList();


        return questions;
    }

    public void addQuestion(Question q){
        EntityManager em =JPAUtil.getCon();
        em.getTransaction().begin();
        em.persist(q);
        em.getTransaction().commit();

    }

    public void deleteQuestion(long l) {

        EntityManager em =JPAUtil.getCon();
        try {
            em.getTransaction().begin();

            Question q = em.find(Question.class, l);
            if(q!=null) em.remove(q);

            em.getTransaction().commit();

        } catch (Exception ex){
            em.getTransaction().rollback();
            throw new RuntimeException(ex);
        }


    }
}