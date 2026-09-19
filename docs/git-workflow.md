# Stacc Git Workflow

Stacc uses a simple branch structure to keep ongoing work separate from stable releases.

## Branches

- `main` contains stable, demo-ready versions of Stacc.
- `development` contains the latest integrated working version.
- Phase branches are temporary branches for one small development phase.

For example:

```text
main
  └── development
        └── phase-001-git-workflow
```

## Normal workflow

Each phase should follow this sequence:

```text
development
→ create a phase branch
→ do the phase work
→ test the changes
→ commit
→ push the phase branch
→ merge it into development
→ delete the completed phase branch
```

Merge `development` into `main` only when a stable milestone is ready. Do not merge every small phase directly into `main`.

## Commit messages

Use simple, natural, human-readable commit messages.

Good examples:

- `Set up the backend`
- `Set up the frontend`
- `Connect MySQL`
- `Add college login`
- `Add student profile`
- `Add course registration`
- `Fix login issue`
- `Improve the dashboard`

Avoid unnecessarily technical messages such as:

- `feat(auth): implement RBAC`
- `chore(repo): bootstrap workspace`

## Safety rules

- Do not use force push during normal development.
- Do not use `git reset --hard` unless it is specifically approved.
- Do not delete uncommitted user work.
- Do not rewrite shared history.
- Do not merge directly into `main` after every small phase.
- Check `git status` before starting a phase.
- Review `git diff` before committing.
- Run the relevant tests before merging code phases.
- Stop and report a merge conflict that cannot be safely resolved.
