# API Documentation

Stacc uses OpenAPI to describe its REST APIs. During development, the library-provided Swagger UI can be used to view and test documented endpoints.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Future controllers will appear automatically in the generated documentation. Request and response DTOs should have clear, useful descriptions when additional documentation is needed.

Swagger provides documentation and development-time testing support. It does not replace backend authentication or authorization checks.
