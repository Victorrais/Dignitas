package data.DAO;

import data.models.Alternative;
import data.models.History;
import data.models.JPAUtil;
import data.models.Question;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.List;

public class HistoryDAO {

    public void addHistory(Question question, Alternative answer){
        History h = new History(answer,question);
        EntityManager em = JPAUtil.getCon();
        em.getTransaction().begin();
        em.persist(h);
        em.getTransaction().commit();
        JPAUtil.closeCon();

    }

    public List<History> selectHistories(){
        EntityManager em = JPAUtil.getCon();
        Query q = em.createQuery("SELECT h from History h");
        List<History> histories = q.getResultList();
        JPAUtil.closeCon();
        return histories;
    }


}
