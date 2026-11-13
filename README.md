# Bibliotheca

**Bibliotheca** é uma estante virtual de livros com leitor embutido, desenvolvida com **Java 17+ / Spring Boot** no backend e **frontend web moderno** integrado (HTML, CSS e JavaScript vanilla). A aplicação permite enviar, organizar e ler livros em uma interface de estante realista com animações de página em 3D.

## Visão geral

- **Estante realista**: prateleiras de madeira, lombadas coloridas, hover 3D, busca/filtros e organização visual.
- **Leitor imersivo**: renderização de PDF/EPUB, temas de leitura, controles de navegação, progresso salvo automaticamente, marcadores, anotações, busca interna e TTS.
- **Persistência local simples**: banco H2 em arquivo e armazenamento dos livros no disco local, sem configuração externa.

## Funcionalidades

### Estante
- Upload de livros com **arrastar e soltar** (drag & drop)
- Formatos aceitos: `PDF, EPUB, MOBI, CBZ, TXT, FB2, AZW3`
- Visual de estante com prateleiras, lombadas, título/autor na lombada e barra de progresso
- Busca em tempo real por título, autor ou tag
- Filtros por status de leitura (lido, lendo, não lido)
- Ajuste de tamanho dos livros (pequeno/médio/grande)
- Tema claro/escuro com persistência
- Estado vazio e carregamento com skeleton

### Leitor
- Renderização de **PDF** via [PDF.js](https://mozilla.github.io/pdf.js/)
- Renderização de **EPUB** via [epub.js](https://github.com/futurepress/epub.js/)
- Leitura de **TXT** (e fallback textual) no leitor integrado
- **Animação de virar página** em 3D (Page Flip), além dos modos **Slide** e **Fade**
- Navegação por botões, slider de progresso, teclas `←`/`→`, clique nas laterais e swipe (touch)
- Temas de leitura: claro (papel), sépia, escuro e preto (OLED)
- Zoom, sumário (TOC), busca no livro, marcadores e anotações
- **TTS (Text-to-Speech)** via Web Speech API (quando suportado pelo navegador)
- Modo tela cheia (Fullscreen API)
- Progresso de leitura com **salvamento automático** e restauração ao reabrir

### Backend
- API REST para upload, listagem, metadados, arquivo, capa, progresso, marcadores, notas e busca
- Extração de metadados e capa de PDF com **Apache PDFBox** (e tentativa de capa para EPUB)
- Banco **H2** em arquivo (`./data/bibliotheca-db`)
- Armazenamento de arquivos em `./data/books/`
- Streaming de arquivos, validação de upload, cache com ETag e tratamento global de erros

## Stack

- **Java 17+**
- **Spring Boot 3.3.x** (Web, Data JPA, Validation)
- **H2 Database** (arquivo local)
- **Apache PDFBox** (metadados/capa de PDF)
- **Apache Tika (core)** (suporte/fallback)
- **Frontend**: HTML5, CSS3, JavaScript (ES modules)
- **Leitura no cliente**: PDF.js, epub.js, JSZip (via CDN)

## Requisitos

- **Java 17 ou superior** instalado (`java -version`)
- Acesso à internet na primeira execução para o Maven Wrapper baixar dependências (e para carregar bibliotecas de CDN do frontend, conforme configuração)
- Porta `8080` disponível (ou use outra porta)

> Maven não precisa estar instalado globalmente: o projeto inclui o **Maven Wrapper** (`./mvnw`).

## Como executar

No diretório do projeto:

```bash
./mvnw spring-boot:run
```

Alternativa (se tiver Maven instalado):

```bash
mvn spring-boot:run
```

Depois, abra no navegador:

- [http://localhost:8080](http://localhost:8080)

### Executar em outra porta

Se a porta `8080` já estiver em uso:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

Acesse: `http://localhost:8081`

### Build (sem executar)

```bash
./mvnw -DskipTests package
```

## Estrutura do projeto

```text
.
├─ pom.xml
├─ mvnw / mvnw.cmd
├─ .mvn/
├─ src/
│  ├─ main/
│  │  ├─ java/com/bibliotheca/
│  │  │  ├─ BibliothecaApplication.java
│  │  │  ├─ config/            # Configurações web
│  │  │  ├─ controller/        # Endpoints REST
│  │  │  ├─ service/           # Regras de negócio, storage e metadados
│  │  │  ├─ repository/        # Repositórios JPA
│  │  │  ├─ entity/            # Entidades (Book, Progress, Bookmark, Note, Shelf)
│  │  │  ├─ dto/               # DTOs de request/response
│  │  │  └─ exception/         # Exceções e handler global
│  │  └─ resources/
│  │     ├─ application.properties
│  │     └─ static/            # Frontend (index.html, css/, js/)
│  └─ test/
└─ data/
   ├─ bibliotheca-db*          # Banco H2 (gerado em runtime)
   └─ books/                   # Arquivos enviados e capas
```

## API (principais endpoints)

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/books/upload` | Upload de livro (multipart, campo `file`) |
| `GET` | `/api/books` | Lista livros |
| `GET` | `/api/books/{id}` | Detalhes do livro |
| `GET` | `/api/books/{id}/file` | Arquivo do livro (stream) |
| `GET` | `/api/books/{id}/cover` | Capa do livro |
| `PATCH` | `/api/books/{id}` | Atualiza título, autor, tags, descrição |
| `DELETE` | `/api/books/{id}` | Remove livro e arquivos |
| `GET` | `/api/books/{id}/progress` | Obtém progresso |
| `PUT` | `/api/books/{id}/progress` | Salva progresso |
| `GET` | `/api/books/{id}/bookmarks` | Lista marcadores |
| `POST` | `/api/books/{id}/bookmarks` | Cria marcador |
| `GET` | `/api/books/{id}/notes` | Lista anotações |
| `POST` | `/api/books/{id}/notes` | Cria anotação |
| `GET` | `/api/books/{id}/search?q=...` | Busca no conteúdo do livro |

## Configuração

As principais configurações ficam em `src/main/resources/application.properties`:

- `server.port` — porta do servidor (padrão: `8080`)
- `spring.datasource.url` — caminho do banco H2
- `spring.servlet.multipart.max-file-size` / `max-request-size` — limite de upload
- `bibliotheca.storage.path` — diretório de armazenamento dos livros
- `bibliotheca.upload.max-size-bytes` — tamanho máximo de upload
- `bibliotheca.upload.allowed-ext` — extensões permitidas
