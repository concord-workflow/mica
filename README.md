# mica

An inventory system for ck8s.

## Prerequisites

- JDK 17

## Building

```
./mvnw clean install -DskipTests
```

The resulting JAR `mica-concord-server-plugin/target/mica-concord-server-plugin-*.jar`
can be added directly to the classpath of concord-server.

## Running in IDE

Start `ca.ibodrov.mica.its.TestingMicaServer` with the following environment variables:
- `TEST_OIDC_AUTHSERVER` - the OIDC server URL, for example `https://dev-12345678.okta.com`;
- `TEST_OIDC_CLIENTID` - the OIDC client ID;
- `TEST_OIDC_SECRET` - the OIDC client secret.

You should be able to access the UI by visiting http://localhost:8001/mica

## UI Development

```
cd mica-ui
npm run dev
```

See package.json for other actions.

## Development Notes

- re-build the database module to let `mica-concord-server-plugin` use the updated schema:
  ``` 
  ./mvnw -pl :mica-db clean install
  ```
