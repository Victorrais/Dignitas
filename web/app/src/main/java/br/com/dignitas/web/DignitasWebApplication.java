package br.com.dignitas.web;

import br.com.dignitas.web.desktop.ConfiguracaoLocal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.Desktop;
import java.net.URI;

/**
 * Ponto de entrada da aplicação web. O front-end estático é servido de src/main/resources/static.
 * Com -Ddignitas.desktop=true (usado pelo Dignitas.exe) roda como programa de desktop:
 * prepara a configuração local, grava o log em arquivo, abre o navegador e mostra o ícone na bandeja.
 */
@SpringBootApplication
public class DignitasWebApplication {

    public static void main(String[] args) {
        boolean desktop = Boolean.getBoolean("dignitas.desktop");
        SpringApplication app = new SpringApplication(DignitasWebApplication.class);
        if (desktop) {
            if (ConfiguracaoLocal.jaEmExecucao()) {
                abrirNoNavegador();
                return;
            }
            System.setProperty("logging.file.name", ConfiguracaoLocal.LOG.toString());
            app.setHeadless(false);
            ConfiguracaoLocal.preparar();
        }
        try {
            app.run(args);
        } catch (RuntimeException e) {
            if (!desktop) throw e;
            ConfiguracaoLocal.falhaAoIniciar(e);
            System.exit(1);
        }
    }

    private static void abrirNoNavegador() {
        try {
            Desktop.getDesktop().browse(URI.create("http://localhost:" + ConfiguracaoLocal.PORTA_PADRAO + "/"));
        } catch (Exception ignorada) {
            // Sem navegador disponível: nada a fazer
        }
    }
}
