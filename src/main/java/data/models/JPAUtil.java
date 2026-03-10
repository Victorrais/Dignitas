package data.models;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public abstract class JPAUtil {
    private static final String PERSISTENCE = "hibernate";
    private static EntityManagerFactory fem;
    private static EntityManager em;

    //TODO: verificar funcionamento do jpa
    public static EntityManager getCon(){
        if(!fem.isOpen()||fem==null){
            fem = Persistence.createEntityManagerFactory(PERSISTENCE);
        }
        if (!em.isOpen()||em==null){
            em = fem.createEntityManager();
        }
        return em;
    }

    public static void closeCon(){
        if(fem.isOpen()||em.isOpen()){
            em.close();
            fem.close();
        }
    }
}
