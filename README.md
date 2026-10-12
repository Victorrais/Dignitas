# Dignitas

Aplicação desktop para **organizar, extrair e resolver questões de concursos públicos**.
O Dignitas importa provas em PDF (com texto ou digitalizadas), identifica as questões automaticamente,
deixa você revisar o resultado e guarda tudo num banco MySQL local para estudo offline.

## Status do projeto

Em desenvolvimento (versão 2.0: reescrita em JavaFX).

## Versão web

A pasta [`web/`](web/README.md) tem a versão web do Dignitas: API REST em Spring Boot sobre as mesmas regras,
com front-end em HTML, CSS e JavaScript, login e o mesmo banco MySQL. Ela pode ser usada de três formas:

- **Executável para Windows** (`Dignitas.exe`, com Java embutido): sobe o servidor, abre o navegador e fica na bandeja.
- **Pela linha de comando**, com Maven.
- **Na internet**, por um túnel a partir do seu computador (`publicar.cmd` ou pelo ícone da bandeja).

Detalhes em [web/README.md](web/README.md).

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Interface | JavaFX 25 + [AtlantaFX](https://github.com/mkpaz/atlantafx) (tema Primer claro/escuro) + Ikonli (ícones Feather) |
| Persistência | MySQL 8, Hibernate ORM 6.6 (JPA), HikariCP |
| Leitura de PDF | Apache PDFBox 3 (texto com posição de cada caractere) |
| OCR | Tesseract via Tess4J (páginas digitalizadas) |
| Build e testes | Maven (wrapper incluso), JUnit 5, H2 (somente nos testes) |

## Funcionalidades

- **Início:** resumo do banco, taxa de acerto, desempenho por disciplina e importações recentes.
- **Resolver questões:** filtros por disciplina, banca, ano e situação (não respondidas, erradas, marcadas).
  Mostra o texto de apoio e a imagem, dá a correção na hora e tem atalhos de teclado (A–E e Enter).
- **Histórico:** todas as respostas, com busca, filtro por acerto ou erro e paginação. A mesma questão pode ser respondida várias vezes.
- **Estatísticas:** acerto por disciplina, por banca e evolução nos últimos 30 dias.
- **Banco de questões:** busca, filtros, ordenação, cadastro manual, edição e exclusão.
- **Importar PDF:** assistente em 3 passos (arquivo e concurso → leitura → revisão).
- **Importações:** lista dos PDFs importados; excluir uma importação remove todas as questões dela.
- **Configurações:** conexão com o MySQL, pacote de OCR, tema claro ou escuro e tamanho da interface.

Atalhos: **Ctrl+B** recolhe/expande a barra lateral; **Ctrl +** / **Ctrl −** ajustam o tamanho da interface
(**Ctrl 0** volta ao padrão de 85%). Ao resolver questões, os botões de texto e imagem no topo da questão
ocultam o material de apoio, e um clique numa imagem a abre ampliada.

## Como a extração funciona

A extração não depende de um modelo fixo de prova, porque os layouts mudam a cada ano e a cada banca.

1. **Leitura por página** (`extraction/PdfTextExtractor`): o PDFBox entrega cada caractere com sua posição.
   As linhas são montadas a partir dessas posições, e não do texto corrido. A calha entre colunas é
   detectada pela posição dos caracteres (funciona mesmo com só ~12 pt entre colunas, como no ENEM).
   Microtexto de segurança e marcas fora da página são descartados. As figuras (imagens rasterizadas)
   são localizadas (`extraction/PdfImageLocator`) e entram na ordem de leitura.
2. **OCR quando necessário** (`extraction/OcrEngine`): páginas sem texto legível (digitalizadas ou com fontes
   sem mapeamento de caracteres) são renderizadas a 300 dpi e lidas pelo Tesseract.
   O resultado tem o mesmo formato do texto nativo.
3. **Layout** (`extraction/LayoutAnalyzer`): remove cabeçalhos e rodapés repetidos, detecta a calha entre
   colunas e lê a coluna da esquerda antes da direita. Quadros com duas colunas dentro de uma página de
   coluna única também são lidos coluna por coluna.
4. **Segmentação** (`extraction/QuestionSegmenter`): reconhece padrões comuns às bancas.
   - Rótulos de questão: "Questão 12", "QUESTÃO 12", "12.", "12 –". Quando a prova usa rótulos,
     números soltos (instruções da capa, números de linha dos textos) não abrem questões.
   - Alternativas: "(A)", "A)", "a.", inclusive várias na mesma linha, e só a letra ("A texto…", estilo ENEM),
     reconhecida pela sequência A–E alinhada (um enunciado como "A autora conclui…" não vira alternativa).
   - **Alternativas em imagem**: a figura ao lado da letra vira a imagem da alternativa.
   - **Texto compartilhado**: "Texto para as questões de 06 a 10" vale, individualmente, para cada questão da
     faixa; o texto-base pode ser só imagem (charge, cartaz) ou texto com imagem.
   - Texto da própria questão: o que vem antes da referência ("SOBRENOME, N. …", "Disponível em: …") vira o
     texto-base da questão, e o restante, o comando. TEXTO I / TEXTO II dentro da questão são mantidos.
   - Números de linha à margem dos textos são removidos.
   - Opção de língua estrangeira ("Questões de 01 a 05 (opção inglês/espanhol)") vira a disciplina.
   - "Proposta de Redação" e similares encerram a leitura até a próxima área.
   - Itens de Certo/Errado (estilo Cebraspe).
   - Textos-base compartilhados: "Texto I", "Leia o texto… questões 1 a 3".
   - Fontes: "Disponível em:", "(Adaptado)".
   - Títulos de disciplina.
5. **Gabarito** (`extraction/AnswerKeyParser`): lê pares ("01 - A"), tabelas (linha de números + linha de
   respostas) e tabelas com duas colunas de resposta (ENEM: Inglês e Espanhol; as questões da opção de
   Espanhol usam a segunda). "X", "*" e "ANULADA" marcam a questão como anulada.
6. **Revisão obrigatória:** a página do PDF aparece ao lado das questões extraídas, com a área de cada questão destacada.
   - Questões incompletas, duvidosas ou duplicadas ficam sinalizadas.
   - Dá para editar, mesclar, remover e anexar o recorte da página como imagem.
   - Nada é gravado antes de você confirmar.

## Banco de dados

O esquema fica em [`src/main/resources/sql/schema.sql`](src/main/resources/sql/schema.sql) e é a fonte da verdade
(o Hibernate não altera tabelas). O script é aplicado automaticamente ao conectar e pode ser executado várias vezes;
bancos criados por versões anteriores recebem as colunas novas (`SchemaInstaller`).

| Tabela | Conteúdo |
|---|---|
| `exam` | concurso: nome, banca, ano, cargo |
| `subject` | disciplina |
| `import_batch` | PDF importado (hash único: impede importar o mesmo arquivo duas vezes) |
| `base_text` | texto de apoio (texto e/ou imagem), compartilhável entre questões |
| `question` | enunciado, número, imagem, anulada, marcada, hash de conteúdo (impede duplicadas) |
| `alternative` | letra, texto, imagem e se é a correta |
| `attempt` | cada resposta do usuário, com data e acerto |

Excluir uma importação remove em cascata as questões, alternativas e respostas ligadas a ela.

## Como executar

Requisitos: **JDK 25** e **MySQL 8** em execução. O Maven não precisa estar instalado, porque o wrapper `mvnw` baixa a versão certa.

```bash
./mvnw javafx:run
```

No Windows, use `mvnw.cmd javafx:run`.

Na primeira execução, abra **Configurações** e informe servidor, usuário e senha do MySQL.
O banco `dignitasdb` e as tabelas são criados automaticamente.
As configurações ficam em `%APPDATA%\Dignitas\config.properties` (a senha é gravada em texto puro, nesse arquivo local).

Para gerar um jar executável único:

```bash
./mvnw package
java -jar target/dignitas-2.0.jar
```

### OCR

PDFs com texto são lidos sem OCR. Para provas digitalizadas, o Dignitas oferece baixar **uma única vez** o
modelo de português do Tesseract (`por.traineddata`, do repositório oficial
[tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast)) para `%APPDATA%\Dignitas\tessdata`.
Também é possível apontar outra pasta em Configurações. Depois disso, o OCR funciona offline.

## Testes

```bash
./mvnw test
```

- `ServicesTest`: executa o `schema.sql` real num H2 em modo MySQL e testa os serviços via Hibernate
  (histórico com respostas repetidas, duplicidade, edição preservando o histórico, exclusão em cascata).
- `ExtractionTest`: gera PDFs com o próprio PDFBox e verifica a extração com uma e duas colunas,
  cabeçalho repetido, texto-base compartilhado, Certo/Errado e os formatos de gabarito.

### Validação com o ENEM 2025

O caderno 1 (Azul) do 1º dia do ENEM 2025 (INEP) foi usado como prova real: 32 páginas, duas colunas,
textos compartilhados, figuras, números de linha e gabarito com colunas de Inglês e Espanhol. Resultado:
**95 de 95 questões** (90 + 5 da opção de Espanhol) extraídas com texto-base, fonte, comando, 5 alternativas e
a resposta igual à do gabarito oficial em todas; importadas no MySQL e lidas de volta pela aplicação.

Ferramentas de desenvolvimento (em `src/test`), úteis para estudar novas provas:

- `extraction.PdfInspect <pdf> <pasta> [dpi]`: renderiza as páginas e despeja linhas e figuras com coordenadas.
- `extraction.ImportReport <prova.pdf> <gabarito.pdf|-> <pasta>`: roda a importação e gera um relatório de cada
  questão, com as imagens recortadas.
- `MySqlImportCheck`: importa uma prova num MySQL real (credenciais em `DIGNITAS_DB_USER`/`DIGNITAS_DB_PASSWORD`).
- `UiSnapshot`: renderiza telas fora da tela (sem abrir janela) para conferência visual.

Para abrir a interface sem um servidor MySQL (banco H2 em memória com questões de exemplo), rode a classe de teste
`br.com.dignitas.DevLauncher` pela IDE.

## Estrutura

```
src/main/java/br/com/dignitas/
  app/          ponto de entrada, configurações do usuário
  domain/       entidades JPA
  persistence/  Hibernate (Jpa) e criação do esquema
  model/        rascunhos de questão (extração/editor) e utilidades de texto
  service/      regras de negócio e consultas
  extraction/   PDF → questões (PDFBox, Tesseract, layout, segmentação, gabarito)
  ui/           janela, componentes e telas (ui/pages)

web/            versão web (projeto Maven próprio)
  core/         regras de negócio sem interface, reaproveitadas pela API
  app/          Spring Boot: API REST, login, front-end estático e empacotamento (packaging/)
```

## Limitações conhecidas

- A identificação é heurística: provas com formatação incomum podem exigir correções na tela de revisão.
- Só imagens rasterizadas são reconhecidas como figuras automaticamente; gráficos e tabelas desenhados em vetor
  ficam como texto. Use "Anexar recorte" na revisão para guardar a imagem da área da questão.
- O OCR perde qualidade em fórmulas, sobrescritos e digitalizações de baixa resolução.
- Se o gabarito tiver vários tipos de prova, vale a primeira ocorrência de cada número. Use o gabarito do caderno certo.

## Time de desenvolvimento

- João V. Q. G. Araujo
