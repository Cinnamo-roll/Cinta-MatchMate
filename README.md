# MatchMate

MatchMate is a mobile-first campus partner matching app. It includes user discovery, partner recommendations, real-time chat, admin review, single-device login control, and a card-room ledger for group score and expense tracking.

## Tech Stack

| Area | Stack |
| --- | --- |
| Mobile frontend | Vue 3, Vite, TypeScript, Vant 4 |
| Backend | Spring Boot 4, Java 17, MyBatis-Plus |
| Database | MySQL 8 |
| Cache and session | Redis, Spring Session, Redisson |
| Realtime | WebSocket |
| Storage | Aliyun OSS |

## Project Structure

```text
MatchMate/
|-- matchmate-mobile/   # Vue mobile client
|-- matchmate-server/   # Spring Boot API server
`-- README.md
```

## Core Features

- Mobile homepage with paged partner loading and friendly empty/loading/error states.
- Rule-based partner recommendation with scores, reasons, and pagination.
- Real-time chat, unread/read status, and conversation management.
- Admin user management, ban/unban, and registration review by daily quota.
- Single-device login takeover with old-session invalidation.
- Card-room ledger with members, rounds, transfers, shared funds, undo flow, and rankings.

## Local Development

Backend:

```bash
cd matchmate-server
./mvnw spring-boot:run
```

Frontend:

```bash
cd matchmate-mobile
npm install
npm run dev
```

Default local endpoints:

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`

## Database

Create the MySQL schema with:

```bash
mysql -u root -p < matchmate-server/src/main/resources/schema.sql
```

Runtime credentials should be provided with environment variables such as `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, and `REDIS_PASSWORD`.

## Useful Commands

```bash
# Frontend build
cd matchmate-mobile
npm run build

# Backend tests
cd matchmate-server
./mvnw test
```
