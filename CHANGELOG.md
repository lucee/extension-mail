# Changelog

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
