package view;

import data.DAO.AlternativeDAO;
import data.DAO.HistoryDAO;
import data.DAO.QuestionDAO;
import data.models.Alternative;
import data.models.History;
import data.models.Question;
import jakarta.persistence.PersistenceException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class Home extends JFrame {

    private JPanel MainPanel;
    private JPanel topMenu;
    private JPanel ContentPanel;
    private JButton resolverQuestoesButton;
    private JButton bancoDeQuestoesButton;
    private JButton historicoButton;
    private JPanel QuestoesPanel;
    private JRadioButton altA;
    private JRadioButton altB;
    private JPanel AlternativesPanel;
    private JTextPane baseTextLabel;
    private JLabel sentenceLabel;
    private JRadioButton altC;
    private JRadioButton altD;
    private JRadioButton altE;
    private JButton confirmButton;
    private JButton editarQuestao;
    private JTable bancoTable;
    private JScrollPane ScrollPaneBanco;
    private JPanel tabelaPanel;
    private JPanel FiltroPanel;
    private JPanel BancoPanel;
    private JPanel HistoricoPanel;
    private JPanel hFiltroPanel;
    private JPanel hTabelaPanel;
    private JTable historicoTable;
    private JButton editarHistorico;
    private JScrollPane ScrollPaneHistorico;
    private ButtonGroup altGroup;
    private Optional<QuestionDAO> qDao = Optional.empty();
    private List<Question> list;
    private Question currentQuestion;
    private List<Alternative> currentAlternatives;
    private CardLayout cl;

    Home(String menu) {
        $$$setupUI$$$();
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        ActionListener listener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmButton.setEnabled(true);
            }
        };
        altA.addActionListener(listener);
        altB.addActionListener(listener);
        altC.addActionListener(listener);
        altD.addActionListener(listener);
        altE.addActionListener(listener);

        ActionListener deleteRowHistory = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                var hDao = new HistoryDAO();
                var str = editarHistorico.getActionCommand();
                var formar = Long.parseLong(str);
                hDao.deleteHistory(formar);
                historyFill();
                bancoFill();
                list = setQuestoesPanel(list, randomizer(list));
            }
        };
        ActionListener deleteRowBanco = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                var qDao = new QuestionDAO();
                var str = editarQuestao.getActionCommand();
                var formar = Long.parseLong(str);
                qDao.deleteQuestion(formar);
                bancoFill();
                historyFill();
                list = setQuestoesPanel(list, randomizer(list));
            }
        };

        editarHistorico.addActionListener(deleteRowHistory);
        editarQuestao.addActionListener(deleteRowBanco);

        confirmButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //todo: preencher botao de confirmaçao
                confirmButton.setEnabled(false);
                var hDao = new HistoryDAO();

                hDao.addHistory(currentQuestion, getSelection());
                list = setQuestoesPanel(list, randomizer(list));
                var btn = altGroup.getSelection();
                if (btn != null) btn.setSelected(false);
                historyFill();
            }
        });

        add(MainPanel);
        setVisible(true);
        ActionListener listener1 = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cl.show(ContentPanel, e.getActionCommand());
            }
        };
        resolverQuestoesButton.addActionListener(listener1);
        historicoButton.addActionListener(listener1);
        bancoDeQuestoesButton.addActionListener(listener1);

        historicoTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = historicoTable.rowAtPoint(e.getPoint());

                if (row != -1) {
                    long id = (long) historicoTable.getValueAt(row, 0);
                    var hDao = new HistoryDAO();
                    hDao.selectHistoryByQuestion(id);
                    editarHistorico.setEnabled(true);
                    editarHistorico.setActionCommand(String.valueOf(id));
                }
            }
        });
        bancoTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = bancoTable.rowAtPoint(e.getPoint());

                if (row != -1) {
                    long id = (long) bancoTable.getValueAt(row, 0);
                    var qDao = new QuestionDAO();
                    qDao.selectQuestionById(id);
                    editarQuestao.setEnabled(true);
                    editarQuestao.setActionCommand(String.valueOf(id));
                }
            }
        });
        cl = (CardLayout) (ContentPanel.getLayout());
        cl.show(ContentPanel, menu);
        list = setQuestoesPanel();
    }

    private Alternative getSelection() {
        var selection = altGroup.getSelection();
        if (selection == null) {
            throw new IllegalStateException("Nenhuma alternativa selecionada");
        }
        for (Alternative a : currentAlternatives) {
            if (selection.getActionCommand().equals(String.valueOf(a.getIdAlternative()))) return a;
        }
        throw new PersistenceException("Alternativa não encontrada");
    }

    private int randomizer(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Você respondeu a todas as questões adicionadas.",
                    "Parabéns!",
                    JOptionPane.INFORMATION_MESSAGE
            );
            setQuestoesPanel();
        }
        return new Random().nextInt(questions.size());
    }

    private List<Question> setQuestoesPanel() {
        return setQuestoesPanel(null, 0);
    }

    private List<Question> setQuestoesPanel(List<Question> qList, int rand) {
        qDao = qDao.or(() -> Optional.of(new QuestionDAO()));
        var questionDao = qDao.get();
        if (qList == null) qList = (questionDao.selectNewQuestions());
        if (qList.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Sem questões não respondidas." +
                            "\nAdicione novas questões e continue respondendo.",
                    "Atenção!",
                    JOptionPane.ERROR_MESSAGE);
            cl.show(ContentPanel, "historico");
            return null;
        }
        var aDao = new AlternativeDAO();

        //alternative
        sentenceLabel.setText(qList.get(rand).getStatement());
        baseTextLabel.setText(qList.get(rand).getBaseText());
        currentAlternatives = aDao.selectAlternatives(qList.get(rand));

        if (currentAlternatives.isEmpty()) {
            throw new IllegalStateException(
                    "Questão ID " + qList.get(rand).getIdQuestion() + " não possui alternativas!"
            );
        }
        var btns = altGroup.getElements();
        int index = 0;
        while (btns.hasMoreElements()) {
            var btn = btns.nextElement();
            if (index < currentAlternatives.size()) {
                var alt = currentAlternatives.get(index);
                btn.setText(alt.getStatement());
                btn.setActionCommand(String.valueOf(alt.getIdAlternative()));
                btn.setEnabled(true);
            } else {
                btn.setText("");
                btn.setActionCommand("");
                btn.setEnabled(false);
            }
            index++;
        }
        currentQuestion = qList.get(rand);
        qList.remove(rand);
        return qList;

    }

    private void createUIComponents() {
        if (ScrollPaneBanco == null) ScrollPaneBanco = new JScrollPane();
        if (ScrollPaneHistorico == null) ScrollPaneHistorico = new JScrollPane();
        bancoTable = new JTable();
        bancoFill();
        historicoTable = new JTable();
        historyFill();

    }

    private void historyFill() {
        var model = new DefaultTableModel(
                new Object[]{"Id", "Questão", "Sua Resposta", "Resultado"}, 0
        );
        historicoTable.setModel(model);
        ScrollPaneHistorico.setViewportView(historicoTable);
        var hDao = new HistoryDAO();
        for (History h : hDao.selectHistories()) {
            String res;
            if (h.getFkAnswered().isAnswer()) {res = "Correta";} else {res = "Errada";}

            Object[] row = new Object[]{
                    h.getFkQuestion().getIdQuestion(),
                    h.getFkQuestion().getStatement(),
                    h.getFkAnswered().getStatement(),
                    res,
            };
            model.addRow(row);
        }
    }

    private void bancoFill() {
        var model = new DefaultTableModel(
                new Object[]{"Id Questão", "Texto Base", "Enunciado"}, 0
        );
        bancoTable.setModel(model);
        ScrollPaneBanco.setViewportView(bancoTable);
        var qDao = new QuestionDAO();
        for (Question q : qDao.selectAllQuestions()) {

            Object[] row = new Object[]{
                    q.getIdQuestion(),
                    q.getBaseText(),
                    q.getStatement()
            };
            model.addRow(row);
        }
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        MainPanel = new JPanel();
        MainPanel.setLayout(new BorderLayout(0, 0));
        topMenu = new JPanel();
        topMenu.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        MainPanel.add(topMenu, BorderLayout.NORTH);
        resolverQuestoesButton = new JButton();
        resolverQuestoesButton.setActionCommand("questao");
        resolverQuestoesButton.setText("Resolver Questões");
        topMenu.add(resolverQuestoesButton);
        historicoButton = new JButton();
        historicoButton.setActionCommand("historico");
        historicoButton.setText("Histórico");
        topMenu.add(historicoButton);
        bancoDeQuestoesButton = new JButton();
        bancoDeQuestoesButton.setActionCommand("banco");
        bancoDeQuestoesButton.setHorizontalAlignment(0);
        bancoDeQuestoesButton.setText("Banco de Questões");
        topMenu.add(bancoDeQuestoesButton);
        ContentPanel = new JPanel();
        ContentPanel.setLayout(new CardLayout(0, 0));
        MainPanel.add(ContentPanel, BorderLayout.CENTER);
        QuestoesPanel = new JPanel();
        QuestoesPanel.setLayout(new BorderLayout(0, 0));
        QuestoesPanel.setVisible(false);
        ContentPanel.add(QuestoesPanel, "questao");
        sentenceLabel = new JLabel();
        sentenceLabel.setPreferredSize(new Dimension(36, 38));
        sentenceLabel.setText("");
        QuestoesPanel.add(sentenceLabel, BorderLayout.CENTER);
        AlternativesPanel = new JPanel();
        AlternativesPanel.setLayout(new GridBagLayout());
        QuestoesPanel.add(AlternativesPanel, BorderLayout.SOUTH);
        altA = new JRadioButton();
        altA.setText("");
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        AlternativesPanel.add(altA, gbc);
        altB = new JRadioButton();
        altB.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        AlternativesPanel.add(altB, gbc);
        altC = new JRadioButton();
        altC.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        AlternativesPanel.add(altC, gbc);
        altD = new JRadioButton();
        altD.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        AlternativesPanel.add(altD, gbc);
        altE = new JRadioButton();
        altE.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        AlternativesPanel.add(altE, gbc);
        confirmButton = new JButton();
        confirmButton.setEnabled(false);
        confirmButton.setText("Confirmar");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(10, 10, 10, 10);
        AlternativesPanel.add(confirmButton, gbc);
        baseTextLabel = new JTextPane();
        QuestoesPanel.add(baseTextLabel, BorderLayout.NORTH);
        BancoPanel = new JPanel();
        BancoPanel.setLayout(new BorderLayout(0, 0));
        ContentPanel.add(BancoPanel, "banco");
        FiltroPanel = new JPanel();
        FiltroPanel.setLayout(new GridBagLayout());
        BancoPanel.add(FiltroPanel, BorderLayout.NORTH);
        final JLabel label1 = new JLabel();
        label1.setText("Editar Questões");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 10, 10, 0);
        FiltroPanel.add(label1, gbc);
        tabelaPanel = new JPanel();
        tabelaPanel.setLayout(new BorderLayout(0, 0));
        BancoPanel.add(tabelaPanel, BorderLayout.CENTER);
        editarQuestao = new JButton();
        editarQuestao.setActionCommand("0");
        editarQuestao.setEnabled(false);
        editarQuestao.setLabel("Excluir Questão");
        editarQuestao.setSelected(false);
        editarQuestao.setText("Excluir Questão");
        editarQuestao.setVisible(true);
        tabelaPanel.add(editarQuestao, BorderLayout.SOUTH);
        ScrollPaneBanco = new JScrollPane();
        tabelaPanel.add(ScrollPaneBanco, BorderLayout.CENTER);
        bancoTable.setAutoCreateRowSorter(true);
        bancoTable.setAutoResizeMode(2);
        ScrollPaneBanco.setViewportView(bancoTable);
        HistoricoPanel = new JPanel();
        HistoricoPanel.setLayout(new BorderLayout(0, 0));
        ContentPanel.add(HistoricoPanel, "historico");
        hFiltroPanel = new JPanel();
        hFiltroPanel.setLayout(new GridBagLayout());
        HistoricoPanel.add(hFiltroPanel, BorderLayout.NORTH);
        final JLabel label2 = new JLabel();
        label2.setText("Histórico");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 10, 10, 0);
        hFiltroPanel.add(label2, gbc);
        hTabelaPanel = new JPanel();
        hTabelaPanel.setLayout(new BorderLayout(0, 0));
        HistoricoPanel.add(hTabelaPanel, BorderLayout.CENTER);
        editarHistorico = new JButton();
        editarHistorico.setActionCommand("0");
        editarHistorico.setEnabled(false);
        editarHistorico.setLabel("Excluir Histórico");
        editarHistorico.setText("Excluir Histórico");
        editarHistorico.setVisible(true);
        hTabelaPanel.add(editarHistorico, BorderLayout.SOUTH);
        ScrollPaneHistorico = new JScrollPane();
        hTabelaPanel.add(ScrollPaneHistorico, BorderLayout.CENTER);
        historicoTable.setAutoCreateRowSorter(true);
        historicoTable.setAutoResizeMode(2);
        ScrollPaneHistorico.setViewportView(historicoTable);
        altGroup = new ButtonGroup();
        altGroup.add(altA);
        altGroup.add(altB);
        altGroup.add(altC);
        altGroup.add(altD);
        altGroup.add(altE);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {return MainPanel;}
}
