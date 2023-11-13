# Mica

## Implementation Notes

Mica can be run separately or as a part of
the [concord-server](https://github.com/walmartlabs/concord/tree/master/server).

The standalone mode requires a bit of a setup, see the example in `org.acme.mica.server.LocalServer`.

## Model

- `Client` -- an external company, client of Acme Corp.
  - `id` -- uuid, internal ID;
  - `name` -- string, "external" human-readable Client ID.

- `Client Data` -- read-only data, associated with client. Updated
  periodically by an external process. E.g. status, business name, VPCs, etc.
  - `documentId` -- uuid, internal ID;
  - `externalId` -- string, "external" human-readable Client ID;
  - `parsedData` -- JSON, client properties.

## APIs

Enumerate clients:

```
# get all clients
GET /api/mica/v1/client

# search
GET /api/mica/v1/client?search=foobar

# search and return extra properties from the latest client data
GET /api/mica/v1/client?search=foobar&props=some_prop&props=...
```

Read client data:

```
# upload client data
POST /api/mica/v1/clientData/import

# fetch the latest published data
GET /api/mica/v1/clientData/latest?externalId=...
```
