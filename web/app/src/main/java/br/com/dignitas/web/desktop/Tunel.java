package br.com.dignitas.web.desktop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publica a aplicação local na internet com um túnel rápido do Cloudflare (cloudflared).
 * Não exige conta: o endereço https://....trycloudflare.com muda a cada publicação e
 * deixa de funcionar quando o túnel é encerrado ou o computador desliga.
 */
public final class Tunel {

    private static final Pattern ENDERECO = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    private Process processo;
    private volatile String endereco;

    /** Procura o cloudflared no PATH e nos locais em que o instalador e o winget o colocam. */
    public static Optional<Path> localizarCloudflared() {
        List<Path> candidatos = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) candidatos.add(Path.of(dir.trim(), "cloudflared.exe"));
            }
        }
        candidatos.add(Path.of("C:\\Program Files (x86)\\cloudflared\\cloudflared.exe"));
        candidatos.add(Path.of("C:\\Program Files\\cloudflared\\cloudflared.exe"));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) candidatos.add(Path.of(local, "Microsoft", "WinGet", "Links", "cloudflared.exe"));
        candidatos.add(ConfiguracaoLocal.PASTA.resolve("cloudflared.exe"));
        return candidatos.stream().filter(Files::isRegularFile).findFirst();
    }

    public boolean ativo() {
        return processo != null && processo.isAlive();
    }

    public String endereco() {
        return endereco;
    }

    /**
     * Inicia o túnel para a porta local e devolve o endereço público assim que o cloudflared o informa.
     *
     * @param aoEncerrar chamado se o túnel cair depois de publicado
     */
    public synchronized CompletableFuture<String> iniciar(Path cloudflared, int porta, Consumer<String> aoEncerrar)
            throws IOException {
        parar();
        endereco = null;
        // 127.0.0.1 e não localhost: o servidor só escuta em IPv4 local, e localhost pode resolver para ::1
        processo = new ProcessBuilder(cloudflared.toString(), "tunnel", "--no-autoupdate",
                "--url", "http://127.0.0.1:" + porta)
                .redirectErrorStream(true)
                .start();
        CompletableFuture<String> resultado = new CompletableFuture<>();
        Process p = processo;
        Thread leitor = new Thread(() -> {
            StringBuilder saida = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = in.readLine()) != null) {
                    if (saida.length() < 8000) saida.append(linha).append('\n');
                    Matcher m = ENDERECO.matcher(linha);
                    if (endereco == null && m.find()) {
                        endereco = m.group();
                        resultado.complete(endereco);
                    }
                }
            } catch (IOException ignorada) {
                // processo encerrado
            }
            if (!resultado.isDone()) {
                resultado.completeExceptionally(new IOException("O cloudflared encerrou sem gerar o endereço.\n"
                        + ultimasLinhas(saida.toString())));
            } else if (aoEncerrar != null && p == processo) {
                aoEncerrar.accept(endereco);
            }
        }, "dignitas-tunel");
        leitor.setDaemon(true);
        leitor.start();
        return resultado.orTimeout(60, TimeUnit.SECONDS);
    }

    public synchronized void parar() {
        if (processo != null) {
            Process p = processo;
            processo = null;
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }
        endereco = null;
    }

    private static String ultimasLinhas(String texto) {
        String[] linhas = texto.strip().split("\n");
        int inicio = Math.max(0, linhas.length - 5);
        return String.join("\n", java.util.Arrays.copyOfRange(linhas, inicio, linhas.length));
    }
}
