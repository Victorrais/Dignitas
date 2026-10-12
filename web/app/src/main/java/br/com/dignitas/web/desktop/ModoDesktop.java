package br.com.dignitas.web.desktop;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Modo executável (Dignitas.exe): depois que o servidor sobe, abre o navegador e mostra um ícone
 * na bandeja do Windows com Abrir, Publicar na internet (túnel) e Sair.
 * Ativo só com -Ddignitas.desktop=true, que o executável passa.
 */
@Component
@ConditionalOnProperty(name = "dignitas.desktop", havingValue = "true")
public class ModoDesktop implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ModoDesktop.class);

    private final Tunel tunel = new Tunel();
    private ConfigurableApplicationContext contexto;
    private TrayIcon icone;
    private MenuItem itemPublicar;
    private MenuItem itemCopiar;
    private int porta;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent evento) {
        contexto = evento.getApplicationContext();
        porta = Integer.parseInt(contexto.getEnvironment().getProperty("local.server.port", "8080"));
        abrirNavegador();
        SwingUtilities.invokeLater(this::criarIcone);
    }

    private String enderecoLocal() {
        return "http://localhost:" + porta + "/";
    }

    private void abrirNavegador() {
        try {
            Desktop.getDesktop().browse(URI.create(enderecoLocal()));
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Não foi possível abrir o navegador: {}", e.getMessage());
        }
    }

    private void criarIcone() {
        if (!SystemTray.isSupported()) {
            log.warn("Bandeja do sistema indisponível; feche o Dignitas pelo Gerenciador de Tarefas.");
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem abrir = new MenuItem("Abrir o Dignitas");
        abrir.addActionListener(e -> abrirNavegador());
        itemPublicar = new MenuItem("Publicar na internet");
        itemPublicar.addActionListener(e -> alternarPublicacao());
        itemCopiar = new MenuItem("Copiar endereço público");
        itemCopiar.setEnabled(false);
        itemCopiar.addActionListener(e -> copiar(tunel.endereco()));
        MenuItem configuracao = new MenuItem("Abrir configuração");
        configuracao.addActionListener(e -> ConfiguracaoLocal.abrirArquivo(ConfiguracaoLocal.ARQUIVO));
        MenuItem sair = new MenuItem("Sair");
        sair.addActionListener(e -> sair());
        menu.add(abrir);
        menu.addSeparator();
        menu.add(itemPublicar);
        menu.add(itemCopiar);
        menu.addSeparator();
        menu.add(configuracao);
        menu.add(sair);

        icone = new TrayIcon(imagemIcone(), "Dignitas · " + enderecoLocal(), menu);
        icone.setImageAutoSize(true);
        icone.addActionListener(e -> abrirNavegador()); // clique duplo
        try {
            SystemTray.getSystemTray().add(icone);
            icone.displayMessage("Dignitas", "Rodando em " + enderecoLocal(), TrayIcon.MessageType.INFO);
        } catch (AWTException e) {
            log.warn("Não foi possível criar o ícone na bandeja: {}", e.getMessage());
        }
    }

    private Image imagemIcone() {
        try (InputStream in = getClass().getResourceAsStream("/static/img/favicon.png")) {
            if (in != null) return ImageIO.read(in);
        } catch (IOException ignorada) {
            // usa o ícone vazio abaixo
        }
        return Toolkit.getDefaultToolkit().createImage(new byte[0]);
    }

    // ---------- Publicação (túnel) ----------

    private void alternarPublicacao() {
        if (tunel.ativo()) {
            tunel.parar();
            atualizarMenuPublicacao(false);
            icone.displayMessage("Dignitas", "Publicação encerrada. O endereço público deixou de funcionar.",
                    TrayIcon.MessageType.INFO);
            return;
        }
        Optional<Path> cloudflared = Tunel.localizarCloudflared();
        if (cloudflared.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Para publicar na internet, instale o cloudflared (gratuito, da Cloudflare).\n\n"
                            + "No Prompt de Comando, execute:\n"
                            + "   winget install --id Cloudflare.cloudflared\n\n"
                            + "Depois feche e abra o Dignitas novamente.",
                    "Dignitas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        itemPublicar.setEnabled(false);
        itemPublicar.setLabel("Publicando…");
        try {
            tunel.iniciar(cloudflared.get(), porta, this::tunelCaiu).whenComplete((url, erro) ->
                    SwingUtilities.invokeLater(() -> {
                        itemPublicar.setEnabled(true);
                        if (erro != null) {
                            atualizarMenuPublicacao(false);
                            tunel.parar();
                            JOptionPane.showMessageDialog(null, "Não foi possível publicar:\n" + erro.getMessage(),
                                    "Dignitas", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        atualizarMenuPublicacao(true);
                        copiar(url);
                        JOptionPane.showMessageDialog(null,
                                "O Dignitas está na internet em:\n\n" + url + "\n\n"
                                        + "O endereço foi copiado. Ele funciona enquanto o Dignitas estiver aberto\n"
                                        + "e muda a cada publicação. Quem acessar precisa do login.",
                                "Dignitas", JOptionPane.INFORMATION_MESSAGE);
                    }));
        } catch (IOException e) {
            itemPublicar.setEnabled(true);
            atualizarMenuPublicacao(false);
            JOptionPane.showMessageDialog(null, "Não foi possível iniciar o cloudflared:\n" + e.getMessage(),
                    "Dignitas", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void tunelCaiu(String url) {
        SwingUtilities.invokeLater(() -> {
            atualizarMenuPublicacao(false);
            if (icone != null) {
                icone.displayMessage("Dignitas", "A publicação foi interrompida (" + url + ").",
                        TrayIcon.MessageType.WARNING);
            }
        });
    }

    private void atualizarMenuPublicacao(boolean publicado) {
        itemPublicar.setLabel(publicado ? "Parar publicação" : "Publicar na internet");
        itemCopiar.setEnabled(publicado);
        if (icone != null) {
            icone.setToolTip(publicado ? "Dignitas · publicado em " + tunel.endereco() : "Dignitas · " + enderecoLocal());
        }
    }

    private static void copiar(String texto) {
        if (texto == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(texto), null);
    }

    // ---------- Encerramento ----------

    private void sair() {
        tunel.parar();
        if (icone != null) SystemTray.getSystemTray().remove(icone);
        int codigo = SpringApplication.exit(contexto, () -> 0);
        System.exit(codigo);
    }

    @PreDestroy
    void encerrar() {
        tunel.parar();
    }
}
