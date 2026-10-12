package br.com.dignitas.web.desktop;

import javax.swing.JOptionPane;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Configuração do usuário no modo executável: um arquivo de propriedades em
 * {@code %USERPROFILE%\dignitas}, criado na primeira execução e lido pelo Spring
 * (spring.config.import). Senhas ficam só nesse arquivo, fora do programa.
 */
public final class ConfiguracaoLocal {

    public static final Path PASTA = Path.of(System.getProperty("user.home"), "dignitas");
    public static final Path ARQUIVO = PASTA.resolve("dignitas-web.properties");
    public static final Path LOG = PASTA.resolve("dignitas-web.log");
    public static final int PORTA_PADRAO = 8080;

    private ConfiguracaoLocal() {
    }

    /** Cria o arquivo de configuração na primeira execução e avisa o usuário do login gerado. */
    public static void preparar() {
        try {
            Files.createDirectories(PASTA);
            if (Files.exists(ARQUIVO)) return;
            String senhaLogin = senhaAleatoria();
            String conteudo = """
                    # Configuração do Dignitas Web. Este arquivo fica só neste computador.
                    # Em senhas com barra invertida (\\), escreva a barra duas vezes.

                    # Banco MySQL
                    dignitas.db.usuario=root
                    dignitas.db.senha=

                    # Login para entrar no Dignitas (também vale para o acesso pela internet)
                    dignitas.login.usuario=admin
                    dignitas.login.senha=%s
                    """.formatted(senhaLogin);
            Files.writeString(ARQUIVO, conteudo, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(null,
                    "Bem-vindo ao Dignitas!\n\n"
                            + "Criamos a sua configuração em:\n" + ARQUIVO + "\n\n"
                            + "Login para entrar no sistema:\n"
                            + "   usuário: admin\n"
                            + "   senha: " + senhaLogin + "\n\n"
                            + "Se o seu MySQL tem senha, informe-a nesse arquivo (dignitas.db.senha).",
                    "Dignitas", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Não foi possível criar " + ARQUIVO + ":\n" + e.getMessage(),
                    "Dignitas", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Se já há um Dignitas rodando nesta máquina, abre o navegador nele em vez de iniciar outro. */
    public static boolean jaEmExecucao() {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> r = c.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORTA_PADRAO + "/login.html"))
                            .timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 && r.body().contains("Dignitas");
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Explica a falha de inicialização em linguagem simples e oferece abrir a configuração. */
    public static void falhaAoIniciar(Throwable e) {
        String causa = mensagensEncadeadas(e);
        String explicacao;
        if (causa.contains("Access denied")) {
            explicacao = "O MySQL recusou o usuário ou a senha.\n"
                    + "Confira dignitas.db.usuario e dignitas.db.senha no arquivo de configuração.";
        } else if (causa.contains("Communications link failure") || causa.contains("Connection refused")) {
            explicacao = "Não foi possível conectar ao MySQL.\n"
                    + "Verifique se o serviço do MySQL está iniciado (net start mysql, como administrador).";
        } else if (causa.contains("already in use") || causa.contains("Address already in use")) {
            explicacao = "A porta " + PORTA_PADRAO + " já está em uso por outro programa.\n"
                    + "Feche esse programa ou defina outra porta com server.port no arquivo de configuração.";
        } else {
            explicacao = "Detalhes no arquivo de log:\n" + LOG;
        }
        Object[] opcoes = {"Abrir configuração", "Fechar"};
        int escolha = JOptionPane.showOptionDialog(null,
                "O Dignitas não conseguiu iniciar.\n\n" + explicacao,
                "Dignitas", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, opcoes, opcoes[1]);
        if (escolha == 0) abrirArquivo(ARQUIVO);
    }

    static void abrirArquivo(Path arquivo) {
        try {
            new ProcessBuilder("notepad.exe", arquivo.toString()).start();
        } catch (IOException e) {
            try {
                Desktop.getDesktop().open(arquivo.toFile());
            } catch (IOException | UnsupportedOperationException ignorada) {
                // Sem como abrir: o caminho já foi mostrado ao usuário
            }
        }
    }

    private static String mensagensEncadeadas(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) sb.append(t.getMessage()).append('\n');
        return sb.toString();
    }

    private static String senhaAleatoria() {
        byte[] b = new byte[9];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
