# API Error Handling

Stacc APIs use one common error format. Validation failures and malformed request bodies return HTTP 400. Field validation errors include only the affected `field` and a safe `message`.

For example:

```json
{
  "timestamp": "2026-09-25T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/example",
  "fieldErrors": [
    {
      "field": "name",
      "message": "Name is required"
    }
  ]
}
```

Unexpected server errors return a safe HTTP 500 response and must not expose stack traces, database details, secrets, or internal paths. Future APIs should reuse the shared handler, and request DTOs should use Jakarta Validation annotations where needed.
