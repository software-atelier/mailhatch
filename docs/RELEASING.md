# Releasing to Maven Central

MailHatch publishes from GitHub Actions through the Sonatype Central Portal. A
published GitHub release starts `.github/workflows/publish.yml`; the workflow can
also be started manually for an existing tag.

## One-time setup

1. In the [Central Portal](https://central.sonatype.com/), verify ownership of the
   `ch.softwareatelier` namespace.
2. Create a Central Portal user token. Its generated username and password are
   separate values; legacy OSSRH credentials do not work with this workflow.
3. Create a password-protected OpenPGP signing key for releases and publish its
   public key to a supported public keyserver.
4. In the GitHub repository, create an environment named `maven-central`.
5. Add these environment secrets:

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_USERNAME` | Username from the Central Portal user token |
   | `MAVEN_CENTRAL_TOKEN` | Password/token from the Central Portal user token |
   | `GPG_PRIVATE_KEY` | Complete ASCII-armored private signing key |
   | `GPG_PASSPHRASE` | Passphrase of that signing key |

Never commit or paste these values into an issue, pull request, or workflow file.
With GitHub CLI installed and authenticated, a secret can be entered without
placing it in shell history, for example:

```bash
gh secret set MAVEN_CENTRAL_USERNAME --env maven-central
gh secret set MAVEN_CENTRAL_TOKEN --env maven-central
gh secret set GPG_PRIVATE_KEY --env maven-central < private-key.asc
gh secret set GPG_PASSPHRASE --env maven-central
```

## Release procedure

1. Set a non-SNAPSHOT version in `pom.xml` and merge the release commit.
2. Create and push an annotated tag with exactly the same version prefixed by
   `v`, for example `v0.3.1`.
3. Create and publish the GitHub release from that tag.
4. Check the GitHub Actions run and the deployment in the Central Portal.
5. Confirm the version is visible on Maven Central before announcing it.

The workflow refuses SNAPSHOT versions, refs that are not real Git tags, tags
that do not point at the checked-out commit, and tags that do not equal the POM
version prefixed with `v`.

Central releases are immutable. If a workflow reports a timeout or network error,
check the Central Portal and Maven Central before retrying. If the version was
already published, prepare a new patch version instead of rerunning the same
release.
