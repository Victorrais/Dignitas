# Dignitas

## Status do Projeto
Em desenvolvimento

## Tecnologias Utilizadas

- Java  
- Swing (Interface gráfica)  
- MySQL  
- JDBC  
- JPA / Hibernate  

## Time de Desenvolvedores

- João V. Q. G. Araujo  

## Objetivo do Software

O sistema Dignitas tem como objetivo oferecer uma solução prática e acessível para organização, extração e resolução de questões de concursos públicos, auxiliando usuários no processo de estudo e aumentando suas chances de aprovação.

A aplicação automatiza a estruturação de questões a partir de arquivos PDF textuais, permitindo ao usuário interagir com o conteúdo de forma eficiente, mesmo em ambiente offline.

## Funcionalidades do Sistema (Requisitos)

### Cadastro de Questões
- Importação de arquivos PDF textuais com modelos previamente definidos  
- Extração automática de enunciado, alternativas e gabarito  
- Validação de modelo suportado  
- Verificação de duplicidade de questões  
- Armazenamento em banco de dados local  

### Consulta e Filtragem de Questões
- Filtragem por:
  - Concurso  
  - Instituição organizadora  
  - Disciplina  

### Registro e Histórico de Resolução
- Seleção de alternativas pelo usuário  
- Indicação de acerto ou erro  
- Armazenamento das respostas  

#### Módulos adicionais:
- Revisão de questões já respondidas  
- Histórico de questões resolvidas  
- Estatísticas de desempenho do usuário  

### Exclusão de Banco
- Remoção de questões individuais  
- Exclusão de importações completas de PDFs  

### Operação Offline
- Funcionamento completo sem conexão com a internet  
- Internet necessária apenas para atualizações futuras  

---

## Observações

- O sistema não utiliza OCR nas versões iniciais  
- Apenas PDFs textuais são suportados  
- Os dados são armazenados localmente  
- O sistema evita duplicidade de questões  

---
