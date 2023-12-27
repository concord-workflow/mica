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

The entity's `kind` is a reference to a `/mica/kind/v1` entity that provides
the schema. For example, a `CorporateCustomer` entity may look like this:

```yaml
name: /clients/AcmeCorp
kind: /schemas/AcmeClient
details:
  id: acme
  validationUrl: https://acme.example.com/validate
```

Which is enforced by the schema stored in a separate `Entity`:

```yaml
name: /schemas/AcmeClient
kind: /mica/kind/v1
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
- `/mica/record/v1` -- basic data record, no attached behaviors;
- `/mica/kind/v1` -- a `kind` definition, aka entity "template";
- `/mica/view/v1` -- entity view object;
- `/mica/transformer/v1` -- an entity transformer (aka data migration).

## Views

Use Mica Views to create projections of data.

For example, given a couple of entities like so:

```yaml
name: /clients/20240101
kind: /schemas/AcmeClientList
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
name: /clients/20230101
kind: /schemas/AcmeClientList
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
name: /schemas/AcmeClientList
kind: /mica/kind/v1
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
kind: /mica/view/v1
name: /views/ActiveClients
selector:
  entityKind: /schemas/AcmeClientList
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

When a view is "rendered", first the `selector` is applied to find entities to
return. Currently, the following selectors are supported:
- `entityKind` -- selects entities of a given kind. Mandatory value, must be a
  path to a `/mica/kind/v1` entity, e.g. `/mica/record/v1`;
- `namePatterns` -- optional list of regular expressions to match entity names
  against. If not specified, all entities of the given kind are selected.

When `namePatterns` is specified, the view returns entities in the order in
which they match the patterns. The entities that match the first pattern will
be grouped first, then the entities that match the second pattern, and so on. 

The `data` object is a JSON array where each element corresponds to a selected
entity.

The resulting data is further processed by applying one or more of the optional
steps (in the order in which they are applied):
- `flatten` -- joins array of arrays of objects into a regular flat array of objects;
- `merge` -- merges multiple objects into one by deep-merging fields;
- `jsonPatch` -- applies a JSON Patch to each object.

## View Flattening

When returning multiple fields per entity, normally the result is a JSON array
of arrays. To flatten the result, use the `flatten` option:

```yaml
kind: /mica/view/v1
name: /views/ActiveClients
selector:
  entityKind: /schemas/AcmeClientList
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

## Merge Results

View data can be merged into a single JSON object using the `merge` option:

```yaml
kind: /mica/view/v1
name: /views/PiecesCombined
selector:
  entityKind: /schemas/Piece
data:
  jsonPath: $
  merge: true
```

Given an entity

```yaml
kind: /schemas/Piece
name: /puzzle/piece-a
foos: ['a', 'b', 'c']
bars:
  baz:
    qux: 123
```

and

```yaml
kind: /schemas/Piece
name: /puzzle/piece-b
foos: ['x', 'y', 'z']
bars:
  eek: true
```

the rendered view will contain the object with keys merged from both entities:

```json
{
    "data": [
        {
            "foos": ["x", "y", "z"],
            "bars": {
                "eek": true,
                "baz": {
                    "qux": 123
                }
            }
        }
    ]
}
```

## JSON Patch Support

Views can apply JSON patch commands to each object in the result set:

```yaml
kind: /mica/view/v1
name: /views/ActiveClients
selector:
  entityKind: /schemas/AcmeClientList
data:
  jsonPath: $.clients[?(@.status=='active')].["id", "validationUrl"]
  flatten: true
  jsonPatch:
    - op: add
      path: /status
      value: valid
```

Given entities from [the example above](#views), the result is:

```json
{
    "data": [
        {
            "id": "foo",
            "validationUrl": "http://foo.example.org",
            "status": "valid"
        },
        {
            "id": "baz",
            "validationUrl": "http://baz.example.org",
            "status": "valid"
        },
        {
            "id": "qux",
            "validationUrl": "http://qux.example.org",
            "status": "valid"
        }
    ]
}
```

The view selects all active clients, picks their `id` and `validationUrl`
properties, flattens the result (so it a simple list of client entries instead
of a list of lists) and adds the `status` field to each object.

## Parametrized Views

Views can declare parameters:

```yaml
kind: /mica/view/v1
name: /views/ActiveClients
parameters:
  clientId:
    type: string
selector:
  entityKind: /schemas/AcmeClientList
data:
  jsonPath: $.clients[?(@.id==$clientId)].["id", "validationUrl"]
  flatten: true
```

The `$syntax` is used to reference parameters in the `jsonPath` expression.
Currently, any JSON value can be used as a parameter. Currently, only primitive
types are supported.

To pass the parameters, use the `parameters` field in the request body:

```
curl -i --json '{"viewName": "/views/ActiveClients", "limit": 10, "parameters": {"clientId": "foo"}}' 'http://localhost:8080/api/mica/v1/view/render'
```

## Data Includes

```yaml
kind: /mica/view/v1
name: /views/effective-config
parameters:
  commitId:
    type: string
selector:
  entityKind: /mica/record/v1
  namePatterns:
    - /stuff/configs
data:
  includes:
    - concord+git://projectName/repoName?path=/stuff/configs&commitId=${parameters.commitId}
```

The `includes` field is a list of URLs to fetch data from.  Only `concord+git`
URL scheme is supported at the moment.

The `concord+git` URL must point at existing Concord project `projectName`
with the repository `repoName`. When rendering the view, Mica will fetch
the contents of the repository at the given `commitId` and look for YAML files
in the given `path`. YAML files with the `kind` field set to
`selector.entityKind` will be considered for further processing, the rest will
be ignored.

## Materialize View Data As Entities

_This section is a work in progress._

Mica provides an option to save (materialize) the rendered view data as entities:

```
curl -i --json '{"viewName": "/examples/materialize/v1-to-v2"}' 'http://localhost:8080/api/mica/v1/view/materialize'
```

The endpoint accepts the same parameters as the `render` endpoint.

The view must return a JSON array of objects in `data`. Each object in
the `data` array is saved as a separate entity. Each resulting entity must
pass the validation according to its `kind` before it can be saved.

For example, given an existing kind `MyRecord/v1`:

```yaml
name: /examples/materialize/MyRecord/v1
kind: /mica/kind/v1
schema:
  properties:
    name:
      type: string
    validationUrl:
      type: string
  required: ['name', 'validationUrl']
```

And a set of entities:

```yaml
name: /examples/materialize/Foo
kind: /examples/materialize/MyRecord/v1
validationUrl: "http://foo.example.org"
```

```yaml
name: /examples/materialize/Bar
kind: /examples/materialize/MyRecord/v1
validationUrl: "http://bar.example.org"
```

Let's create a view to migrate the entities to `MyRecord/v2`, which
introduces an additional property `status` and is defined as follows:

```yaml
name: /examples/materialize/MyRecord/v2
kind: /mica/kind/v1
schema:
  properties:
    name:
      type: string
    status:
      type: string
      enum: [active, retired]
    validationUrl:
      type: string
  required: ['name', 'status', 'validationUrl']
```

A view definition:

```yaml
name: /examples/materialize/v1-to-v2
kind: /mica/view/v1
selector:
  entityKind: /examples/materialize/MyRecord/v1
data:
  jsonPath: $
  jsonPatch:
    - op: add
      path: /status
      value: "active"
    - op: replace
      path: /kind
      value: /examples/materialize/MyRecord/v2
```

Calling the `migration` endpoints renders and saves the following data:

```json
{
    "data": [
        {
            "name": "/examples/materialize/MyRecord/Foo",
            "kind": "/MyRecord/v2",
            "status": "active",
            "validationUrl": "http://foo.example.org"
        },
        {
            "name": "/examples/materialize/MyRecord/Bar",
            "kind": "/MyRecord/v2",
            "status": "active",
            "validationUrl": "http://bar.example.org"
        }
    ]
}
```

## Validate View Entities

_This section is a work in progress._

Mica provides an option to validate the rendered view data as entities.
Using the example above, the view definition would look like this:

```yaml
name: /examples/materialize/v1-to-v2
kind: /mica/view/v1
selector:
  entityKind: /examples/materialize/MyRecord/v1
data:
  jsonPath: $
  jsonPatch:
    - op: add
      path: /status
      value: "active"
    - op: add
      path: /foo
      value: "bar"
    - op: replace
      path: /kind
      value: /examples/materialize/MyRecord/v2
validation:
  asEntityKind: /examples/materialize/MyRecord/v2
```

The `validation.asEntityKind` field references a /mica/kind/v1 schema to use
for validation. The schema is applied to each entity in `data`. Validation
results are returned in a separate field:

```json
{
  "name":"/examples/materialize/v1-to-v2",
  "kind":"MicaMaterializedView/v1",
  "length":2,
  "data": [
    {...cut...,"validationUrl":"http://foo.example.org","foo":123,"status":"active"},
    {...cut...,"validationUrl":"http://bar.example.org","foo":123,"status":"active"}
  ],
  "validation": [
    {"error":{"kind":"UNEXPECTED_VALUE","metadata":{"details":"Additional properties are not allowed: [foo]","propertyNames":["foo"]}}},
    {"error":{"kind":"UNEXPECTED_VALUE","metadata":{"details":"Additional properties are not allowed: [foo]","propertyNames":["foo"]}}}
  ]
}
```

In this example, we changed /examples/materialize/v1-to-v2 to disallow any
extra properties (by adding `additionalProperties: false`). As the result, the
validation fails for both entities.

## Entity Validation

To validate an entity of kind `K`, Mica looks up the `/mica/kind/v1` entity
with name `K`. An entity cannot be created or updated if the schema is not
found.

## Supported JSON Schema Features

Mica implements a subset of JSON Schema features:

- `type`: array, boolean, object, string, number, null, any (see `ca.ibodrov.mica.schema.ValueType`);
- `required` properties;
- `enum` values;
- array `items`;
- `additionalProperties` option (`true`, `false` or a schema object);
- `$ref`
- _TODO_ format: uri, email, uuid, date, time, etc;

The major difference from the standard is that Mica does not support `null`
values. When validating `required` properties, Mica treats `null` values as
"missing" properties.

## Database Design

Entities are stored in a single table `MICA_ENTITIES`.
Normally, the backend deserializes the rows as
`ca.ibodrov.mica.api.model.Entity` with straightforward mapping, where
`MICA_ENTITIES.DATA` is interpreted as "the rest of the properties".
