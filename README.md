# Unifica App

This is a POC for using Keycloak to replicate Biff's auth.

The intent is to use as much of Keycloak as possible

- admin API
- extensions in Clojure
- themes in Clojure + a Biff-like as possible

## Docker

```
docker compose up -d
```

Then got o localhost:8090 to see the Keycloak configuration and log on.

## Local

Install Clojure.

Run `clj -M:dev dev` to get started. See `clj -M:dev --help` for other commands.

Consider adding `alias biff='clj -M:dev'` to your `.bashrc`.


