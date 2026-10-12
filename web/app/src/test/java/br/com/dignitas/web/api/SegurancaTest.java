package br.com.dignitas.web.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Login e proteção de acesso: necessários para publicar a aplicação na internet. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaTest {

    @Autowired
    MockMvc mvc;

    @Test
    void paginaDeLoginEPublica() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/css/base.css")).andExpect(status().isOk());
    }

    @Test
    void apiSemLoginRetorna401() throws Exception {
        mvc.perform(get("/api/questoes")).andExpect(status().isUnauthorized());
    }

    @Test
    void paginaSemLoginRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login.html"));
    }

    @Test
    void loginComSenhaCorretaEntraNoSistema() throws Exception {
        mvc.perform(formLogin("/login").user("teste").password("senha-de-teste"))
                .andExpect(authenticated().withUsername("teste"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginComSenhaErradaVoltaComErro() throws Exception {
        mvc.perform(formLogin("/login").user("teste").password("errada"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login.html?erro"));
    }

    @Test
    void alteracaoSemTokenCsrfERecusada() throws Exception {
        mvc.perform(post("/api/questoes").with(user("teste"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sairEncerraASessao() throws Exception {
        mvc.perform(post("/logout").with(user("teste")).with(csrf()))
                .andExpect(redirectedUrl("/login.html?saiu"));
    }
}
