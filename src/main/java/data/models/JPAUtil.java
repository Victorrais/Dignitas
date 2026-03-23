package data.models;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.Optional;

public abstract class JPAUtil {
    private static final String PERSISTENCE = "hibernate";
    private static Optional<EntityManagerFactory> fem = Optional.empty();
    private static Optional<EntityManager> em = Optional.empty();

    //TODO: verificar funcionamento do jpa
    public static EntityManager getCon(){
        fem=fem.filter(EntityManagerFactory::isOpen)
                .or(()-> Optional.of(Persistence.createEntityManagerFactory(PERSISTENCE)));
        em=em.filter(EntityManager::isOpen)
                .or(()-> fem.map(EntityManagerFactory::createEntityManager));
        return em.orElseThrow();
    }

    public static void closeCon(){
        em.ifPresent(e->{if(e.isOpen())e.close();});
        fem.ifPresent(e->{if(e.isOpen())e.close();});
    }
}