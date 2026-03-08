
# The cyclist diary article generator

> Transform a GitHub issue into a nice article

This repository contains a GitHub Action powered by [Quarkus GitHub Action](https://github.com/quarkiverse/quarkus-github-action).

When pushing to the `main` branch, the GitHub Action artifact is automatically published to the Maven repository of this GitHub repository.

The `action.yml` descriptor instructs GitHub Actions to run this published artifact using JBang when the action is executed.

## Usage

Add this action to your workflow to generate articles from GitHub issues and convert GPX tracks to polylines.

Two versions are available:

### JBang version (default)

Uses JBang to run the action:

```yaml
- name: Generate cyclist diary content
  uses: the-cyclist-diary/the-cyclist-diary-editor@main
  with:
    github-token: ${{ secrets.GITHUB_TOKEN }}
    github-username: ${{ github.repository_owner }}
    content-path: content/posts
    full-scan: 'false'
```

### Docker version

Uses a pre-built Docker image:

```yaml
- name: Generate cyclist diary content
  uses: the-cyclist-diary/the-cyclist-diary-editor/action.docker.yml@main
  with:
    github-token: ${{ secrets.GITHUB_TOKEN }}
    github-username: ${{ github.repository_owner }}
    content-path: content/posts
    full-scan: 'false'
```

### Parameters

- `github-token` (required): GitHub token for API access
- `github-username` (required): GitHub username
- `content-path` (optional): Path to the content folder where maps will be generated
- `full-scan` (optional): Set to `'true'` to generate polylines for ALL GPX files, not just modified ones (default: `'false'`)
- `action` (optional): Name of the action if named

## CLI Mode - Generate Polylines Locally

This project also includes a CLI mode for generating polylines from GPX files locally, for testing before deployment.

### Documentation

- **[CLI-USAGE.md](CLI-USAGE.md)** - Detailed CLI documentation
