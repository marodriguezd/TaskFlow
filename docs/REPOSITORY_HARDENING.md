# Repository administration recommendations

These controls require GitHub repository administration and cannot be enforced by files in this repository.

## Protect `main`

Configure a branch ruleset (or branch protection rule) for `main` with:

- Require pull requests before merging; require at least one approval where team size permits.
- Require the `Build & Test` status check from the CI workflow and require the branch to be up to date before merging.
- Require conversation resolution and block force pushes and branch deletion.
- Require signed commits only if contributors can reliably sign commits; do not rewrite existing history to retrofit signatures.
- Limit bypass permissions to trusted maintainers and keep release/tag permissions restricted.

The release tag `v1.1.1` and its history are published artifacts; never amend or retag them as part of routine hardening.

## Commit signature hygiene

The public GitHub commit API currently reports the recent `main` commits as unsigned. This is a repository-hygiene observation, not evidence that the commits or code are untrusted. Preserve existing history; enable signing for future commits if desired and apply any signed-commit enforcement prospectively.
