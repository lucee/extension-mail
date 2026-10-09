# Lucee Mail Extension

With Lucee 7.1, mail functionaility has been moved from core into this extension

Docs:
- https://docs.lucee.org/reference/tags/mail.html
- https://docs.lucee.org/reference/tags/pop.html
- https://docs.lucee.org/reference/tags/pop.html

Issues: https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20smtp

## Versioning and releases

Works like Lucee core (LDEV-6516): every code change gets its own version number. The version is not edited by hand.
`pom.xml` uses a CI-friendly `${revision}` built from `extension.version.base` (`1.1.0`), a build number and
`extension.version.qualifier` (`-SNAPSHOT`).

* **Every push to `main`** (a merged PR or a direct commit) takes the highest existing build of the base, from the git tags,
  Maven Central and the Central snapshot repository, adds 1, builds, tests, publishes that version (e.g. `1.1.0.12-SNAPSHOT`)
  to the [Central snapshot repository](https://central.sonatype.com/repository/maven-snapshots/) and creates the tag `1.1.0.12`.
  No `[release]` keyword and no "set version" commit is needed.
* Pushes run one after another (workflow concurrency queue), so two quick merges get two different numbers.
* PR builds, forks and runs without the deploy secrets build and test `1.1.0.0-SNAPSHOT` and never use up a number.
  A commit with `[skip ci]` in its message does not start a build, so it does not get a number either.
  Like Lucee core there is no exception for documentation-only changes: they get a new snapshot too.
* **Release candidate or final release**, either way gets the next number:
  * like Lucee core, commit a change of `extension.version.qualifier` in `pom.xml` to `-RC` (release candidate) or to empty
    (final release). That push publishes e.g. `1.1.0.13-RC` to Maven Central. Afterwards commit `-SNAPSHOT` back
    ("new cycle"), otherwise every following push publishes another RC.
  * or run the workflow by hand on `main` (Actions > Run workflow), tick `deploy` and pick `RC`, `BETA`, `ALPHA` or `release`
    as qualifier. That publishes the current code once, without a commit; the pom keeps `-SNAPSHOT`.
* Central keeps snapshots for a limited time only; the git tags keep the numbers unique after a snapshot was removed.
* To start a new line change `extension.version.base`.
