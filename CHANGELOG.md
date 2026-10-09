# Changelog

## Unreleased

- [LDEV-4258](https://luceeserver.atlassian.net/browse/LDEV-4258) — include every `cfmailpart` of the same type (previously only the first text/plain or text/html part was sent)
- [LDEV-6545](https://luceeserver.atlassian.net/browse/LDEV-6545) — send HTML mail parts `quoted-printable` by default again (LDEV-5176 had flipped the default to `7bit`, so long HTML lines were split at 998 characters); `lucee.mail.use.7bit.transfer.encoding.for.html.parts=true` still opts in to `7bit`, but falls back to `quoted-printable` for a part whose lines cannot be wrapped to 998 characters; extra `text/html` parts are now recognised regardless of case and as `html`/`htm`
- [LDEV-4258](https://luceeserver.atlassian.net/browse/LDEV-4258) — fix a second `cfmailpart` with a short type (`html`, `htm`, `text`, `plain`) failing the whole send; extra html and text parts are now sent as `text/html` / `text/plain` with their charset

## 1.1.0.12

- [LDEV-766](https://luceeserver.atlassian.net/browse/LDEV-766) — honour `useTLS`/`starttls` even when no SMTP username is set; also set `mail.smtp.starttls.required` so jakarta.mail does not fall back to plain text when STARTTLS fails

## 1.1.0.9

- [LDEV-6485](https://luceeserver.atlassian.net/browse/LDEV-6485) — wrap Session/Transport creation in the extension's own classloader as TCCL so jakarta.mail ServiceLoader does not pick up javax.mail providers; exclude transitive jakarta.mail 2.0.1 from commons-email2-jakarta

## 1.1.0.8

- [LDEV-6455](https://luceeserver.atlassian.net/browse/LDEV-6455) — fix spooled cfmail silently dropped (`NotSerializableException: sun.nio.cs.UTF_8`); charset fields on `SMTPClient` are now held via a serializable wrapper

## 1.1.0.6

- [LDEV-5893](https://luceeserver.atlassian.net/browse/LDEV-5893) — stop re-enabling deprecated TLS protocols on SMTP STARTTLS path

## 1.1.0.3-RC

- [LDEV-6093](https://luceeserver.atlassian.net/browse/LDEV-6093) — auto-bundle parent POMs to make extension fully self-contained

## 1.1.0.1-RC

- Add GAV (groupId/artifactId/version) metadata

## 1.1.0.1-BETA

- Update README

## 1.1.0.1-ALPHA

- Update libraries

## 1.1.0.0-ALPHA

- [LDEV-5674](https://luceeserver.atlassian.net/browse/LDEV-5674) — switch from javax to jakarta

## 1.0.0.18-SNAPSHOT

- Remove workaround after fix in Lucee
- Add additional test cases
- Improve clean method

## 1.0.0.17-BETA

- Get ServerImpl from the core
- Retry on stale connection [EOF] errors
- Add local test with reuseConnection=false
- Fix test cases
- Add missing test case assets
- Update classes used
- Define Lucee admin password
- Switch to Maven build
- Add GitHub Actions CI

## 1.0.0.0-ALPHA

- Initial commit
