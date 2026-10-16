# Lucee Mail Extension

With Lucee 7.1, mail functionaility has been moved from core into this extension

Docs:
- https://docs.lucee.org/reference/tags/mail.html
- https://docs.lucee.org/reference/tags/pop.html
- https://docs.lucee.org/reference/tags/pop.html

Issues: https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20smtp

## Versioning and releases

The version lives in `pom.xml` as a `-SNAPSHOT` (e.g. `1.1.0.18-SNAPSHOT`), the same scheme as the Quartz extension.

* **Every push** builds, tests and publishes the `-SNAPSHOT` from `pom.xml` to the
  [Central snapshot repository](https://central.sonatype.com/repository/maven-snapshots/) and to S3, then a bump job
  commits the next `-SNAPSHOT` ("Set version to ..."), so every commit gets its own build number.
* **Pull requests** only build and test; they never publish.
* **Manual run** (Actions > Run workflow) with a type:
  * `release` publishes `X.Y.Z.N` (the pom version without `-SNAPSHOT`) to Maven Central and S3,
  * `rc`, `beta`, `alpha` publish `X.Y.Z.N-RC` / `-BETA` / `-ALPHA` (signed) to S3 only, never to Central,
  * after any of these the next `-SNAPSHOT` is committed;
  * `snapshot` deploys the current `-SNAPSHOT` again without a bump.
* To start a new line change the version in `pom.xml` by hand (e.g. `1.2.0.0-SNAPSHOT`).
