# TaskFlow

TaskFlow is a task manager with a Java 21 API, an Angular frontend, and PostgreSQL persistence. Each user manages a private task list. The dashboard shows task counts and progress across three statuses.

## Features

- Account registration, sign-in, and sign-out
- A dashboard and a task list with search and status filters
- Task creation, editing, and deletion with field validation
- French user interface and API error messages

## Tech stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven |
| Frontend | Angular standalone components, TypeScript, Angular Router, Reactive Forms |
| Data | PostgreSQL, Flyway, Docker Compose |
| Backend tests | JUnit 5, Spring MockMvc, Mockito, H2 in PostgreSQL compatibility mode |

## Architecture

The Angular features call the API through HTTP services. A route guard controls navigation and an interceptor sends the JWT on task requests. Spring Security verifies the token. REST controllers handle requests, services apply business rules, and repositories query PostgreSQL. Request and response DTOs define the REST contract.

## Project structure

```text
backend/
  src/main/java/com/portfolio/taskflow/
    TaskflowApplication.java
    config/       Spring Security filter chain and CORS
    controller/   Authentication and task endpoints
    dto/          API requests, responses, and errors
    entity/       JPA entities
    exception/    Request errors and centralized handling
    repository/   JPA queries and the demo claim
    security/     JWT creation and validation
    service/      Account and task operations
  src/main/resources/db/migration/  Flyway migrations
  src/test/java/com/portfolio/taskflow/
    config/       Local configuration tests
    controller/   REST and integration tests
    support/      Test-only secret generation
frontend/src/app/
  core/          API configuration, authentication, and HTTP errors
  features/auth/ Sign-in and registration
  features/tasks/ Workspace, task dialogs, and task API
  app.component.ts
  app.routes.ts
```

## Prerequisites

- Java 21 and Maven 3.9 or later
- Docker Desktop with Docker Compose
- Node.js 22.12 or later in the 22.x series, with npm

## Getting started

From the repository root in PowerShell, with Docker Desktop running, start PostgreSQL and the API.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TaskFlow.ps1
```

The launcher starts PostgreSQL on `127.0.0.1:5432`, waits for it, and starts Spring Boot on port 8080. It stores local secrets in `%LOCALAPPDATA%\TaskFlow\.env.local` outside the checkout and OneDrive. Docker Compose and Spring Boot inherit the same `DATABASE_PASSWORD` for the launch. Existing JWT signing keys and demo claim codes are preserved.

In a second terminal, from the repository root, install the frontend dependencies and start Angular.

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Open [TaskFlow](http://localhost:4200) and select **Créer un compte**. Choose a password with at least 12 characters and at most 72 UTF-8 bytes.

### Existing PostgreSQL volume

The launcher generates a 32-byte random database password only when the resolved Compose volume does not exist. If the volume already exists, the launcher needs its current role password in the private configuration. To register that password without changing the role or starting containers, use the masked prompt.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TaskFlow.ps1 -InitializeOnly -ConfigureDatabasePassword
```

This lets you start TaskFlow before an approved rotation if you need to resume work. The old development password remains usable against the existing role until the role password is changed. Keep a copy of the current password in a password manager until the rotation is verified.

#### Back up the database before rotating

Stop the API with Ctrl+C in its terminal and pause any other writers. Keep the PostgreSQL container running. From the repository root, identify the service and save a verified archive outside Git and OneDrive. This example targets the default Compose project.

```powershell
$containers = @(docker ps --filter 'label=com.docker.compose.project=taskflow' --filter 'label=com.docker.compose.service=postgres' --format '{{.ID}}')
if ($LASTEXITCODE -ne 0 -or $containers.Count -ne 1) { throw 'Expected one running TaskFlow PostgreSQL container.' }
$pg = $containers[0]
$backupDir = Join-Path $env:LOCALAPPDATA 'TaskFlow\backups'
[void][IO.Directory]::CreateDirectory($backupDir)
$archive = Join-Path $backupDir ("taskflow-before-rotation-" + (Get-Date -Format 'yyyyMMdd-HHmmssfff') + '.pgdump')
$tempDir = (docker exec $pg mktemp -d).Trim()
if ($LASTEXITCODE -ne 0 -or $tempDir -notmatch '^/tmp/[^/]+$') { throw 'Could not create the private container directory.' }
$tempArchive = "$tempDir/taskflow.pgdump"
try {
    docker exec $pg pg_dump -U taskflow -d taskflow --format=custom --file=$tempArchive
    if ($LASTEXITCODE -ne 0) { throw 'Backup failed.' }
    docker exec $pg pg_restore --file=/dev/null $tempArchive
    if ($LASTEXITCODE -ne 0) { throw 'Archive verification failed.' }
    docker cp "${pg}:$tempArchive" $archive
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the verified archive.' }
}
finally {
    docker exec $pg rm -f -- $tempArchive
    docker exec $pg rmdir -- $tempDir
}
```

The archive contains task data and application password hashes. Store it in the private backup directory and use it only for recovery. [PostgreSQL backup documentation](https://www.postgresql.org/docs/16/app-pgdump.html)

#### Rotate the role password after approval

Generate a unique strong password in a password manager. Use the existing container and the same value in the PostgreSQL and launcher prompts. The `\password` command prompts securely and keeps the cleartext value out of SQL history. [PostgreSQL psql documentation](https://www.postgresql.org/docs/16/app-psql.html)

```powershell
docker exec -it $pg psql -X -U taskflow -d taskflow
```

At the psql prompt, run the following and enter the new password twice.

```text
\password
\q
```

Test a TCP connection using the new password. Then save that password privately and start TaskFlow with the new Compose configuration.

```powershell
docker exec -it $pg psql -X -h 127.0.0.1 -U taskflow -d taskflow -W -v ON_ERROR_STOP=1 -c 'SELECT 1'
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL authentication failed.' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TaskFlow.ps1 -InitializeOnly -ConfigureDatabasePassword
if ($LASTEXITCODE -ne 0) { throw 'The local password was not saved.' }
Remove-Item Env:\DATABASE_PASSWORD -ErrorAction SilentlyContinue
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TaskFlow.ps1
```

Compose can recreate the PostgreSQL container to apply the new port binding and reattaches the existing `pgdata` volume. The container's `POSTGRES_PASSWORD` setting takes effect during initial database creation. The `\password` step is what changes the existing role. In another terminal, verify that the binding displays `127.0.0.1:5432` and the volume name is still `taskflow_pgdata`.

```powershell
docker ps --filter 'label=com.docker.compose.project=taskflow' --filter 'label=com.docker.compose.service=postgres' --format '{{.Ports}}'
$containers = @(docker ps --filter 'label=com.docker.compose.project=taskflow' --filter 'label=com.docker.compose.service=postgres' --format '{{.ID}}')
if ($LASTEXITCODE -ne 0 -or $containers.Count -ne 1) { throw 'Expected one running TaskFlow PostgreSQL container.' }
docker inspect --format '{{range .Mounts}}{{.Name}} {{.Destination}}{{end}}' $containers[0]
```

[Compose container replacement](https://docs.docker.com/reference/cli/docker/compose/up/) · [PostgreSQL image configuration](https://hub.docker.com/_/postgres)

#### Recover if necessary

For a password mismatch, test the current PostgreSQL password and correct the local entry with the masked launcher prompt. If a credential rollback is needed, use `\password` again to restore the previous PostgreSQL password, then save that same value locally. A password-only rollback needs no data restore.

If data recovery is needed, select the verified `$archive` and restore it to a separate recovery database. Use a separate PostgreSQL 16 instance if the original cluster is unavailable. The following example uses the running container and a unique recovery database. Set `$pg` to its current container ID before running it.

```powershell
if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) { throw 'Select a verified backup archive first.' }
$recovery = 'taskflow_restore_' + (Get-Date -Format 'yyyyMMddHHmmssfff')
$restoreDir = (docker exec $pg mktemp -d).Trim()
if ($LASTEXITCODE -ne 0 -or $restoreDir -notmatch '^/tmp/[^/]+$') { throw 'Could not create the private restore directory.' }
$restoreArchive = "$restoreDir/taskflow.pgdump"
try {
    docker cp $archive "${pg}:$restoreArchive"
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the archive.' }
    docker exec $pg createdb -U taskflow $recovery
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the recovery database.' }
    docker exec $pg pg_restore -U taskflow --dbname=$recovery --exit-on-error --single-transaction $restoreArchive
    if ($LASTEXITCODE -ne 0) { throw 'The recovery restore failed.' }
    docker exec $pg psql -X -U taskflow -d $recovery -v ON_ERROR_STOP=1 -c 'SELECT (SELECT count(*) FROM public.task_user) AS accounts, (SELECT count(*) FROM public.task_item) AS tasks'
}
finally {
    docker exec $pg rm -f -- $restoreArchive
    docker exec $pg rmdir -- $restoreDir
}
```

Verify the restored counts and application behavior before considering a cutover. This restore leaves the original database and volume in place. [PostgreSQL restore documentation](https://www.postgresql.org/docs/16/app-pgrestore.html)

### Existing demo tasks

Flyway runs on API startup and Hibernate validates the resulting schema. On a legacy database, V2 adds ownership and records eligible existing task IDs 2 through 7. To claim those tasks when registering the demo recipient recorded by V2, copy the configured code locally before filling in **Code d’attribution**.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TaskFlow.ps1 -InitializeOnly -CopyDemoClaimCode
```

The claim is recorded with account creation in a single transaction. Subsequent sign-ins use the email and password. Unowned tasks, including legacy task 8, are kept in PostgreSQL and excluded from the API.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `TASKFLOW_JWT_KEY` | Required signing key of at least 32 random bytes encoded as Base64 |
| `TASKFLOW_DEMO_CLAIM_CODE` | Optional code for the pending demo claim |
| `TASKFLOW_LOCAL_SECRETS` | Optional absolute path to a local secrets file |
| `TASKFLOW_FRONTEND_ORIGIN` | Allowed browser origin, defaulting to `http://localhost:4200` |
| `DATABASE_URL` | PostgreSQL JDBC URL, defaulting to the local Compose database |
| `DATABASE_USER` | PostgreSQL role, defaulting to the local Compose role |
| `DATABASE_PASSWORD` | Required PostgreSQL password for both Compose and Spring Boot. Loaded from the private file by the launcher |

Explicit environment variables take precedence over the local file during a normal launch. The [example file](backend/.env.example) lists the secret entries with blank values. For direct Maven startup, set `TASKFLOW_LOCAL_SECRETS` to the absolute path of the private file or supply the required environment variables. Use a file outside Git and OneDrive.

## API endpoints

The API base URL is `http://localhost:8080/api`.

| Method | Path | Result |
| --- | --- | --- |
| POST | `/auth/register` | 201 with `accessToken`, `expiresAt`, and `email` |
| POST | `/auth/login` | 200 with `accessToken`, `expiresAt`, and `email` |
| GET | `/tasks` | 200 with the signed-in user's tasks |
| GET | `/tasks/{id}` | 200 with a task owned by the signed-in user |
| POST | `/tasks` | 201 with the created task |
| PUT | `/tasks/{id}` | 200 with the updated task |
| DELETE | `/tasks/{id}` | 204 with an empty body |

Authentication accepts `email` and `password`. Registration also accepts `claimCode` for the pending demo claim. Task requests require an `Authorization: Bearer <accessToken>` header.

A task needs a non-blank `title` of at most 120 characters and can have a `description` of at most 2,000 characters. Its `status` is `TODO`, `IN_PROGRESS`, or `DONE`. Omitting the status on a write defaults it to `TODO`.

Errors contain `status`, `message`, and a per-field `errors` map. Invalid input returns 400. Authentication failures return 401, denied access and invalid demo codes return 403, unavailable tasks return 404, and duplicate email addresses return 409.

## Running tests

```powershell
cd backend
mvn clean test
```

The suite covers authentication, JWT validation, task CRUD, input errors, isolation between users, and the one-time demo claim. It runs against an isolated in-memory test database.

From the repository root, build the frontend after installing its dependencies.

```powershell
cd frontend
npm.cmd run build
```

The production bundle is in `frontend/dist/taskflow/browser/`.

## Security

Passwords are hashed with BCrypt. The API is stateless and validates signed, 30-minute JWTs. Angular keeps the token in memory. Logging out or refreshing clears the browser session.

The task repository scopes reads, updates, and deletes by the authenticated user's ID. An unowned task and a task belonging to another account both return 404. Store JWT keys, demo claim codes, and database passwords in the private local file or in environment variables.

For future commits, copy the private address from GitHub **Settings → Emails** and set it for this repository with `git config --local user.email "<GitHub noreply address>"`. This setting applies only to new commits. [GitHub email settings](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address)
