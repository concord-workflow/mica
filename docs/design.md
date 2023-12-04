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
  updatedAt     timestamp;
  validatedAt   timestamp;
  status        enum: valid, invalid, unknown;
  *             other arbitrary keys
```

The entity's `kind` is a reference to a `MicaKind/v1` entity that provides
the schema. For example, a `CorporateCustomer` entity may look like this:

```yaml
name: AcmeCorp
kind: AcmeClient
details:
  id: acme
  validationUrl: https://acme.example.com/validate
```

Which is enforced by the schema stored in a separate `Entity`:

```yaml
name: AcmeClient
kind: MicaKind/v1
schema:
  properties:
    id:
      type: string
    status:
      type: string
      enum: [active, retired]
    validationUrl:
      type: string
  required: ['id', 'validationUrl']
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

For example, given a couple of entities like so:

```yaml
name: clients-20240101
kind: AcmeClientList
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

```yaml
name: clients-20230101
kind: AcmeClientList
clients:
  - id: qux
    status: active
    validationUrl: "http://qux.example.org"
  - id: eek
    status: retired
    validationUrl: "http://eek.example.org"
  - id: ack
    status: retired
    validationUrl: "http://ack.example.org"
```

Validated by the following schema:

```yaml
name: AcmeClientList
kind: MicaKind/v1
schema:
  properties:
    clients:
      type: array
      items:
        properties:
          id:
            type: string
          status:
            type: string
            enum: [active, retired]
          validationUrl:
            type: string
        required: ['id', 'validationUrl']
```

Plus a view definition:

```yaml
kind: MicaView/v1
name: ActiveClients
selector:
  entityKind: AcmeClientList
data:
  jsonPath: $.clients[?(@.status=='active')].["id", "validationUrl"]
```

Equals:

```
curl 'http://localhost:8080/api/mica/v1/view/render/ActiveClients'
```

```json
{
    "data": [
        [
            {
                "id": "foo",
                "validationUrl": "http://foo.example.org"
            },
            {
                "id": "baz",
                "validationUrl": "http://baz.example.org"
            }
        ],
        [
            {
                "id": "qux",
                "validationUrl": "http://qux.example.org"
            }
        ]
    ]
}
```

The `data` object is a JSON array where each element corresponds to a selected
entity.

## View Flattening

When returning multiple fields per entity, normally the result is a JSON array
of arrays. To flatten the result, use the `flatten` option:

```yaml
kind: MicaView/v1
name: ActiveClients
selector:
  entityKind: AcmeClientList
data:
  jsonPath: $.clients[?(@.status=='active')].["id", "validationUrl"]
  flatten: true
```

Using the example data from the previous section, the result is:

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
        },
        {
            "id": "qux",
            "validationUrl": "http://qux.example.org"
        }
    ]
}
```

Note that now the result is a JSON array of objects, not an array of arrays.

## Parametrized Views

Views can declare parameters:

```yaml
kind: MicaView/v1
name: ActiveClients
parameters:
  clientId:
    type: string
selector:
  entityKind: AcmeClientList
data:
  jsonPath: $.clients[?(@.id==$clientId)].["id", "validationUrl"]
  flatten: true
```

The `$syntax` is used to reference parameters in the `jsonPath` expression.
Currently, any JSON value can be used as a parameter. Currently, only primitive
types are supported.

To pass the parameters, use the `parameters` field in the request body:

```
curl -i --json '{"viewName": "ActiveClients", "limit": 10, "parameters": {"clientId": "foo"}}' 'http://localhost:8080/api/mica/v1/view/render'
```

## Entity Validation

To validate an entity of kind `K`, Mica looks up the `MicaKind/v1` entity
with name `K`. An entity cannot be created or updated if the schema is not
found.

_This section is a work in progress._

When the schema entity is updated, Mica re-validates all entities of that kind.
If validation fails, the entity is marked as invalid.

The re-validation is a background process which compares the schema's
`updatedAt` with the entity's `validatedAt`. If the schema is newer, the entity
is re-validated. If the entity changes in the meantime and its `validatedAt`
becomes newer than the schema's `updatedAt`, the entity is not re-validated.

Invalid entities are not returned by views unless specified explicitly.

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
