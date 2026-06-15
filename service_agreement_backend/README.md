# Service Agreement Backend

Java/Spring Boot replacement for the old Node/Excel backend.

## What Changed

- Contracts are fetched from the collaboration API:
  `https://masterdata-api-prod.grouphive.de/elements/aa0000000000000000000010/query`
- OAuth 2.0 client credentials are read from properties or environment variables.
- Worklogs are stored in Postgres instead of an Excel workbook.
- The iframe-facing endpoints are kept compatible:
  - `GET /contract/{orderNumber}`
  - `GET /contract-by-external-id/{externalId}`
  - `POST /worklog`
  - `PUT /worklog/{id}`
  - `GET /worklogs/{orderNumber}`
  - `GET /export/worklogs`

## Required Configuration

Set these values in `src/main/resources/application.properties`, environment variables, or deployment secrets:

```properties
POSTGRES_URL=jdbc:postgresql://host:5432/database
POSTGRES_USERNAME=username
POSTGRES_PASSWORD=password

COLLABORATION_OAUTH_TOKEN_URL=https://identity-provider.example.com/oauth/token
COLLABORATION_OAUTH_CLIENT_ID=client-id
COLLABORATION_OAUTH_CLIENT_SECRET=client-secret
COLLABORATION_OAUTH_SCOPE=optional-scope

EXPORT_TOKEN=shared-export-token
```

Optional tuning:

```properties
COLLABORATION_LIMIT=1000
COLLABORATION_IS_INACTIVE=true
COLLABORATION_QUICK_FILTERS=A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,0,1,2,3,4,5,6,7,8,9
COLLABORATION_ELEMENT_CATEGORY_ID=
```

If `COLLABORATION_QUICK_FILTERS` is empty, the backend sends one broad request. If the API is slow or caps results, use multiple quick filters and increase/decrease `COLLABORATION_LIMIT` after testing. `COLLABORATION_IS_INACTIVE=true` follows the API note you provided and fetches active items only; leave it blank to omit the field and include all items.

## Run Locally

```powershell
.\mvnw.cmd spring-boot:run
```

Build a jar:

```powershell
.\mvnw.cmd -DskipTests package
java -jar .\target\service-agreement-backend-1.0.0.jar
```

The app listens on port `3000` by default.

## Zendesk Iframe Setting

`iframe.html` now reads the backend URL from `{{setting.api_base}}`. Configure that Zendesk app setting to the deployed Java backend base URL, for example:

```text
https://service-agreement-backend.example.com
```
