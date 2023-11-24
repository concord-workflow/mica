# Mica API

## Spec

The swagger.json file is served on http://localhost:8080/api/mica/swagger.json
Visit http://localhost:5173/mica/api to see the rendered version.

## Security

Mica uses Concord's security model. The `/api/mica/v1` endpoints can be
accessed using Concord API keys:

```
curl -i -H 'Authorization: myapikey' http://localhost:8080/api/mica/v1/entity
```
