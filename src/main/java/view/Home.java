package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Home extends JFrame {

    private JPanel MainPanel;
    private JPanel topMenu;
    private JPanel ContentPanel;
    private JButton resolverQuestoesButton;
    private JButton bancoDeQuestoesButton;
    private JButton historicoButton;
    private JPanel QuestoesPanel;
    private JPanel HistoricoPanel;
    private JRadioButton altA;
    private JRadioButton altB;
    private JPanel AlternativesPanel;
    private JTextPane baseTextLabel;
    private JLabel sentenceLabel;
    private JRadioButton altC;
    private JRadioButton altD;
    private JRadioButton altE;
    private JButton confirmButton;
    private JPanel rodape;
    private JPanel titulo;
    private JPanel historico;
    private JTextField textField1;
    private JButton button1;
    private JLabel pageCount;
    private JPanel Titulos;
    private JTextArea textArea1;
    private JButton button2;
    private JButton button3;
    private JButton button4;
    private JButton editarButton;
    private JTable bancoTable;
    private JScrollPane ScrollPane;
    private JPanel tabelaPanel;
    private JPanel FiltroPanel;
    private JPanel paginacaoPanel;
    private JPanel BancoPanel;

    public Home() {
        setSize(500,400);
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

        confirmButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //todo: preencher botao de confirmaçao
                confirmButton.setEnabled(false);
            }
        });

        add(MainPanel);
        setVisible(true);
        ActionListener listener1 = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println(e.getActionCommand());
                CardLayout cl = (CardLayout)(ContentPanel.getLayout());
                System.out.println();
                if(e.getActionCommand().equals(resolverQuestoesButton.getActionCommand())){
                    cl.show(ContentPanel, "Card1");
                } else if (e.getActionCommand().equals(historicoButton.getActionCommand())) {
                    cl.show(ContentPanel, "Card2");
                } else if (e.getActionCommand().equals(bancoDeQuestoesButton.getActionCommand())) {
                    cl.show(ContentPanel, "Card3");
                }

            }
        };
        resolverQuestoesButton.addActionListener(listener1);
        historicoButton.addActionListener(listener1);
        bancoDeQuestoesButton.addActionListener(listener1);

        bancoTable.addMouseListener(new MouseAdapter() {
            //todo: verificar se o mouse listener é o suficiente
            //para ativar o botao de Update do CRUD. Caso não, utilize outro listener
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                editarButton.setEnabled(true);
                editarButton.setVisible(true);
                if(bancoTable.getSelectedRow()>-1) {
                    editarButton.setEnabled(true);
                    editarButton.setVisible(true);
                }
            }
        });
    }

    static void main(String[] args) {
        new Home().setVisible(true);
    }

    public void itemStateChanged(ItemEvent evt) {
        CardLayout cl = (CardLayout)(ContentPanel.getLayout());
        cl.show(ContentPanel, (String)evt.getItem());
    }

    private void createUIComponents() {
        if(bancoTable==null)bancoTable=new JTable();
        bancoTable.setModel(new javax.swing.table.DefaultTableModel(
                new Object[][]{

                },
                new String []{
                    "Questão","Categoria","Última modificação"
                }
        ));
        if(ScrollPane==null) ScrollPane=new JScrollPane();
        ScrollPane.setViewportView(bancoTable);
        if (bancoTable.getColumnModel().getColumnCount() > 0) {
            bancoTable.getColumnModel().getColumn(0).setHeaderValue("Questão");
            bancoTable.getColumnModel().getColumn(1).setHeaderValue("Categoria");
            bancoTable.getColumnModel().getColumn(2).setHeaderValue("Última modificação");
        }
    }
}
