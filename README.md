# TaskFlow v0.3

A small task management REST API built with Java 21, Spring Boot and PostgreSQL.

## What's new in v0.3

The API uses separate request and response DTOs while keeping the controller → service → repository architecture. The request DTO accepts task fields, and the response DTO returns those fields with the database-generated ID.

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

The API is available at `http://localhost:8080/api/tasks`.

## Endpoints

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/tasks` | 200 with the task list |
| GET | `/api/tasks/{id}` | 200 with the task |
| POST | `/api/tasks` | 201 with the new task |
| PUT | `/api/tasks/{id}` | 200 with the updated task |
| DELETE | `/api/tasks/{id}` | 204 with an empty body |

### Create or update a task

- `title` is required, must contain a non-whitespace character, and has a limit of 120 characters.
- `description` is optional and has a limit of 2,000 characters.
- `status` accepts `TODO`, `IN_PROGRESS`, or `DONE`. Omitting it sets the status to `TODO` on both create and update. An explicit `null` returns HTTP 400.

```bash
curl -X POST http://localhost:8080/api/tasks -H 'Content-Type: application/json' -d '{"title":"Build Angular UI","description":"Create task board","status":"TODO"}'
```

### Error responses

A centralized exception handler returns JSON with `status`, a user-facing `message`, and an `errors` object that maps invalid fields to their messages. Invalid requests return HTTP 400. Looking up, updating, or deleting a missing task returns HTTP 404 with an empty `errors` object.

```json
{
  "status": 400,
  "message": "Les données de la tâche sont invalides.",
  "errors": {
    "title": ["Le titre est obligatoire."]
  }
}
```

## Tests

From the repository root, run `mvn clean test` in the backend directory.

```bash
cd backend
mvn clean test
```

JUnit 5 and Spring MockMvc exercise the CRUD routes, validation limits, and HTTP 400 and 404 responses. Mockito replaces the repository, so the suite runs without PostgreSQL and leaves the database volume intact.

## Next milestones

- Angular frontend
- JWT authentication and authorization
- Project ownership and task assignment
- Pagination and search
- Flyway migrations instead of `ddl-auto=update`
- GitHub Actions

> Portfolio starter, not production ready. Database credentials are for local development only.
