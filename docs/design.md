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

## Use Cases

### Enforcing Client Data Schema

Client profiles can be used to enforce a certain schema to the client data.

### Verification of Remote Client Endpoints

Given a client profile:

```yaml
name: "remote-client"
kind: "MicaProfile/1.0"
schema:
  required:
    - validationEndpoint
  properties:
      validationEndpoint:
        type: string
        format: uri      
```

One can make a Concord flow that fetches all `validationEndpoint` values (using
an imaginary, for now, Concord task `mica`):

```yaml
- task: mica
  in:
    action: listClients
    props:
      - validationEndpoint
  out: clients
```

And runs some form of validation for each client:

```yaml
- task: validateClient
  in:
    endpoint: ${item.properties.validationEndpoint}
  loop:
    items: ${clients}
```
