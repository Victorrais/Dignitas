package br.com.dignitas.service;

import br.com.dignitas.domain.*;
import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.AlternativeDraft;
import br.com.dignitas.model.Texts;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.service.Dtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.*;

public class QuestionService {

    // ---------- Consultas ----------

    public PageResult<QuestionRow> search(QuestionFilter f, int page, int size) {
        return Jpa.read(em -> {
            Where w = where(f);
            long total = w.apply(em.createQuery(
                    "select count(q) from Question q left join q.exam e" + w.sql, Long.class)).getSingleResult();
            TypedQuery<Object[]> q = w.apply(em.createQuery(
                    "select q.id, q.number, q.statement, s.name, e.board, e.year, e.name, q.updatedAt, q.annulled, q.flagged "
                            + "from Question q left join q.subject s left join q.exam e" + w.sql + orderBy(f.sort()),
                    Object[].class));
            q.setFirstResult(page * size).setMaxResults(size);
            List<QuestionRow> rows = q.getResultList().stream().map(r -> new QuestionRow(
                    (Long) r[0], (Integer) r[1], (String) r[2], (String) r[3], (String) r[4], (Integer) r[5],
                    (String) r[6], (LocalDateTime) r[7], (Boolean) r[8], (Boolean) r[9])).toList();
            return new PageResult<>(rows, total, page, size);
        });
    }

    /** Ids das questões que atendem ao filtro (para sortear a próxima questão a resolver). */
    public List<Long> ids(QuestionFilter f) {
        return Jpa.read(em -> {
            Where w = where(f);
            return w.apply(em.createQuery("select q.id from Question q left join q.exam e" + w.sql, Long.class))
                    .getResultList();
        });
    }

    /** Carrega a questão com alternativas, texto-base, disciplina e concurso. */
    public Question loadFull(long id) {
        return Jpa.read(em -> {
            List<Question> list = em.createQuery(
                            "select distinct q from Question q left join fetch q.alternatives "
                                    + "left join fetch q.baseText left join fetch q.subject left join fetch q.exam "
                                    + "where q.id = :id", Question.class)
                    .setParameter("id", id).getResultList();
            if (list.isEmpty()) throw new NoSuchElementException("Questão " + id + " não encontrada");
            return list.getFirst();
        });
    }

    public long count() {
        return Jpa.read(em -> em.createQuery("select count(q) from Question q", Long.class).getSingleResult());
    }

    public List<Subject> subjects() {
        return Jpa.read(em -> em.createQuery("select s from Subject s order by s.name", Subject.class).getResultList());
    }

    public List<String> boards() {
        return Jpa.read(em -> em.createQuery(
                "select distinct e.board from Exam e where e.board is not null order by e.board", String.class).getResultList());
    }

    public List<Integer> years() {
        return Jpa.read(em -> em.createQuery(
                "select distinct e.year from Exam e where e.year is not null order by e.year desc", Integer.class).getResultList());
    }

    /** Dos hashes informados, retorna os que já existem no banco. */
    public Set<String> existingHashes(Collection<String> hashes) {
        if (hashes.isEmpty()) return Set.of();
        return Jpa.read(em -> new HashSet<>(em.createQuery(
                        "select q.contentHash from Question q where q.contentHash in :h", String.class)
                .setParameter("h", hashes).getResultList()));
    }

    // ---------- Edição ----------

    public QuestionDraft toDraft(Question q) {
        QuestionDraft d = new QuestionDraft();
        d.setId(q.getId());
        d.setNumber(q.getNumber());
        Exam e = q.getExam();
        if (e != null) d.setExam(new ExamInfo(e.getName(), e.getBoard(), e.getYear(), e.getRole()));
        d.setSubjectName(q.getSubject() == null ? null : q.getSubject().getName());
        BaseText bt = q.getBaseText();
        if (bt != null) {
            BaseTextDraft b = new BaseTextDraft(bt.getId(), bt.getContent(), bt.getSource());
            b.setImage(bt.getImage());
            d.setBaseText(b);
        }
        d.setStatement(q.getStatement());
        q.getAlternatives().forEach(a -> d.addAlternative(a.getLetter(), a.getStatement(), a.isAnswer()).setImage(a.getImage()));
        d.setAnnulled(q.isAnnulled());
        d.setTrueFalse(q.getAlternatives().size() == 2
                && "C".equals(q.getAlternatives().get(0).getLetter())
                && "E".equals(q.getAlternatives().get(1).getLetter()));
        d.setImage(q.getImage());
        return d;
    }

    /** Cria ou atualiza uma questão a partir do rascunho. Retorna o id. */
    public long save(QuestionDraft draft) {
        List<String> errors = draft.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return Jpa.tx(em -> {
            Question q = draft.getId() == null ? new Question() : em.find(Question.class, draft.getId());
            if (q == null) throw new NoSuchElementException("Questão não encontrada");
            String hash = draft.contentHash();
            Long clash = em.createQuery("select count(q) from Question q where q.contentHash = :h and q.id <> :id", Long.class)
                    .setParameter("h", hash).setParameter("id", q.getId() == null ? -1L : q.getId()).getSingleResult();
            if (clash > 0) throw new DuplicateQuestionException();
            q.setExam(draft.getExam() == null || draft.getExam().isEmpty() ? null : findOrCreateExam(em, draft.getExam()));
            apply(em, q, draft, hash, new IdentityHashMap<>());
            if (q.getId() == null) em.persist(q);
            em.flush();
            return q.getId();
        });
    }

    public void delete(long id) {
        Jpa.txVoid(em -> {
            Question q = em.find(Question.class, id);
            if (q != null) em.remove(q);
            deleteOrphans(em);
        });
    }

    public void setFlagged(long id, boolean flagged) {
        Jpa.txVoid(em -> em.createQuery("update Question q set q.flagged = :f where q.id = :id")
                .setParameter("f", flagged).setParameter("id", id).executeUpdate());
    }

    // ---------- Auxiliares compartilhados com ImportService ----------

    /** Copia o rascunho para a entidade (sem tratar concurso/importação). */
    static void apply(EntityManager em, Question q, QuestionDraft d, String hash,
                      Map<BaseTextDraft, BaseText> baseTexts) {
        q.setNumber(d.getNumber());
        q.setStatement(d.getStatement() == null ? "" : d.getStatement().strip());
        q.setContentHash(hash);
        q.setAnnulled(d.isAnnulled());
        q.setImage(d.getImage());
        q.setSubject(Texts.isBlank(d.getSubjectName()) ? null : findOrCreateSubject(em, d.getSubjectName().strip()));

        BaseTextDraft btd = d.getBaseText();
        if (btd == null || btd.isEmpty()) {
            q.setBaseText(null);
        } else {
            BaseText bt = baseTexts.get(btd);
            if (bt == null) {
                bt = btd.getId() != null ? em.find(BaseText.class, btd.getId()) : null;
                if (bt == null) {
                    bt = new BaseText();
                    copy(btd, bt);
                    em.persist(bt);
                } else {
                    copy(btd, bt);
                }
                baseTexts.put(btd, bt);
            }
            q.setBaseText(bt);
        }

        // Atualiza as alternativas no lugar para preservar os ids referenciados pelo histórico.
        List<Alternative> current = q.getAlternatives();
        List<AlternativeDraft> wanted = d.getAlternatives();
        for (int i = 0; i < wanted.size(); i++) {
            AlternativeDraft w = wanted.get(i);
            if (i < current.size()) {
                Alternative a = current.get(i);
                a.setLetter(w.getLetter());
                a.setStatement(nz(w.getText()));
                a.setAnswer(w.isCorrect());
                a.setImage(w.getImage());
            } else {
                Alternative a = new Alternative(w.getLetter(), nz(w.getText()), w.isCorrect());
                a.setImage(w.getImage());
                q.addAlternative(a);
            }
        }
        while (current.size() > wanted.size()) {
            current.removeLast();
        }
    }

    private static void copy(BaseTextDraft from, BaseText to) {
        to.setContent(nz(from.getContent()));
        to.setSource(Texts.blankToNull(from.getSource()));
        to.setImage(from.getImage());
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }

    static Subject findOrCreateSubject(EntityManager em, String name) {
        List<Subject> found = em.createQuery("select s from Subject s where lower(s.name) = lower(:n)", Subject.class)
                .setParameter("n", name).getResultList();
        if (!found.isEmpty()) return found.getFirst();
        Subject s = new Subject(name);
        em.persist(s);
        return s;
    }

    static Exam findOrCreateExam(EntityManager em, ExamInfo info) {
        String name = info.nameOrDefault();
        String board = Texts.blankToNull(info.board());
        String jpql = "select e from Exam e where e.name = :n"
                + (board == null ? " and e.board is null" : " and e.board = :b")
                + (info.year() == null ? " and e.year is null" : " and e.year = :y");
        TypedQuery<Exam> q = em.createQuery(jpql, Exam.class).setParameter("n", name);
        if (board != null) q.setParameter("b", board);
        if (info.year() != null) q.setParameter("y", info.year());
        List<Exam> found = q.getResultList();
        if (!found.isEmpty()) return found.getFirst();
        Exam e = new Exam(name, board, info.year(), Texts.blankToNull(info.role()));
        em.persist(e);
        return e;
    }

    /** Remove textos-base e concursos que ficaram sem uso. */
    static void deleteOrphans(EntityManager em) {
        em.flush();
        em.createQuery("delete from BaseText b where not exists (select 1 from Question q where q.baseText = b)")
                .executeUpdate();
        em.createQuery("delete from Exam e where not exists (select 1 from Question q where q.exam = e) "
                + "and not exists (select 1 from ImportBatch i where i.exam = e)").executeUpdate();
    }

    // ---------- Montagem do filtro ----------

    private static final class Where {
        final String sql;
        final Map<String, Object> params;

        Where(String sql, Map<String, Object> params) {
            this.sql = sql;
            this.params = params;
        }

        <T> TypedQuery<T> apply(TypedQuery<T> q) {
            params.forEach(q::setParameter);
            return q;
        }
    }

    private static Where where(QuestionFilter f) {
        List<String> c = new ArrayList<>();
        Map<String, Object> p = new HashMap<>();
        if (!Texts.isBlank(f.text())) {
            c.add("lower(q.statement) like :text");
            p.put("text", "%" + f.text().strip().toLowerCase() + "%");
        }
        if (f.subjectId() != null) {
            c.add("q.subject.id = :subject");
            p.put("subject", f.subjectId());
        }
        if (!Texts.isBlank(f.board())) {
            c.add("e.board = :board");
            p.put("board", f.board());
        }
        if (f.year() != null) {
            c.add("e.year = :year");
            p.put("year", f.year());
        }
        if (f.examId() != null) {
            c.add("e.id = :exam");
            p.put("exam", f.examId());
        }
        if (f.skipAnnulled()) c.add("q.annulled = false");
        if (f.status() != null) {
            switch (f.status()) {
                case UNANSWERED -> c.add("not exists (select 1 from Attempt a where a.question = q)");
                case WRONG -> c.add("exists (select 1 from Attempt a where a.question = q and a.correct = false)");
                case FLAGGED -> c.add("q.flagged = true");
                case ALL -> {
                }
            }
        }
        return new Where(c.isEmpty() ? "" : " where " + String.join(" and ", c), p);
    }

    private static String orderBy(Sort sort) {
        if (sort == null) sort = Sort.RECENT;
        return switch (sort) {
            case RECENT -> " order by q.updatedAt desc, q.id desc";
            case OLDEST -> " order by q.updatedAt asc, q.id asc";
            case NUMBER -> " order by e.name, q.number, q.id";
        };
    }
}
