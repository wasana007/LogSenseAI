# Dashboard for loggovervåking

---
> Hendelsesdrevet full-stack system for sanntids loggprosessering og automatisk feildiagnostikk med AI-støtte, der
> Apache Kafka fungerer som kjernen i hendelsesflyten. Systemet er bygget med Java 21, Spring Boot 3, Kafka, React,
> PostgreSQL, Elasticsearch og integrasjon mot Ollama LLM.
> En hendelsesdrevet plattform der Kafka håndterer all asynkron meldingsflyt mellom API, AI-agent og datalagring.
> Systemet er utviklet for å hjelpe utviklere og QA-team med raskere feilsøking ved å sentralisere og analysere
> applikasjonslogger i sanntid.
> Alle loggforespørsler publiseres som events i Kafka, som sikrer løs kobling mellom komponenter, høy skalerbarhet og
> robusthet ved feilhåndtering.
> Ved å kombinere Kafka-basert event streaming med AI-basert analyse reduseres tiden det tar å identifisere og forstå
> feil
> i distribuerte systemer betydelig.
> Backend er bygget med en lagdelt Spring Boot-arkitektur, mens frontend er utviklet i React, koblet sammen via REST API
> og sikret med OAuth2/JWT.
> Autentiseringsleverandøren er utbyttbar — Google brukes som standard, men arkitekturen støtter alle
> OAuth2-leverandører
> som følger standarden.

## 🎥 Demo

### 🎬 Dashboard for loggovervåking - Klikk på bildet nedenfor for å se hele demoen på YouTube ▶️

[![Watch Demo](docs/images/logsenseai.jpg)](https://www.youtube.com/watch?v=MTGsfn9Y7eY&list=PLOwWtF7kBLb8EYRrO9Z94Oalhewrdnwmj)
NB! Stemmen i videoen er generert med AI-basert tekst-til-tale-teknologi.

Designprioriteter:

- Skalerbar og løst koblet backend ved hjelp av Kafka for asynkron meldingshåndtering
- Ren lagdelt arkitektur med tydelig separasjon mellom API-, agent- og persistenslag
- AI-agentsystem der Ollama LLM dynamisk velger analyseverktøy ved kjøretid
- Sikkerhet gjennom Spring Security OAuth2 Login med egengenerert JWT
- Interaktiv React-frontend med sanntids statusoppdatering via WebSocket
- Strukturert datalagring og observabilitet gjennom PostgreSQL
- Elasticsearch-basert loggsøk med keyword- og statusfilter

---

## Arkitektur

```
Klient (React)
        │
        │  POST /api/v1/logs  →  returnerer correlationId umiddelbart
        ▼
Spring Boot REST API (LogController)
        │
        │  Publiserer til Kafka (key = correlationId)
        ▼
Apache Kafka (Hendelsesstrømming)
        │
        │  Konsumeres asynkront
        ▼
LogConsumerService
        │
        ▼
AI-agentlag
   ├── AgentService         ← Spør Ollama LLM hvilket verktøy som skal brukes
   ├── ToolRouter           ← Ruter til riktig verktøy basert på LLM-output
   └── Verktøy
        ├── DbTool           → PostgreSQL-analyse
        ├── KafkaTool        → Meldingskø-analyse
        └── OllamaTool       → Dyp LLM-resonnering (via OllamaClient)
        │
        ▼
LogStorageService  →  PostgreSQL (status: PENDING → COMPLETED / FAILED)
                   →  Elasticsearch (indeksering for søk)
        │
        ▲
        │  WebSocket /topic/logs  (push fra backend til React)
        │
Klient (React)
```

---

## Autentiseringsflyt (OAuth2 + JWT)

Spring Boot fungerer som **OAuth2 Login-server** — den håndterer hele OAuth2-flyten og utsteder sin egen JWT etter
vellykket innlogging. React kommuniserer utelukkende via JWT i `Authorization`-headeren.

```
 1.  React          →  åpner popup: /oauth2/authorization/google
 2.  Spring         →  redirecter til Google (med prompt=select_account)
 3.  Google         →  bruker logger inn og godtar
 4.  Google         →  callback til Spring: /login/oauth2/code/google
 5.  Spring         →  OAuth2 success handler kjøres
 6.  Spring         →  genererer egen JWT (email som claim)
 7.  Spring         →  redirect til React: /login-success?token=xxx
 8.  React          →  lagrer token i localStorage
 9.  React          →  kaller API med: Authorization: Bearer xxx
 10. Spring         →  JwtAuthFilter validerer token (signatur + utløp)
 11. Spring         →  returnerer brukerdata fra /api/v1/me
 12. React          →  ✔ Innlogget, dashboard vises
```

```
┌─────────────┐        ┌──────────────────┐        ┌────────────┐
│    React    │        │   Spring Boot    │        │   Google   │
└──────┬──────┘        └────────┬─────────┘        └─────┬──────┘
       │  /oauth2/authorization │                         │
       │──────────────────────▶│                         │
       │                       │── redirect ────────────▶│
       │                       │                         │ (login)
       │                       │◀── /login/oauth2/code ──│
       │                       │    successHandler        │
       │                       │    generateToken(JWT)    │
       │◀── redirect /login-success?token=xxx ───────────│
       │  localStorage.set(jwt)│                         │
       │  Bearer xxx ─────────▶│                         │
       │                       │ JwtAuthFilter validates  │
       │◀── user JSON ─────────│                         │
```

## Håndtering av payroll-log-events (Kafka)

Systemet støtter behandling av payroll-relaterte logghendelser via Kafka-topicet `payroll-log-events`. Disse hendelsene
sendes fra payroll-systemet og behandles asynkront i LogSenseAI.

Alle events følger et event-drevet flyt der hver melding identifiseres med en `correlationId` som brukes for sporing
gjennom hele systemet.

### Flyt for payroll-log-events 🔗 [Backend (Java Spring Boot + Kafka)](https://github.com/wasana007/payroll-backend)

```
Payroll Service
      │
      │  Publiserer LogEvent (JSON)
      ▼
Kafka topic: payroll-log-events
      │
      ▼
PayrollLogConsumer (LogSenseAI)
      │
      ├── Deserialisering av LogEvent
      ├── Lagrer PENDING i PostgreSQL
      │
      ├── Filtrerer bort INFO-logger
      │
      ├── Formaterer melding for AI-analyse
      ├── Kaller AgentService (Ollama LLM)
      │
      ├── Lagrer resultat (COMPLETED)
      └── eller feilhåndtering (FAILED)
      │
      ▼
PostgreSQL + WebSocket → React Dashboard
```

---

### Behandling i PayrollLogConsumer

Hver melding behandles stegvis:

| Steg | Beskrivelse                                       |
|------|---------------------------------------------------|
| 1    | Kafka konsumerer melding fra `payroll-log-events` |
| 2    | LogEvent deserialiseres fra JSON                  |
| 3    | INFO-logger filtreres bort fra AI-behandling      |
| 4    | Meldingen formateres for analyse                  |
| 5    | Lagres som `PENDING` i databasen                  |
| 6    | Sendes til AI-agent (AgentService / Ollama)       |
| 7    | Resultat lagres som `COMPLETED`                   |
| 8    | Feil lagres som `FAILED`                          |
| 9    | Resultat pushes til React via WebSocket           |

---

### Viktige designvalg

* Kafka bruker `correlationId` som nøkkel for å sikre rekkefølge per loggstrøm
* Systemet er **at-least-once delivery**, duplikater kan forekomme
* Idempotens håndteres via `correlationId`
* INFO-logger ekskluderes for å redusere belastning på AI-modellen
* Kun relevante logger (WARN/ERROR/DEBUG) sendes til AI-analyse

---

### Eventstruktur

```json
{
  "level": "ERROR",
  "source": "payroll-service",
  "employeeId": "12345",
  "timestamp": "2026-05-13T10:15:30",
  "message": "Feil i lønnsberegning på grunn av manglende verdi"
}
```

---

### Kafka topics

| Topic                             | Beskrivelse                                  |
|-----------------------------------|----------------------------------------------|
| `payroll-log-events`              | Inngående logghendelser fra payroll-systemet |
| `payroll-events`                  | Generelle payroll-domene events              |
| `payroll-log-events` → LogSenseAI | AI-basert analysepipeline                    |

---

### Full behandlingsflyt

```
Kafka → Consumer → AI Agent → PostgreSQL → WebSocket → React Dashboard
```

---

### Payload til frontend

```json
{
  "correlationId": "uuid",
  "status": "COMPLETED",
  "level": "ERROR",
  "employeeId": "12345",
  "message": "Feil i lønnsberegning",
  "result": "AI-analyse av årsak og forslag til løsning"
}
```

---

## Formål

Denne integrasjonen gir:

* Fullt løs koblet arkitektur mellom payroll og AI-systemet
* Skalerbar behandling av logghendelser via Kafka
* Mulighet for reanalyse av historiske events
* Raskere feilsøking gjennom AI-drevet innsikt
* Sanntids oppdatering i dashboard via WebSocket

---


**Nøkkelkomponenter:**

| Komponent             | Ansvar                                                         |
|-----------------------|----------------------------------------------------------------|
| `SecurityConfig.java` | OAuth2 Login-oppsett, CORS, JWT-filter, success handler        |
| `JwtUtil.java`        | Genererer og validerer JWT med egendefinert secret             |
| `JwtAuthFilter.java`  | Interceptor — trekker ut og validerer Bearer token per request |
| `MeController.java`   | `GET /api/v1/me` — returnerer innlogget brukers e-post         |

---

## Teknologistabel

| Lag               | Teknologi                      |
|-------------------|--------------------------------|
| Språk             | Java 21                        |
| Backend-rammeverk | Spring Boot 3, Spring Security |
| Frontend          | React                          |
| Meldingssystem    | Kafka + Zookeeper              |
| AI / LLM          | Ollama — llama3.2              |
| Database          | PostgreSQL + Spring Data JPA   |
| Søk               | Elasticsearch 8                |
| Autentisering     | OAuth2 Login  + egen JWT       |
| API-dokumentasjon | Swagger UI                     |
| Infrastruktur     | Docker + Docker Compose        |

---

## Nøkkelfunksjoner

### Fullt hendelsesdrevet arkitektur (async)

REST-laget returnerer umiddelbart med en `correlationId` — uten å vente på at AI-analysen fullføres. Kafka kobler
produsent og konsument fullstendig fra hverandre, noe som gir horisontal skalerbarhet og feilisolasjon.

```
POST /api/v1/logs
  → 202 Accepted
  → { "correlationId": "uuid", "status": "PENDING" }

WebSocket /topic/logs
  → { "correlationId": "uuid", "status": "COMPLETED", "result": "..." }
```

### AI-agentsystem med LLM-basert verktøyvalg

`AgentService` sender loggmeldingen til Ollama LLM med en strukturert prompt. LLM returnerer et JSON-objekt som angir
hvilket verktøy som skal brukes. `ToolRouter` ruter deretter til riktig verktøy basert på LLM-output.

**LLM-prompt (forenklet):**

```
Du er en AI-agent som velger riktig analyseverktøy for en loggmelding.
Tilgjengelige verktøy: DB_ANALYZE, KAFKA_ANALYZE, OLLAMA_ANALYZE
Svar KUN med: {"tool": "DB_ANALYZE"}
```

**LLM-output → verktøyruting:**

| LLM-beslutning   | Verktøy      | Brukes til                          |
|------------------|--------------|-------------------------------------|
| `DB_ANALYZE`     | `DbTool`     | Database-feil, SQL, tilkoblinger    |
| `KAFKA_ANALYZE`  | `KafkaTool`  | Kafka-feil, topic, consumer, broker |
| `OLLAMA_ANALYZE` | `OllamaTool` | Alle andre logger (LLM-resonnering) |

Systemet har innebygd fallback til `OLLAMA_ANALYZE` dersom LLM returnerer ugyldig JSON eller et ukjent verktøynavn.

Nye verktøy kan legges til ved å implementere verktøygrensesnittet og registrere en ny rutingoppføring — ingen endringer
kreves i `AgentService` eller LLM-prompten.

### CorrelationId-basert sporing

Hver loggforespørsel tildeles en unik `correlationId` (UUID) som følger meldingen gjennom hele systemet — fra
REST-mottak via Kafka til lagring i PostgreSQL. Resultatet mottas av React-klienten via WebSocket når analysen er
fullført.

**Statuslivssyklus:**

```
PENDING → COMPLETED
        → FAILED
```

### Datapersistens

Alle loggoppføringer lagres strukturert i PostgreSQL med `correlationId`, `status`, `createdAt` og `completedAt` — noe
som muliggjør historisk søk og nedstrømsanalyse. Fullførte logger indekseres i tillegg i Elasticsearch for rask
fulltekstsøk.

---

## Forespørselsflyt

```
1.  Bruker klikker "Logg inn" → React åpner Google-popup
2.  Spring redirecter til Google med prompt=select_account
3.  Bruker velger Google-konto og godtar
4.  Google sender callback til Spring: /login/oauth2/code/google
5.  Spring OAuth2 success handler kjøres
6.  Spring genererer JWT med email som claim
7.  Spring redirecter React til /login-success?token=xxx
8.  React lagrer JWT i localStorage
9.  Bruker sender logg via POST /api/v1/logs med Authorization: Bearer xxx
10. JwtAuthFilter validerer token (signatur + utløp)
11. LogController genererer correlationId og lagrer PENDING i PostgreSQL
12. LogController publiserer (correlationId, log) til Kafka-topic
13. LogController returnerer 202 Accepted med correlationId umiddelbart
14. React viser PENDING og venter på WebSocket-melding
15. LogConsumerService mottar melding fra Kafka asynkront
16. AgentService spør Ollama LLM: hvilket verktøy skal brukes?
17. LLM returnerer {"tool": "DB_ANALYZE"} (eller tilsvarende)
18. ToolRouter ruter til riktig verktøy (DbTool / KafkaTool / OllamaTool)
19. Resultat lagres i PostgreSQL og indekseres i Elasticsearch med status COMPLETED
20. Backend pusher COMPLETED via WebSocket → React viser resultatet
```

---

## API-referanse

### Send loggmelding (asynkron)

```http
POST /api/v1/logs
Content-Type: application/json
Authorization: Bearer <token>
```

**Forespørselskropp:**

```
ERROR: database connection timeout after 30s
```

**Svar — 202 Accepted:**

```json
{
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING"
}
```

---

### Søk i logger (Elasticsearch)

```http
GET /api/v1/logs/search?q=timeout
Authorization: Bearer <token>

GET /api/v1/logs/search/status/FAILED
Authorization: Bearer <token>
```

---

### Motta analyseresultat (WebSocket)

```
WS  /ws
SUB /topic/logs
```

**Melding — COMPLETED:**

```json
{
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "message": "ERROR: database connection timeout after 30s",
  "result": "Rotårsak: Databaseforbindelsen ble ikke etablert innen tidsfristen...",
  "createdAt": "2026-01-01T12:00:00",
  "completedAt": "2026-01-01T12:00:03"
}
```

**Eksempel med curl:**

```bash
# Send logg
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d 'ERROR: DB connection timeout after 30s'
```

---

## Prosjektstruktur

```
backend/
├── agent/
│   ├── AgentService.java          # Spør LLM og velger verktøy dynamisk
│   └── ToolRouter.java            # Ruter LLM-beslutning til riktig verktøy
├── config/
│   ├── JwtAuthFilter.java         # Validerer Bearer JWT per request
│   ├── JwtUtil.java               # Genererer og verifiserer JWT
│   ├── KafkaConfig.java           # Kafka producer-konfigurasjon
│   ├── SecurityConfig.java        # OAuth2 Login + JWT-filter + CORS
│   ├── SwaggerConfig.java         # OpenAPI / Swagger UI
│   └── WebSocketConfig.java       # Konfigurerer WebSocket/STOMP for sanntidsoppdateringer
├── controller/
│   ├── LogController.java         # POST (async) + GET /search + GET /search/status
│   └── MeController.java          # GET /api/v1/me → brukerens e-post
├── model/
│   ├── LogEntry.java              # correlationId, status, message, result, timestamps
│   ├── LogDocument.java           # Elasticsearch-dokument for indeksering
│   └── LogStatus.java             # Enum (PENDING, COMPLETED, FAILED)
├── repository/
│   ├── LogRepository.java         # JPA repository for LogEntry (findByCorrelationId)
│   └── LogSearchRepository.java   # Elasticsearch repository for søk
├── service/
│   ├── LogConsumerService.java    # Kafka-lytter → kaller AgentService
│   ├── LogNotificationService.java# WebSocket broadcasting av loggstatus til frontend
│   ├── LogStorageService.java     # Håndterer persistens i PostgreSQL og Elasticsearch
│   ├── OllamaClient.java          # HTTP-klient mot Ollama LLM API
│   └── PayrollLogConsumer.java    # Konsumerer payroll-log-events og trigger AI pipeline
└── tools/
    ├── DbTool.java                # Analyserer DB-relaterte logger
    ├── KafkaTool.java             # Analyserer Kafka-relaterte logger
    └── OllamaTool.java            # LLM-analyse via OllamaClient

frontend/
└── src/
    ├── App.jsx                    # OAuth2 popup-flyt + WebSocket-basert dashboard + Elasticsearch-søk
    ├── LoginSuccess.jsx           # Håndterer token fra OAuth2-redirect
    ├── index.js                   # Inngangspunkt
    ├── config.js                  # Alle konfigurasjonskonstanter
    └── hooks/
        ├── useLogApi.js           # POST logg + håndterer PENDING-status
        └── usePayrollLog.js       # WebSocket-tilkobling + live event-tabell
```

---

## Kom i gang

### Forutsetninger

- Java 21+
- Node.js 18+
- Docker og Docker Compose
- [Ollama](https://ollama.ai) installert lokalt
- OAuth2-legitimasjon (Client ID + Secret)

### 1. Start infrastruktur

```bash
docker-compose up -d
```

Starter PostgreSQL, Kafka, Zookeeper og Elasticsearch.

### 2. Start lokal LLM

```bash
ollama run llama3.2
```

Modellen eksponeres på `http://localhost:11434`.

### 3. Kjør backend

```bash
./mvnw spring-boot:run

```

Aktiver profil i IntelliJ:

```
Run Configuration → Environment variables → SPRING_PROFILES_ACTIVE=local
```

`application-local.yml` skal ikke committes til Git (ligger i `.gitignore`)

API tilgjengelig på `http://localhost:8080`  
Swagger UI på `http://localhost:8080/swagger-ui.html`

### 4. Kjør frontend

```bash
cd frontend
npm install
npm start
```

React-appen tilgjengelig på `http://localhost:3000`

---

## Hva dette prosjektet demonstrerer

- Full-stack utvikling med Java/Spring Boot og React
- Fullt hendelsesdrevet design med Apache Kafka (async, non-blocking)
- AI-agentarkitektur med LLM-basert verktøyvalg ved kjøretid
- CorrelationId-basert sporing gjennom hele systemet
- Statuslivssyklus (PENDING → COMPLETED / FAILED) med WebSocket-push
- Lokal LLM-integrasjon (Ollama) i en Spring Boot-backend
- OAuth2 Login + egengenerert JWT for stateless API-autentisering
- Popup-basert innloggingsflyt i React uten sideomlasting
- Sanntids logg-dashboard via WebSocket (STOMP + SockJS)
- Elasticsearch-integrasjon for historisk loggsøk og filtrering
- Ren lagdelt arkitektur med tydelig separasjon av ansvar
- Containerisert infrastruktur med Docker Compose
- RESTful API med OpenAPI/Swagger-dokumentasjon

---

## Veikart

- [x] Elasticsearch-integrasjon for loggsøk
- [ ] Flerstegs AI-resonnering (chain-of-thought agent)
- [ ] Minnelag for kontekstbevisst loggkorrelasjon
- [ ] Observabilitetsdashbord (React UI med historikk)
- [ ] Resilience4j (retry + circuit breaker for Ollama-kall)
- [ ] Distribuert sporing (OpenTelemetry + Jaeger)
- [ ] Unit- og integrasjonstester (JUnit 5 + Testcontainers)
- [ ] Prometheus-metrikker for Kafka og agent-ytelse

---

## Om

Utviklet som et læringsprosjekt innen full-stack arkitektur og AI-integrasjon, med fokus på Java, Spring Boot, Kafka,
hendelsesdrevet design, LLM-agentmønstre, Elasticsearch og React-frontend i produksjonslignende systemer.