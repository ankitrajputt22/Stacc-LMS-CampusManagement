# Stacc Backend

The Stacc backend uses Java 21, Spring Boot, and Maven. It follows modular-monolith principles so that future business domains remain clearly separated within one application.

## MySQL connection

The backend includes Spring Data JPA and MySQL Connector/J. Its connection configuration reads these environment variables:

- `MYSQL_HOST` (defaults to `localhost`)
- `MYSQL_PORT` (defaults to `3306`)
- `MYSQL_DATABASE` (defaults to `stacc`)
- `MYSQL_USER`
- `MYSQL_PASSWORD`

Set the username and password in the local environment before running the application. Never commit real credentials.

Hibernate schema management is disabled. Flyway will begin managing database changes in Phase 7; this phase contains no application tables or migrations.

Run the backend tests with:

```bash
mvn test
```
