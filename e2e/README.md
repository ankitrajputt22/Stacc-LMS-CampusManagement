# Stacc End-to-End Tests

Playwright journeys in a real browser against the whole Stacc stack in Docker, with the demo data.

```bash
npm ci
npx playwright install chromium
./stack.sh up
npm test
./stack.sh down
```

The stack runs as its own Compose project, `stacc-e2e`, on its own ports and volumes, with passwords generated outside the repository, so it never touches your normal `stacc` stack. See `../docs/testing.md`.
