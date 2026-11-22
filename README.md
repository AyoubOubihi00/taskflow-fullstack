# TaskFlow — starter API

A small task management REST API built with Java 21, Spring Boot and PostgreSQL.

## Prerequisites
- Java 21
- Maven 3.9+
- Docker Compose

## Run
```bash
docker compose up -d
cd backend
mvn spring-boot:run
```

API base: `http://localhost:8080/api/tasks`

## Endpoints
- `GET /api/tasks` — list tasks
- `GET /api/tasks/{id}` — retrieve task
- `POST /api/tasks` — create task
- `PUT /api/tasks/{id}` — update task
- `DELETE /api/tasks/{id}` — delete task

Example:
```bash
curl -X POST http://localhost:8080/api/tasks -H 'Content-Type: application/json' -d '{"title":"Build Angular UI","description":"Create task board","status":"TODO"}'
```

## Next milestones
- Angular frontend
- JWT authentication and authorization
- Project ownership and task assignment
- Pagination and search
- Flyway migrations instead of `ddl-auto=update`
- Unit/integration tests and GitHub Actions

> Portfolio starter, not production ready. Database credentials are for local development only.
