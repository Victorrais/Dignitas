# Dignitas Web

Versão web do Dignitas: API REST em Spring Boot sobre as regras de negócio, banco MySQL (JPA/Hibernate),
front-end em HTML, CSS e JavaScript, e login.

## Módulos

| Módulo | Conteúdo |
|---|---|
| `core/` | Regras de negócio (sem interface nem Spring): domínio, serviços, persistência e extração de PDF |
| `app/` | Spring Boot: API REST, segurança, front-end estático (`app/src/main/resources/static`) e empacotamento (`app/packaging`) |
| `docs/` | Wireframes (HTML e PNG) e capturas dos protótipos das telas |

## Antes de começar

- **MySQL 8** iniciado. No Windows, como administrador: `net start mysql`.
- **Configuração local**: `%USERPROFILE%\dignitas\dignitas-web.properties`. O executável cria o arquivo na primeira execução.
  Ele guarda as senhas e fica fora do repositório:

  ```properties
  dignitas.db.usuario=root
  dignitas.db.senha=sua-senha-do-mysql
  dignitas.login.usuario=admin
  dignitas.login.senha=senha-para-entrar-no-sistema
  ```

  As mesmas opções podem vir de variáveis de ambiente (`DIGNITAS_DB_SENHA`, `DIGNITAS_LOGIN_SENHA` etc.).
  Sem senha de login configurada, uma senha aleatória é gerada a cada execução e aparece no log.

## Executável para Windows

Gere com:

```bash
empacotar.cmd
```

O resultado fica em `app\target\dist\`:

- `Dignitas\Dignitas.exe`: o programa, com Java embutido. Não precisa ter Java instalado.
- `Dignitas-Web-Windows.zip`: a mesma pasta compactada, para levar para outro computador.

Ao abrir o `Dignitas.exe`:

1. O servidor sobe e o navegador abre em http://localhost:8080.
2. Um ícone aparece na bandeja do Windows, com Abrir, Publicar na internet, Copiar endereço público, Abrir configuração e Sair.
3. Se algo impedir o início (MySQL parado, senha errada, porta ocupada), uma janela explica o problema.

O log fica em `%USERPROFILE%\dignitas\dignitas-web.log`.

## Pela linha de comando

```bash
mvnw.cmd -B -DskipTests install
```

```bash
mvnw.cmd -B -pl app spring-boot:run
```

Depois abra http://localhost:8080.

## Publicar na internet (túnel)

O túnel rápido do Cloudflare cria um endereço `https://....trycloudflare.com` que aponta para o Dignitas
rodando no seu computador. É gratuito e não exige conta, mas:

- funciona só enquanto o computador e o Dignitas estiverem ligados;
- o endereço muda a cada publicação;
- todo acesso passa pelo login.

Instale o `cloudflared` uma vez:

```bash
winget install --id Cloudflare.cloudflared
```

Depois publique de uma destas formas:

- **Executável**: clique com o botão direito no ícone da bandeja e escolha **Publicar na internet**. O endereço é copiado automaticamente.
- **Linha de comando**: com o Dignitas rodando, execute o comando abaixo e copie o endereço `trycloudflare.com` que aparecer.

  ```bash
  publicar.cmd
  ```

Por segurança, o servidor só aceita conexões da própria máquina (`server.address=127.0.0.1`). O acesso
externo acontece apenas pelo túnel.

## Testes

```bash
mvnw.cmd -B test
```

- `core/src/test`: testes unitários JUnit das regras, sem banco.
- `app/src/test`: testes de API e de segurança com MockMvc, usando H2 em modo MySQL (perfil `test`).
  Os testes não leem a configuração local.
