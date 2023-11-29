# Mica

## Core Model

The primary concept is `Entity` -- a JSON object, validated using rules of the
declared `kind`:

```text
Entity
  id:           uuid, internal ID
  name          string, URI path element
  kind          string, URI path element
  createdAt     timestamp;
  updatedAt     timestamp
  *             other arbitrary keys
```

The entity's `kind` is a reference to a `MicaKind/v1` entity that provides
the schema. For example, a `CorporateCustomer` entity may look like this:

```json
{
  "name": "AcmeCorp",
  "kind": "CorporateCustomer",
  "details": {
    "displayName": "Acme Corp",
    "validationUrl": "https://acme.example.com/validate"
  }
}
```

Which is enforced by the schema stored in a separate `Entity`:

```json
{
    "name": "CorporateCustomer",
    "kind": "MicaKind/v1",
    "schema": {
        "properties": {
            "displayName": {
                "type": "string",
                "required": true
            },
            "validationUrl": {
                "type": "string",
                "required": true
            }
        }
    }
}
```

Mica is enforcing schemas every time an entity is created or updated. See
the validation section below.

The schema defines the keys of the entity, their types, and whether they are
required. The schema object itself is a recursive type:

```text
ObjectSchemaNode:
  type           string
  properties     map of keys -> ObjectSchemaNode
  required       a subset of keys from "properties"
  enum           array of JSON values
  items          ObjectSchemaNode, schema of an array item
```

There are several built-in entity kinds:
- `MicaRecord/v1` -- basic data record, no attached behaviors;
- `MicaKind/v1` -- a `kind` definition, aka entity "template";
- `MicaView/v1` -- entity view object.

## Views

Use Mica Views to create projections of data.

For example, given an entity (a list of clients of Acme Corp):

```yaml
name: clients-20240101
kind: AcmeClient
clients:
  - id: foo
    status: active
    validationUrl: "http://foo.example.org"
  - id: bar
    status: retired
    validationUrl: "http://bar.example.org"
  - id: baz
    status: active
    validationUrl: "http://baz.example.org"
```

Plus a view definition:

```yaml
kind: MicaView/v1
name: ActiveClients
selector:
  entityKind: AcmeClient
data:
  jsonPath: $.clients[?(@.status='active')].["id", "validationUrl"]
```

Equals:

```
curl 'http://localhost:8080/api/mica/v1/view/ActiveClients/render'
```

```json
{
    "data": [
        {
            "id": "foo",
            "validationUrl": "http://foo.example.org"
        },
        {
            "id": "baz",
            "validationUrl": "http://baz.example.org"
        }
    ]
}
```

TODO:
- parameterized views

## Entity Validation

To validate an entity of kind `K`, Mica looks up the `MicaKind/v1` entity
with name `K`. An entity cannot be created or updated if the schema is not
found.

## Supported JSON Schema Features

Mica implements a subset of JSON Schema features:

- types: array, boolean, object, string, number, null, any (see `ca.ibodrov.mica.schema.ValueType`);
- required properties;
- enum values;
- _TODO_ format: uri, email, uuid, date, time, etc;

## Database Design

Entities are stored in a single table `MICA_ENTITIES`.
Normally, the backend deserializes the rows as
`ca.ibodrov.mica.api.model.Entity` with straightforward mapping, where
`MICA_ENTITIES.DATA` is interpreted as "the rest of the properties".
