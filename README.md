# Lucee Mail Extension

With Lucee 7.1, mail functionaility has been moved from core into this extension

Docs:
- https://docs.lucee.org/reference/tags/mail.html
- https://docs.lucee.org/reference/tags/pop.html
- https://docs.lucee.org/reference/tags/pop.html

Issues: https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20smtp

## Versioning and releases

The version is not edited by hand. `pom.xml` uses a CI-friendly `${revision}` built from
`extension.version.base` (e.g. `1.1.0`), a build number and `extension.version.qualifier` (e.g. `-RC`).

* Local, PR and normal pushes use build number `0` (e.g. `1.1.0.0-RC`) and publish nothing.
* A release build (push to `main` with `[release]` in the commit message, or a manual run with `deploy` checked)
  takes the highest existing build of the base, from the git tags and from Maven Central, adds 1,
  publishes that version (e.g. `1.1.0.12-RC`) and creates the tag `1.1.0.12`.
* A `-SNAPSHOT` qualifier publishes to the Central snapshot repository, any other qualifier (or none) publishes a release.
* To start a new line change `extension.version.base`; for a stable line set `extension.version.qualifier` to empty.
