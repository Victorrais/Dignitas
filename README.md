# Dignitas

Aplicação desktop para **organizar, extrair e resolver questões de concursos públicos**.
O Dignitas importa provas em PDF (com texto ou digitalizadas), identifica as questões automaticamente,
deixa você revisar o resultado e guarda tudo num banco MySQL local para estudo offline.

## Status do projeto

Em desenvolvimento (versão 2.0: reescrita em JavaFX).

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
- **Configurações:** conexão com o MySQL, pacote de OCR e tema claro ou escuro.

## Como a extração funciona

A extração não depende de um modelo fixo de prova, porque os layouts mudam a cada ano e a cada banca.

1. **Leitura por página** (`extraction/PdfTextExtractor`): o PDFBox entrega cada caractere com sua posição.
   As linhas são montadas a partir dessas posições, e não do texto corrido, para não misturar colunas lado a lado.
2. **OCR quando necessário** (`extraction/OcrEngine`): páginas sem texto legível (digitalizadas ou com fontes
   sem mapeamento de caracteres) são renderizadas a 300 dpi e lidas pelo Tesseract.
   O resultado tem o mesmo formato do texto nativo.
3. **Layout** (`extraction/LayoutAnalyzer`): remove cabeçalhos e rodapés repetidos, detecta a calha entre
   colunas e lê a coluna da esquerda antes da direita.
4. **Segmentação** (`extraction/QuestionSegmenter`): reconhece padrões comuns às bancas.
   - Rótulos de questão: "Questão 12", "12.", "12 –".
   - Alternativas: "(A)", "A)", "a.", inclusive várias na mesma linha.
   - Itens de Certo/Errado (estilo Cebraspe).
   - Textos-base compartilhados: "Texto I", "Leia o texto… questões 1 a 3".
   - Fontes: "Disponível em:", "(Adaptado)".
   - Títulos de disciplina.
5. **Gabarito** (`extraction/AnswerKeyParser`): lê pares ("01 - A") e tabelas (linha de números + linha de
   respostas). "X", "*" e "ANULADA" marcam a questão como anulada.
6. **Revisão obrigatória:** a página do PDF aparece ao lado das questões extraídas, com a área de cada questão destacada.
   - Questões incompletas, duvidosas ou duplicadas ficam sinalizadas.
   - Dá para editar, mesclar, remover e anexar o recorte da página como imagem.
   - Nada é gravado antes de você confirmar.

## Banco de dados

O esquema fica em [`src/main/resources/sql/schema.sql`](src/main/resources/sql/schema.sql) e é a fonte da verdade
(o Hibernate não altera tabelas). O script é aplicado automaticamente ao conectar e pode ser executado várias vezes.

| Tabela | Conteúdo |
|---|---|
| `exam` | concurso: nome, banca, ano, cargo |
| `subject` | disciplina |
| `import_batch` | PDF importado (hash único: impede importar o mesmo arquivo duas vezes) |
| `base_text` | texto de apoio, compartilhável entre questões |
| `question` | enunciado, número, imagem, anulada, marcada, hash de conteúdo (impede duplicadas) |
| `alternative` | letra, texto e se é a correta |
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
```

## Limitações conhecidas

- A identificação é heurística: provas com formatação incomum podem exigir correções na tela de revisão.
- Figuras, gráficos e tabelas não são reconstruídos. Use "Anexar recorte" para guardar a imagem da área da questão.
- O OCR perde qualidade em fórmulas, sobrescritos e digitalizações de baixa resolução.
- Se o gabarito tiver vários tipos de prova, vale a primeira ocorrência de cada número. Use o gabarito do caderno certo.

## Time de desenvolvimento

- João V. Q. G. Araujo
