# TaskFlow v0.4

A task management app with an Angular frontend, a Java 21 Spring Boot API and PostgreSQL.

## What's new in v0.4

The Angular dashboard shows live task counts and completion. The task list supports title search, status filters, and creating, editing, and deleting tasks. Forms follow the API's validation rules and display feedback for successful and failed operations.

Angular calls the existing Spring Boot API, which reads and writes tasks in PostgreSQL. The API uses separate request and response DTOs and follows the controller → service → repository architecture.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Compose
- Node.js 22.12 or later in the 22.x series, with npm

## Run

Start PostgreSQL from the repository root. Docker Compose reuses the existing `pgdata` volume for this project.

```bash
docker compose up -d postgres
```

Start the API in one terminal from the repository root.

```bash
cd backend
mvn spring-boot:run
```

Start Angular in another terminal from the repository root.

```bash
cd frontend
npm ci
npm start
```

Open `http://localhost:4200`. Angular sends requests to `http://localhost:8080/api/tasks`. Use `localhost` in the browser to match the API's allowed origin.

On Windows PowerShell, use `npm.cmd` in place of `npm` if the script execution policy blocks `npm.ps1`.

## Build the frontend

```bash
cd frontend
npm ci
npm run build
```

The production files are in `frontend/dist/taskflow/browser/`.

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

- JWT authentication and authorization
- Project ownership and task assignment
- Pagination and search
- Flyway migrations instead of `ddl-auto=update`
- GitHub Actions

> Portfolio starter, not production ready. Database credentials are for local development only.
