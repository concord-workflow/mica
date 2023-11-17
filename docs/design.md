# Mica

## Model

- `Client` -- an external company, client of Acme Corp.
  - `id` -- uuid, internal ID;
  - `name` -- string, "external" human-readable Client ID.

- `Client Data` -- read-only data, associated with client. Updated
  periodically by an external process. E.g. status, business name, VPCs, etc.
  - `documentId` -- uuid, internal ID;
  - `externalId` -- string, "external" human-readable Client ID;
  - `kind` -- string, type of the document. Mostly for versioning purposes;
  - `parsedData` -- JSON, client properties.

- `Client Profile` -- defines a set of properties for a given client.
  - `id` -- uuid, internal ID;
  - `name` -- string;
  - `kind` -- type of the profile. Mostly for versioning purposes;
  - `schema` -- JSON schema, defines the set of properties.
