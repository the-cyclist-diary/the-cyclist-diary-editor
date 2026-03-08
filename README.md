
# The cyclist diary article generator

> Transform a GitHub issue into a nice article

This repository contains a GitHub Action powered by [Quarkus GitHub Action](https://github.com/quarkiverse/quarkus-github-action).

When pushing to the `main` branch, the GitHub Action artifact is automatically published to the Maven repository of this GitHub repository.

The `action.yml` descriptor instructs GitHub Actions to run this published artifact using JBang when the action is executed.

## CLI Mode - Generate Polylines Locally

This project also includes a CLI mode for generating polylines from GPX files locally, for testing before deployment.

### Documentation

- **[CLI-USAGE.md](CLI-USAGE.md)** - Detailed CLI documentation
