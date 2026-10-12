package br.com.dignitas.web.api;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Testes de API rodam como usuário autenticado e enviam o token CSRF em toda requisição. */
@TestConfiguration
class UsuarioDeTeste {

    @Bean
    MockMvcBuilderCustomizer usuarioAutenticado() {
        return builder -> builder.defaultRequest(get("/").with(user("teste")).with(csrf()));
    }
}
