# MyAAC quickstart files — upstream reference (NOT wired)

These three files are byte-copies of `canary/docker/quickstart/myaac/`
(`Dockerfile`, `entrypoint.sh`, `bootstrap.php`) and are kept for upstream
parity only.

**They are not part of the Canary Kotlin stack.** MyAAC 2.x is a PHP/MySQL
website (`pdo_mysql`, `mysql:` DSNs, `information_schema` queries) while this
port runs on **PostgreSQL 16**; MyAAC cannot read the Kotlin database, so the
website cannot be wired into `docker/docker-compose.yml`. The Kotlin quickstart
uses the native login/status server on port `7171` and has no website
component.

If you run an upstream C++ Canary deployment (MariaDB), these files build a
working MyAAC image with the repository `docker/` folder as build context:

```bash
docker build -f quickstart/myaac/Dockerfile -t canary-myaac .
```

See `docker/DOCKER.md` ("Upstream quickstart extras") for the full mapping of
what was and was not ported.
