package br.com.dignitas.persistence;

import br.com.dignitas.app.AppConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Acesso ao JPA/Hibernate. Cada operação abre um {@link EntityManager} curto,
 * o que evita estado compartilhado entre telas e threads.
 */
public final class Jpa {
    public static final String UNIT = "dignitas";

    private static volatile EntityManagerFactory emf;

    private Jpa() {
    }

    /** Conecta ao banco configurado: o local (H2 embutido) ou o MySQL. */
    public static synchronized void init(AppConfig config) {
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.driver", config.jdbcDriver());
        props.put("jakarta.persistence.jdbc.url", config.connectionUrl());
        props.put("jakarta.persistence.jdbc.user", config.connectionUser());
        props.put("jakarta.persistence.jdbc.password", config.connectionPassword());
        init(props);
    }

    /** Inicializa com propriedades arbitrárias (usado também pelos testes com H2). */
    public static synchronized void init(Map<String, Object> props) {
        shutdown();
        emf = Persistence.createEntityManagerFactory(UNIT, props);
    }

    public static boolean isReady() {
        EntityManagerFactory f = emf;
        return f != null && f.isOpen();
    }

    public static synchronized void shutdown() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        emf = null;
    }

    private static EntityManagerFactory factory() {
        EntityManagerFactory f = emf;
        if (f == null || !f.isOpen()) {
            throw new IllegalStateException("Banco de dados não conectado. Verifique as Configurações.");
        }
        return f;
    }

    /** Executa uma leitura (sem transação explícita). */
    public static <T> T read(Function<EntityManager, T> work) {
        try (EntityManager em = factory().createEntityManager()) {
            return work.apply(em);
        }
    }

    /** Executa {@code work} dentro de uma transação, com rollback em caso de erro. */
    public static <T> T tx(Function<EntityManager, T> work) {
        try (EntityManager em = factory().createEntityManager()) {
            EntityTransaction t = em.getTransaction();
            t.begin();
            try {
                T result = work.apply(em);
                t.commit();
                return result;
            } catch (RuntimeException e) {
                if (t.isActive()) t.rollback();
                throw e;
            }
        }
    }

    public static void txVoid(Consumer<EntityManager> work) {
        tx(em -> {
            work.accept(em);
            return null;
        });
    }
}
