# Mica

## ToC

- [Core Model](#core-model)
- [Views](#views)
    - [View Flattening](#view-flattening)
    - [Merge Results](#merge-results)
    - [Group By Key and Merge](#group-by-key-and-merge)
    - [JSON Patch Support](#json-patch-support)
    - [Parametrized Views](#parametrized-views)
    - [View Includes](#view-includes)
    - [Git Repository Support](#git-repository-support)
    - [Concord JSON Store Support](#concord-json-store-support)
    - [S3 Support](#s3-support)
    - [Property Files Support](#property-files-support)
    - [Materialize View Data As Entities](#materialize-view-data-as-entities)
    - [Validate View Entities](#validate-view-entities)
    - [Field Mapping](#field-mapping)
    - [Caching](#caching)
- [Dashboards](#dashboards)
- [Supported JSON Schema Features](#supported-json-schema-features)
- [Database Design](#database-design)

## Core Model

The primary concept is `Entity` -- a JSON object which shape depends on its
`kind`. Any entity contains the following fields:

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
the schema. For example, a `/schemas/AcmeClient` entity may look like this:

```yaml
name: /clients/AcmeCorp
kind: /schemas/AcmeClient
details:
  clientId: acme
  validationUrl: https://acme.example.com/validate
```

Which is enforced by the schema stored in a separate `Entity`:

```yaml
name: /schemas/AcmeClient
kind: /mica/kind/v1
schema:
  properties:
    clientId:
      type: string
    status:
      type: string
      enum: [ active, retired ]
    validationUrl:
      type: string
  required: [ 'id', 'validationUrl' ]
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
  - clientId: foo
    status: active
    validationUrl: "http://foo.example.org"
  - clientId: bar
    status: retired
    validationUrl: "http://bar.example.org"
  - clientId: baz
    status: active
    validationUrl: "http://baz.example.org"
```

```yaml
name: /clients/20230101
kind: /schemas/AcmeClientList
clients:
  - clientId: qux
    status: active
    validationUrl: "http://qux.example.org"
  - clientId: eek
    status: retired
    validationUrl: "http://eek.example.org"
  - clientId: ack
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
          clientId:
            type: string
          status:
            type: string
            enum: [ active, retired ]
          validationUrl:
            type: string
        required: [ 'id', 'validationUrl' ]
```

Plus a view definition:

```yaml
kind: /mica/view/v1
name: /views/ActiveClients
selector:
  entityKind: /schemas/AcmeClientList
data:
  jsonPath: $.clients[?(@.status=='active')].["clientId", "validationUrl"]
```

Equals:

```shell
curl 'http://localhost:8080/api/mica/v1/view/render/ActiveClients'
```

```json
{
    "data": [
        [
            {
                "clientId": "foo",
                "validationUrl": "http://foo.example.org"
            },
            {
                "clientId": "baz",
                "validationUrl": "http://baz.example.org"
            }
        ],
        [
            {
                "clientId": "qux",
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

- `jsonPath` -- applies a JSON Path expression to each entity;
- `flatten` -- joins array of arrays of objects into a regular flat array of objects;
- `mergeBy` -- merges entities by a key;
- `merge` -- merges multiple objects into one by deep-merging fields;
- `jsonPatch` -- applies a JSON Patch to each object;
- `dropProperties` -- removes specified properties from each object;
- `map` -- maps fields from the source to the target.

### JSON Path in Views

Mica views can use JSON Path expressions to select fields from entities. The
`jsonPath` field in the view definition specifies a single expression:

```yaml
kind: /mica/view/v1
name: /views/my-view
selector:
  ...
data:
  jsonPath: $.value
```

Or a list of expressions that are applied in the order they are specified:

```yaml
kind: /mica/view/v1
name: /views/my-view
selector:
  ...
data:
  jsonPath:
    - $.someObject # applied first
    - $.someNestedProperty # applied to the result of the previous expression
```

### View Flattening

When returning multiple fields per entity, normally the result is a JSON array
of arrays. To flatten the result, use the `flatten` option:

```yaml
kind: /mica/view/v1
name: /views/ActiveClients
selector:
  entityKind: /schemas/AcmeClientList
data:
  jsonPath: $.clients[?(@.status=='active')].["clientId", "validationUrl"]
  flatten: true
```

Using the example data from the previous section, the result is:

```json
{
    "data": [
        {
            "clientId": "foo",
            "validationUrl": "http://foo.example.org"
        },
        {
            "clientId": "baz",
            "validationUrl": "http://baz.example.org"
        },
        {
            "clientId": "qux",
            "validationUrl": "http://qux.example.org"
        }
    ]
}
```

Note that now the result is a JSON array of objects, not an array of arrays.

### Merge Results

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
foos: [ 'a', 'b', 'c' ]
bars:
  baz:
    qux: 123
```

and

```yaml
kind: /schemas/Piece
name: /puzzle/piece-b
foos: [ 'x', 'y', 'z' ]
bars:
  eek: true
```

the rendered view will contain the object with keys merged from both entities:

```json
{
    "data": [
        {
            "foos": [
                "x",
                "y",
                "z"
            ],
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

### Group By Key and Merge

To group entities by a key and merge them into a single object, use the
`mergeBy` option:

```yaml
kind: /mica/view/v1
name: /views/merge-by-key
selector:
  entityKind: /schemas/WeatherMonitoringEvent
data:
  jsonPath: $
  mergeBy: $.city
```

Given the following entities:

```json
[
  { "city":  "London", "temperature": 15 },
  { "city":  "London", "humidity": 80 },
  { "city":  "Paris", "temperature": 20 },
  { "city":  "Paris", "humidity": 70 }
]
```

Should produce the following result:

```json
[
  { "city": "London", "temperature": 15, "humidity": 80 },
  { "city": "Paris", "temperature": 20, "humidity": 70 }
]
```

Just like with the regular `merge`, the entities are merged in the order they
match the patterns.

The `mergeBy` option and `merge` are mutually exclusive. If both are specified,
the `mergeBy` option takes precedence.

### JSON Patch Support

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
            "clientId": "foo",
            "validationUrl": "http://foo.example.org",
            "status": "valid"
        },
        {
            "clientId": "baz",
            "validationUrl": "http://baz.example.org",
            "status": "valid"
        },
        {
            "clientId": "qux",
            "validationUrl": "http://qux.example.org",
            "status": "valid"
        }
    ]
}
```

The view selects all active clients, picks their `id` and `validationUrl`
properties, flattens the result (so it a simple list of client entries instead
of a list of lists) and adds the `status` field to each object.

### Parametrized Views

Views can declare parameters:

```yaml
kind: /mica/view/v1
name: /views/ActiveClients
parameters:
  properties:
    clientId:
      type: string
selector:
  entityKind: /schemas/AcmeClientList
data:
  jsonPath: $.clients[?(@.id==${parameters.clientId})].["id", "validationUrl"]
  flatten: true
```

The `parameters` value must be a valid JSON Schema object (with
`type: object` by default).

The `${parameters.foo}` syntax is used to reference parameters. Currently,
only top-level fields are supported (i.e. nested parameters like
`${parameters.foo.bar}` are not supported).

Parameters can be used in:

- `selector.includes`
- `selector.entityKind`
- `selector.namePatterns`
- `data.jsonPath` expressions
- `validation.asEntityKind`

To pass the parameters, use the `parameters` field in the request body:

```shell
curl -i --json '{"viewName": "/views/ActiveClients", "parameters": {"clientId": "foo"}}' 'http://localhost:8080/api/mica/v1/view/render'
```

### View Includes

Mica can fetch data from both internal and external sources. The `includes`
field in the view definition specifies the list of URLs to fetch data from:

```yaml
kind: /mica/view/v1
name: /views/effective-config
selector:
  includes:
    - concord+git://orgName/projectName/repoName?path=/stuff/configs&ref=main
  entityKind: /mica/record/v1
  namePatterns:
    - /stuff/configs
data:
  jsonPath: $
```

Supported schemes:

- `mica://internal` -- fetch data from the internal entity store (DB);
- `concord+git://` -- fetch data from a Git repository. The repository must be
  added to a Concord project first;
- `concord+jsonstore://` -- fetch data from a Concord JSON store;
- `s3://` -- fetch data from AWS S3 buckets.

By default, `includes` contain the URL of the internal entity store:

```yaml
selector:
  includes:
    - mica://internal
```

When overriding `includes` in a view, the default value is not included. If you
wish to include both internal entities and external data, use the following
syntax:

```yaml
selector:
  includes:
    - mica://internal
    - concord+git://...
```

See below for the parameters that can be used with different schemes.

### Git Repository Support

Data can be fetched from a Git repository using the `concord+git` scheme:

```yaml
selector:
  entityKind: /some/kind/v1
  includes:
    - concord+git://orgName/projectName/repositoryName?path=/stuff/configs&ref=main
```

The `concord+git` URL must point at existing Concord organization `orgName`
containing project `projectName` with the repository `repositoryName`. When
rendering the view, Mica will fetch the contents of the repository at the given
`ref` (commit ID, tag or a branch name) and look for YAML files in the given
`path`. YAML files with the `kind` field set to `selector.entityKind` are
considered for further processing, the rest is ignored.

Supported parameters:

- `path` -- optional, path inside the repository to look for YAML files. Default
  is the repository root;
- `ref` -- optional, commit ID or branch name to fetch the data from;
- `useFileNames` -- optional, if `true`, use file names as entity names (even if
  `name` present in the file). Default is `false`;
- `namePrefix` -- optional, if specified, prepend the given string to each
  entity name. Default is empty string;
- `allowedFormats` -- optional, a comma-separated list of file types to look for.
  Default is `yaml`. See [Property Files Support](#property-files-support) for details;
- `{format}.filePattern` -- optional, a regular expression to match file names.
  Default is `.*\\.ya?ml` for YAML files and `.*\\.properties` for Java
  `.properties` files.

### Concord JSON Store Support

Data can be fetched from a Concord JSON store using the `concord+jsonstore`
scheme:

```yaml
selector:
  entityKind: /some/kind/v1
  includes:
    - concord+jsonstore://myOrg/myStore
```

Mica will look for entries with `kind` equals to `/some/kind/v1` in `myStore`
JSON store in `myOrg` Concord organization.

Supported parameters:

- `defaultKind` -- optional, sets `kind` for JSON store entries without their
  own. Default is `/concord/json-store/item/v1`.

### S3 Support

Data can be fetched from AWS S3 buckets using the `s3` scheme:

```yaml
selector:
  includes:
    - s3://my-bucket
```

Mica will fetch all objects in the bucket and try to parse them as JSON. Object
names will be used as entity names.

To fetch a specific objects add the object key:

```yaml
selector:
  includes:
    - s3://my-bucket/foo.json
```

Supported parameters:

- `defaultKind` -- optional, sets `kind` for the resulting entities. Default is `/s3/object/v1`;
- `region` -- optional, use specific AWS region. If not set then the region specified in
  the `AWS_REGION` environment variable will be used;
- `secretRef` -- optional, use AWS credentials from a Concord secret. If not set then
  the `DefaultCredentialsProvider` will be used. See below for an example;
- `endpoint` -- optional, overrides the S3 endpoint URI. The only allowed values are `localhost`
  or `127.0.0.1`. Useful only for local testing.

Example of using Concord secrets for authentication:

```yaml
selector:
  includes:
    - s3://my-bucket/foo.json?region=us-east-1&secretRef=Default/myAwsCredentials
```

Mica will look for a secret named `myAwsCredentials` in the Concord organization `Default`.
The secret must be of a `USERNAME_PASSWORD` type. It can be created using
[Concord API](https://concord.walmartlabs.com/docs/api/secret.html#create-a-secret)
or using Concord UI. Here is an example of using API to create the secret:

```shell
export AWS_ACCESS_KEY_ID=
export AWS_SECRET_ACCESS_KEY=

curl -H 'Authorization: Bearer <token>' \
-F name=myAwsCredentials \
-F type=username_password \
-F username=${AWS_ACCESS_KEY_ID} \
-F password=${AWS_SECRET_ACCESS_KEY} \
http://localhost:8080/api/v1/org/Default/secret
```

```json
{"id":"0196c48b-cec2-79fd-9d49-8980a13694a5","result":"CREATED","ok":true}
```

The user that renders the view must have `READER` access to the created secret.
The access level can be set using
[Concord API](https://concord.walmartlabs.com/docs/api/secret.html#update-access-rules)
or in the Concord UI.

### Property Files Support

Mica can ingest Java `.properties` files and render them as view entities. In
turn, views can be rendered as flat properties files.

By default, Mica includes only YAML files when fetching data from
`concord+git://` references. To include other file types, use
the `allowedFormats` parameter:

```yaml
selector:
  includes:
    - concord+git://myConcordOrg/myConcordProject/myGitRepo?allowedFormats=yaml,properties
```

The `properties` value enables support for Java `.properties` files. When
fetching data from a Git repository, Mica will look for `.properties` files and
render them as entities.

Mica uses file names to determine the format. By default, YAML files are using
`.*\\.ya?ml` pattern and `.properties` files are using `.*\\.properties` pattern.
Patterns can be overridden using `{format}.filePattern` parameters:

```yaml
selector:
  includes:
    - concord+git://myConcordOrg/myConcordProject/myGitRepo?allowedFormats=properties&properties.filePattern=.*(%5C.custom-format%7C%5C.properties)
    # unescaped version: .*(\.custom-format|\.properties)
```

The example above includes files with `.properties` and `.custom-format` extensions.

Note, the `filePattern` value is a regex, all non-URI symbols must be escaped
for the matching to work.

For example, given a `foo/bar.properties` file:

```properties
a.b.c=123
#disabledProperty=456
aBool=false
aValueWithCurlyBraces={{mustache}}
```

Mica will render the following entity:

```yaml
name: /foo/bar.properties
kind: /mica/java-properties/v1
data:
  "a.b.c": 123
  aBool: false
  aValueWithCurlyBraces: "{{mustache}}"
```

Property keys and values are treated as strings and used "as is".

Views can be rendered as flat properties files as well.

For example, given the view definition:

```yaml
name: /examples/properties/effective-properties
kind: /mica/view/v1
selector:
  entityKind: /mica/java-properties/v1
  # the properties will be merged in the order they match the patterns
  namePatterns:
    - "/examples/properties/foo.properties"
    - "/examples/properties/bar.properties"
data:
  jsonPath: $.data
  flatten: true # always true when using /api/mica/v1/view/renderProperties
```

and a couple of entities:

```yaml
name: /examples/properties/foo.properties
kind: /mica/java-properties/v1
data:
  a.b.c: 123
  aBool: false
  aValueWithCurlyBraces: "{{mustache}}"
```

```yaml
name: /examples/properties/bar.properties
kind: /mica/java-properties/v1
data:
  a.b.c: 234
  aBool: true
```

The view can be rendered as a flat properties file:

```shell
curl -i --json '{"viewName": "/examples/properties/effective-properties" }' 'http://localhost:8080/api/mica/v1/view/renderProperties'
```

```properties
a.b.c=234
aBool=true
aValueWithCurlyBraces={{mustache}}
```

### Materialize View Data As Entities

_This section is a work in progress._

Mica provides an option to save (materialize) the rendered view data as entities:

```shell
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
  required: [ 'name', 'validationUrl' ]
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
      enum: [ active, retired ]
    validationUrl:
      type: string
  required: [ 'name', 'status', 'validationUrl' ]
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

### Validate View Entities

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
    "name": "/examples/materialize/v1-to-v2",
    "kind": "/mica/materializedView/v1",
    "length": 2,
    "data": [
        {
            ...cut...,
            "validationUrl": "http://foo.example.org",
            "foo": 123,
            "status": "active"
        },
        {
            ...cut...,
            "validationUrl": "http://bar.example.org",
            "foo": 123,
            "status": "active"
        }
    ],
    "validation": [
        {
            "error": {
                "kind": "UNEXPECTED_VALUE",
                "metadata": {
                    "details": "Additional properties are not allowed: [foo]",
                    "propertyNames": [
                        "foo"
                    ]
                }
            }
        },
        {
            "error": {
                "kind": "UNEXPECTED_VALUE",
                "metadata": {
                    "details": "Additional properties are not allowed: [foo]",
                    "propertyNames": [
                        "foo"
                    ]
                }
            }
        }
    ]
}
```

In this example, we changed /examples/materialize/v1-to-v2 to disallow any
extra properties (by adding `additionalProperties: false`). As the result, the
validation fails for both entities.

### Field Mapping

The `jsonPath` operation is limited to selecting individual fields from
the source. While the JSON path implementation supports multiple field selectors,
they are limited to the fields at the same level. To select multiple fields,
possibly at different levels in the JSON data Mica provides the `map` operation:

```yaml
name: /examples/field-mapping
kind: /mica/view/v1
selector:
  entityKind: /mica/record/v1
data:
  jsonPath: $
  map:
    foo: $.foo.value
    bar: $.foo.nested.bar
```

Now, given the following entity:

```yaml
name: /examples/data
kind: /mica/record/v1
data:
  foo:
    value: 123
    nested:
      bar: 345
```

The rendered view will contain:

```json
{
    "data": [
        {
            "foo": 123,
            "bar": 345
        }
    ]
}
```

### Caching

In-memory caching can be enabled for views. The `cache` property in the view
definition specifies the cache settings:

```yaml
name: /examples/cached-view
kind: /mica/view/v1
selector:
  entityKind: /mica/record/v1
data:
  jsonPath: $
  cache:
    enabled: true
    ttl: PT10S
```

By default, caching is disabled. The `ttl` value is a duration in ISO 8601
format. The cache is invalidated after the specified period.

Caching is applied to the regular API operations such as `render` and
`renderProperties`.

## Dashboards

View data can be visualized in Mica UI using `/mica/dashboard/v1` entities.

For example, for a hypothetical dashboard that display a table of CI builds
we can store each "build" as separate entities:

```yaml
name: /examples/build-pr-443
kind: /example/build
version: 1.0.0-SNAPSHOT
finishedAt: 2024-05-01 15:48:01+0
visible: true
```

and

```yaml
name: /examples/build-main
kind: /example/build
version: 1.0.0-SNAPSHOT
finishedAt: 2024-05-01 16:01:54+0
visible: true
```

Then, given the view:

```yaml
name: /examples/build-view
kind: /mica/view/v1
selector:
  entityKind: /example/build
data:
  jsonPath: "[?(@.visible == true)]" # only "visible" builds
  map: # re-map fields
    version: $.version
    releaseDate: $.finishedAt
```

We can make a dashboard for it:

```yaml
name: /examples/build-dashboard
kind: /mica/dashboard/v1
title: CI Builds
view:
  name: /examples/build-view
layout: table
table:
  columns:
    - title: Version
      jsonPath: $.version
    - title: Release Date
      jsonPath: $.releaseDate
```

## Supported JSON Schema Features

Mica uses [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator)
under the hood. The default JSON Schema version is [2020-12](https://json-schema.org/draft/2020-12/release-notes).

## Database Design

Entities are stored in a single table `MICA_ENTITIES`.
Normally, the backend deserializes the rows as
`ca.ibodrov.mica.api.model.Entity` with straightforward mapping, where
`MICA_ENTITIES.DATA` is interpreted as "the rest of the properties".
