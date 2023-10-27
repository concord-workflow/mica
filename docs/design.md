# Mica

## Model

- `Client` -- an external company, client of Acme Corp.
  - `id` -- uuid, internal ID;
  - `name` -- string, "external" human-readable Client ID.

- `Client Data` -- read-only data, associated with client. Updated
  periodically by an external process. E.g. status, business name, VPCs, etc.
  - `documentId` -- uuid, internal ID;
  - `externalId` -- string, "external" human-readable Client ID;
  - `parsedData` -- JSON, client properties.

- `Profile` - a named collection of properties. Can inherit properties from
  other profiles.

- `Client Profile` -- read-write client data managed by Mica. E.g. client-level
  configuration overrides or custom tags.

## APIs

Enumerate clients:

```
# get all clients
GET /api/v1/client

# search
GET /api/v1/client?search=foobar

# search and return extra properties from the latest client data
GET /api/v1/client?search=foobar&props=some_prop&props=...
```

Read client data:

```
# upload client data
POST /api/v1/clientData/import
```

## UI

- Client management:
  - list, search
  - view data
  - manage client-level profiles
  - client groups
- Profiles
  - list, search
  - CRUD
- Profile Schemas
  - list, search
  - CRUD
