package data.DAO;
import data.models.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class AlternativeDAO {
    public List<Alternative> selectAlternatives( Question question){

        EntityManager em = JPAUtil.getCon();
        TypedQuery<Alternative> q = em.createQuery("SELECT a from Alternative where a.idQuestion == :idQuestion",Alternative.class);
        q.setParameter("idQuestion", question.getId());
        List<Alternative> alternatives = q.getResultList();
        JPAUtil.closeCon();
        return alternatives;
    }
    public void addAlternatives( List<Alternative> alternatives,  Question q){
        for (Alternative a : alternatives){
            a.setIdQuestion(q);
        }
        EntityManager em =JPAUtil.getCon();
        em.getTransaction().begin();
        em.persist(alternatives);
        em.getTransaction().commit();
        JPAUtil.closeCon();
    }
}
