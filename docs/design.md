# Mica

## Core Model

- `Entity` -- a data object of a specific `kind`.
    - `id` -- uuid, internal ID;
    - `name` -- string, URI path element;
    - `kind` -- string, URI path element;
    - `data` -- JSONB, all other properties.

- `Property` -- a property of a `MicaSchema/v1` or `MicaEntityView/v1`.
    - `type` -- string, URI path element;
    - `value` -- JSONB, optional;
    - `required` -- boolean, optional. Default is `false`;
    - `enum` -- array of strings, optional.

- `EntityView` -- a projection of entity data.
    - `id` -- uuid, internal ID;
    - `name` -- string, URI path element, unique;
    - `selectKind` -- string, URI path element;
    - `fields` -- array of property references.

Built-in entity `kinds`:

- `MicaRecord/v1` -- basic data record, no attached behaviors;
- `MicaSchema/v1` -- entity schema object;
- `MicaKind/v1` -- a `kind` definition, entity "template" object;
- `MicaEntityView/v1` -- entity view object.

## Schemas

Mica implements a subset of JSON Schema features:

- types: object, string, number;
- required properties;
- enum values;
- _TODO_ types: array, boolean, etc;
- _TODO_ formats: uri, email, uuid, date, time, etc;
- _TODO_ refs

## Database Design

Entities are stored in a single table `MICA_ENTITIES`.
Normally, the backend deserializes the rows as
`ca.ibodrov.mica.api.model.Entity` with straightforward mapping, where
`MICA_ENTITIES.DATA` is interpreted as "the rest of the properties".
