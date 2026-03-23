package data.DAO;

import data.models.Alternative;
import data.models.History;
import data.models.JPAUtil;
import data.models.Question;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class HistoryDAO {

    public  void deleteHistory(long l) throws PersistenceException{
        EntityManager em =JPAUtil.getCon();
        try {
            em.getTransaction().begin();

            var qDao = new QuestionDAO();
            Question q = qDao.selectQuestionById(l);

            History h = selectHistoryByQuestion(l);
            if(h!=null) em.remove(h);

            em.getTransaction().commit();

        } catch (Exception ex){
            em.getTransaction().rollback();
            throw new RuntimeException(ex);
        }

    }

    public  History selectHistoryByQuestion(long id) throws PersistenceException,IllegalStateException {//todo verificar
        EntityManager em = JPAUtil.getCon();
        TypedQuery<History> q = em.createQuery(
                "SELECT q from History q where q.fkQuestion.idQuestion=:id",
                History.class
        );
        q.setParameter("id",id);
        var history =q.getSingleResult();

        return history;
    }

    public void addHistory(Question question, Alternative answer){
        History h = new History(answer,question);
        EntityManager em = JPAUtil.getCon();
        em.getTransaction().begin();
        em.persist(h);
        em.getTransaction().commit();

    }

    public List<History> selectHistories(){
        EntityManager em = JPAUtil.getCon();
        TypedQuery<History> q = em.createQuery("SELECT h from History h", History.class);
        List<History> histories = q.getResultList();

        return histories;
    }
}