# MyAAC quickstart files (upstream component)

These three files are byte-copies of `canary/docker/quickstart/myaac/`
(`Dockerfile`, `entrypoint.sh`, `bootstrap.php`), wired into
`docker/docker-compose.yml` as the `myaac` service exactly like upstream.

MyAAC 2.x is a PHP/**MySQL** website (`pdo_mysql`, `mysql:` DSNs), so it reads
the stack's **`mariadb`** service — not the Kotlin game database, which stays
on PostgreSQL. The compose stack starts the MariaDB service for this component
and for `opentibiabr/login-server`; seed it with your game data (or the
upstream MariaDB dumps) if you want the website to show real accounts.

Build standalone with the `docker/` folder as context:

```bash
docker build -f quickstart/myaac/Dockerfile -t canary-myaac .
```

See `docker/DOCKER.md` ("Upstream quickstart components") for the full setup.
