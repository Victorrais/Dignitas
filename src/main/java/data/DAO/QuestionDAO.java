package data.DAO;

import data.models.ContentBlock;
import data.models.JPAUtil;
import data.models.Question;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.List;

public class QuestionDAO {

    public List<Question> selectQuestions( ){

        EntityManager em = JPAUtil.getCon();
        Query q = em.createQuery("SELECT q from Question q");
        List<Question> questions = q.getResultList();
        JPAUtil.closeCon();
        return questions;
    }

    public void addQuestion(List<ContentBlock> blocks){
        Question q = new Question();
        for (ContentBlock b : blocks){
            try{
                q.setBaseText("asd");//todo: refatorar
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        EntityManager em =JPAUtil.getCon();
        em.getTransaction().begin();
        em.persist(q);
        em.getTransaction().commit();
        JPAUtil.closeCon();
    }
}