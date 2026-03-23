package data.DAO;

import data.models.Alternative;
import data.models.JPAUtil;
import data.models.Question;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class AlternativeDAO {
    public Alternative selectAlternative(Alternative alternative){
        EntityManager em = JPAUtil.getCon();
        TypedQuery<Alternative> q = em.createQuery("SELECT a from Alternative a where a.idAlternative = :idAlternative",Alternative.class);
        q.setParameter("idAlternative", alternative);
        var alt=q.getSingleResult();

        return alt;
    }
    public List<Alternative> selectAlternatives( Question question){
        EntityManager em = JPAUtil.getCon();
        TypedQuery<Alternative> q = em.createQuery("SELECT a from Alternative a where a.question.idQuestion = :idQuestion",Alternative.class);
        /*
        Detalhe que esse stmt Question deve ser de uma questão recuperada SEMPRE.
        Questões a ser adicionadas sempre estarão sem id, graças ao auto_increment,
        portanto a ordem de recuperaçao deve ser sempre de Questao e, somente após o Select bem-sucedido,
        Alternativas, respetivamente.
        */
        q.setParameter("idQuestion", question.getIdQuestion());
        List<Alternative> alternatives = q.getResultList();
        return alternatives;
    }
    public void addAlternatives( List<Alternative> alternatives,  Question q){
        EntityManager em =JPAUtil.getCon();
        em.getTransaction().begin();
        for (Alternative a : alternatives){
            if (a.getIdQuestion().equals(q))em.persist(a);
        }
        em.getTransaction().commit();
    }
}