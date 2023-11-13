# Mica API

## Security

Mica uses Concord's security model. The `/api/mica/v1` endpoints can be
accessed using Concord API keys:

```
curl -i -H 'Authorization: myapikey' http://localhost:8001/api/mica/v1/client
```

## Endpoints

Enumerate clients:

```
# get all clients
GET /api/mica/v1/client

# search
GET /api/mica/v1/client?search=foobar

# search and return extra properties from the latest client data
GET /api/mica/v1/client?search=foobar&props=some_prop&props=...
```

Read client data:

```
# upload client data
POST /api/mica/v1/clientData/import

# fetch the latest published data
GET /api/mica/v1/clientData/latest?externalId=...
```
