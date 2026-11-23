# TaskFlow v0.5

A task management app with an Angular frontend, a Java 21 Spring Boot API and PostgreSQL.

## What's new in v0.5

Users can register and sign in. The Angular dashboard, task counts, search, filters, and editing tools show the signed-in user's tasks. Spring Security checks the JWT and applies ownership to every task operation.

The API hashes passwords with BCrypt. Angular keeps the 30-minute JWT in memory and sends it as a bearer token to the task API. A page refresh, logout, or expiration returns the user to sign-in.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Compose
- Node.js 22.12 or later in the 22.x series, with npm

## Run

From the repository root in PowerShell, start Docker Desktop and run the backend launcher.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TaskFlow.ps1
```

The launcher starts PostgreSQL with the existing Compose container and `pgdata` volume, then runs Spring Boot from `backend/`. Stop a previous TaskFlow backend if it is still listening on port 8080.

In another PowerShell terminal, start Angular from the repository root. Run `npm.cmd ci` on the first setup and when dependencies change.

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Open `http://localhost:4200` and choose **Créer un compte**. Enter your email and choose a password of at least 12 characters and at most 72 UTF-8 bytes. Angular uses `http://localhost:8080/api`. The default allowed browser origin is `http://localhost:4200`, configurable with `TASKFLOW_FRONTEND_ORIGIN`.

To check isolation, create a task with one account, sign out, and register a second account. The second account starts with an empty dashboard.

### Local secrets

On its first run, the launcher creates `%LOCALAPPDATA%\TaskFlow\.env.local` outside the checkout and reuses that file on later starts. [The example file](backend/.env.example) lists the two settings with blank values. The launcher saves a valid JWT key inherited from the current terminal on first initialization, or generates one using the system's cryptographic random generator. Explicit environment variables take precedence over the file.

Set `TASKFLOW_LOCAL_SECRETS` to an absolute file path to use another location. When starting Maven directly from `backend/`, Spring Boot also recognizes the Git-ignored `backend/.env.local` if no path is set. The launcher passes the selected path to Spring Boot in its Maven process.

If the demo tasks are still awaiting assignment, use the following command once to copy the claim code locally. It copies only the claim code, with Windows clipboard history and synchronization disabled for that entry.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TaskFlow.ps1 -InitializeOnly -CopyDemoClaimCode
```

Paste it into **Code d’attribution** when registering `user01@mail.com`. After the registration succeeds, the account signs in with its email and password. The demo claim code can be left blank in the configuration thereafter.

## PostgreSQL migration

Flyway runs on backend startup. It baselines a v0.4 database with its existing `task_item` table at version 1, then applies [migration V2](backend/src/main/resources/db/migration/V2__users_and_task_ownership.sql). An empty database runs both migrations. Hibernate validates the resulting schema.

V2 adds the user table and a nullable foreign key for task owners. It records IDs 2 through 7 that already exist as the tasks eligible for the demo claim. Registration of `user01@mail.com` with the configured code assigns these tasks and records the claim in the same transaction. The claim can be used once. Task 8 keeps its original data with a null owner. The API excludes ownerless tasks from every task operation.

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
| POST | `/api/auth/register` | 201 with `accessToken`, `expiresAt`, and `email` |
| POST | `/api/auth/login` | 200 with `accessToken`, `expiresAt`, and `email` |
| GET | `/api/tasks` | 200 with the current user's task list |
| GET | `/api/tasks/{id}` | 200 with the task |
| POST | `/api/tasks` | 201 with the new task |
| PUT | `/api/tasks/{id}` | 200 with the updated task |
| DELETE | `/api/tasks/{id}` | 204 with an empty body |

Both authentication endpoints accept `email` and `password`. Registration also accepts `claimCode` for the pending demo claim. Use `Authorization: Bearer <accessToken>` on task requests.

### Create or update a task

- `title` is required, must contain a non-whitespace character, and has a limit of 120 characters.
- `description` is optional and has a limit of 2,000 characters.
- `status` accepts `TODO`, `IN_PROGRESS`, or `DONE`. Omitting it sets the status to `TODO` on both create and update. An explicit `null` returns HTTP 400.

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TASKFLOW_ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Build Angular UI","description":"Create task board","status":"TODO"}'
```

### Error responses

A centralized exception handler returns JSON with `status`, a user-facing `message`, and an `errors` object that maps invalid fields to their messages.

- **400** for invalid inputs.
- **401** for incorrect credentials or a missing, invalid, or expired task token.
- **403** for denied access or an incorrect demo claim code.
- **404** for a missing task, a task owned by another account, or a task without an owner.
- **409** when the email is already registered.

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

JUnit 5, Mockito, and Spring MockMvc cover CRUD validation, JWT authentication, ownership isolation, and the one-time claim. The integration tests migrate and exercise an isolated in-memory database in PostgreSQL compatibility mode.

## Next milestones

- Project ownership and task assignment
- Pagination and search
- GitHub Actions

> Portfolio starter, not production ready. Database credentials are for local development only.
