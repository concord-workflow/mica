# Mica

## Core Model

- `Entity` -- a data object of a specific `kind`.
    - `id` -- uuid, internal ID;
    - `name` -- string, URI path element;
    - `kind` -- string, URI path element;
    - `data` -- JSONB.

- `MicaEntityKind` -- a template for creating new entities (i.e. defining new `kind` types).
    - `id` -- uuid, internal ID;
    - `name` -- string, URI path element, unique;
    - `extendsKind` -- string, URI path element, optional. Default is `MicaRecord/v1`;
    - `schemaRef` -- string, URI, optional.

- `Property` -- a property of a `MicaSchema/v1` or `MicaEntityView/v1`.
    - `type` -- string, URI path element;
    - `value` -- JSONB, optional;
    - `required` -- boolean, optional. Default is `false`;
    - `enum` -- array of strings, optional.

- `EntityView` -- a projection of entity data.
    - `id` -- uuid, internal ID;
    - `name` -- string, URI path element, unique;
    - `selectKind` -- string, URI path element;
    - `properties` -- Map<String, Property>.

Built-in entity `kinds`:

- `MicaRecord/v1` -- basic data record, no attached behaviors;
- `MicaSchema/v1` -- entity schema object;
- `MicaEntityKind/v1` -- entity template object;
- `MicaEntityView/v1` -- entity view object.

## Schemas

Mica implements a subset of JSON Schema features:

- types: object, string, number;
- required properties;
- _TODO_ types: array, boolean, null, etc;
- _TODO_ formats: uri, email, uuid, date, time, etc;
- _TODO_ enum values.

## Examples

_This section is a work in progress. Most of the features are not implemented yet._

Let's define the schema for client profiles as a `MicaSchema/v1` entity:

```yaml
kind: MicaSchema/v1
name: ClientProfile
data:
  type: object
  properties:
    - name: id
      type: string
      required: true
    - name: name
      type: string
      required: true
    - name: status
      type: string
      required: false
      enum: [ active, retired ]
    - name: validationEndpoint
      type: string
      format: uri
      required: false
```

```
curl -i -d @client-profile-schema.yaml -H 'Content-Type: text/yaml' http://localhost:8000/api/mica/v1/entity
```

Now create a `EntityTemplate` for `CorporateProfile/v1`:

```yaml
kind: MicaEntityKind/v1
name: CorporateProfile/v1
extendsKind: MicaRecord/v1
schemaRef: ClientProfile/v1
```

This will create a new entity kind `CorporateProfile/v1` that extends
`MicaRecord/v1` and uses `ClientProfile/v1` as a schema.

Now we create a new custom entity of kind `CorporateProfile/v1`:

```yaml
kind: CorporateProfile/v1
name: acme
data:
  id: acme
  name: Acme Corp.
  status: active
  validationEndpoint: https://localhost:8001/api/v1/server/ping
```

And another one:

```yaml
kind: CorporateProfile/v1
name: evil
data:
  id: evil
  name: Evil Inc.
  status: retired
```

If we try to create an entity with invalid data, we'll get an error:

```
curl -i --json '{"kind: CorporateProfile/v1", "name": "evil", "data": {"status": "retired"}}' http://localhost:8000/api/mica/v1/entity

HTTP/1.1 400 Bad Request
Content-Type: application/json
```
```json
{
    "error": "invalid data",
    "details": [
        {
            "property": "data.id",
            "message": "required property is missing"
        },
        {
            "property": "data.name",
            "message": "required property is missing"
        }
    ]
} 
```

Let's create an `EntityView/v1` that will project `CorporateProfile/v1`
entities and return `validationEndpoint` property:

```yaml
kind: MicaEntityView/v1
name: ValidationEndpoints
selectKind: CorporateProfile/v1
properties:
  - name: id
    type: string
  - name: name
    type: string
  - name: validationEndpoint
    type: string
```

Now we can query `ValidationEndpoints` view:

```
curl -i http://localhost:8000/api/mica/v1/views/query?name=ValidationEndpoints

HTTP/1.1 200 OK
Content-Type: application/json
```

```json
[
    {
        "id": "acme",
        "name": "Acme Corp.",
        "validationEndpoint": "https://localhost:8001/api/v1/server/ping"
    },
    {
        "id": "evil",
        "name": "Evil Inc.",
        "validationEndpoint": null
    }
]
```
