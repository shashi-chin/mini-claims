# mini-claims

Week 1 Java 17 mini-claims API for a Texas / Guidewire-prep plan. It is shaped like **Guidewire ClaimCenter Cloud API claims intake** (`POST /claims`, idempotency, claim JSON). **This is not ClaimCenter** and not a ClaimCenter install.

Author: Shashi Chin (`au.com.shashichin`).

Tonight's done bar: repo + OpenAPI + tests for `POST /claims` (failing then passing). In-memory store only. No AWS yet.

## Architecture

The API is a single Spring Boot 3.4 service exposing a REST interface for claims intake. Storage is in-memory for now.

```mermaid
flowchart LR
    subgraph Client
        C[HTTP Client]
    end

    subgraph Spring Boot API
        CTRL[ClaimController]
        SVC[ClaimService]
        subgraph In-Memory Store
            IK[(byIdempotencyKey)]
            ID[(byId)]
        end
    end

    C -->|POST /claims<br>GET /claims/id| CTRL
    CTRL --> SVC
    SVC --> IK
    SVC --> ID
    SVC -->|Claim JSON| CTRL
    CTRL -->|Response| C
```

**Packages:**
- `au.com.shashichin.miniclaims` — main app entry point
- `au.com.shashichin.miniclaims.claim` — controller, service, domain records, exceptions
- `au.com.shashichin.miniclaims.error` — global exception handler and `ApiError` response

### Request Flow: POST /claims

Every `POST /claims` requires an `Idempotency-Key` header. The service checks the key against a stored map to decide whether this is a new request, a replay, or a conflict.

```mermaid
sequenceDiagram
    participant C as Client
    participant CTRL as ClaimController
    participant SVC as ClaimService
    participant STORE as In-Memory Maps

    C->>CTRL: POST /claims + Idempotency-Key + body
    CTRL->>SVC: create(idempotencyKey, request)

    alt Key missing or blank
        SVC-->>CTRL: MissingIdempotencyKeyException
        CTRL-->>C: 400 Bad Request
    else Key exists, body matches
        SVC->>STORE: lookup byIdempotencyKey
        STORE-->>SVC: existing claim
        SVC-->>CTRL: CreateClaimResult(claim, replay=true)
        CTRL-->>C: 200 OK + existing claim
    else Key exists, body differs
        SVC->>STORE: lookup byIdempotencyKey
        STORE-->>SVC: stored request differs
        SVC-->>CTRL: IdempotencyConflictException
        CTRL-->>C: 409 Conflict
    else New key
        SVC->>STORE: store in byIdempotencyKey & byId
        SVC-->>CTRL: CreateClaimResult(claim, replay=false)
        CTRL-->>C: 201 Created + new claim
    end
```

### CI Pipeline

Tests run on every push and pull request via GitHub Actions. A Buildkite pipeline is also defined and ready to run when an agent is connected.

```mermaid
flowchart TB
    subgraph Trigger
        PUSH[git push / PR]
    end

    subgraph GitHub Actions
        GHA[ubuntu-latest<br>Java 17 Temurin]
        GHA_TEST["./mvnw -B test"]
    end

    subgraph Buildkite
        BK[linux-small agent]
        BK_DOCKER[eclipse-temurin:17-jdk container]
        BK_TEST["./mvnw -B test"]
    end

    PUSH --> GHA
    GHA --> GHA_TEST
    PUSH -.->|when agent connected| BK
    BK --> BK_DOCKER --> BK_TEST
```

## Target AWS Architecture (not deployed)

This is the target production stack. **None of this is running yet** — the current repo is in-memory only.

```mermaid
flowchart TB
    subgraph Edge / Access
        R53[Route 53<br>DNS]
        ACM[ACM<br>TLS]
        WAF[AWS WAF]
        APIGW[API Gateway<br>HTTP API]
        COG[Cognito<br>JWT auth]
    end

    subgraph VPC
        subgraph Public Subnet
            ALB[Application<br>Load Balancer]
        end
        subgraph Private Subnet - App
            ECS[ECS Fargate<br>Spring Boot]
        end
        subgraph Private Subnet - Data
            RDS[(RDS PostgreSQL<br>claim records)]
            DDB[(DynamoDB<br>Idempotency-Key)]
        end
    end

    subgraph Storage
        S3[(S3<br>FNOL attachments)]
    end

    subgraph Events
        EB[EventBridge<br>claim.created<br>claim.status.changed]
        SQS[SQS<br>async workers]
        SNS[SNS<br>notifications]
    end

    subgraph Side Path
        LAMBDA[Lambda<br>S3 object-created<br>metadata + enqueue]
    end

    subgraph Security
        IAM[IAM task roles]
        SM[Secrets Manager<br>RDS creds]
        KMS[KMS]
        CT[CloudTrail]
        GD[GuardDuty]
    end

    subgraph Ops
        CW[CloudWatch<br>logs / metrics / alarms]
        XRAY[X-Ray]
    end

    subgraph Container Registry
        ECR[ECR<br>mini-claims image]
    end

    R53 --> ACM
    ACM --> WAF
    WAF --> APIGW
    APIGW --> COG
    COG --> ALB
    ALB --> ECS
    ECS --> RDS
    ECS --> DDB
    ECS --> S3
    ECS --> EB
    EB --> SNS
    EB --> SQS
    S3 --> LAMBDA
    LAMBDA --> SQS
    ECS --> SM
    SM --> KMS
    ECS --> CW
    ECS --> XRAY
    ECR -.->|image| ECS
```

### Request Flow: POST /claims (AWS target)

```mermaid
sequenceDiagram
    participant C as Client
    participant R53 as Route 53
    participant APIGW as API Gateway + WAF
    participant COG as Cognito
    participant ALB as ALB
    participant ECS as ECS Fargate
    participant DDB as DynamoDB
    participant RDS as RDS PostgreSQL
    participant EB as EventBridge
    participant SNS as SNS

    C->>R53: POST /claims
    R53->>APIGW: DNS resolve
    APIGW->>COG: validate JWT
    COG-->>APIGW: OK
    APIGW->>ALB: forward
    ALB->>ECS: route to task

    ECS->>DDB: PutItem conditional<br>(Idempotency-Key)

    alt Key already exists, body matches
        DDB-->>ECS: existing claimId
        ECS-->>C: 200 OK (replay)
    else Key exists, body differs
        DDB-->>ECS: ConditionalCheckFailed
        ECS-->>C: 409 Conflict
    else New key accepted
        DDB-->>ECS: success
        ECS->>RDS: INSERT claim
        RDS-->>ECS: claimId
        ECS->>EB: claim.created event
        EB->>SNS: notify
        ECS-->>C: 201 Created
    end
```

### CI/CD: Buildkite → ECS (target)

GitHub Actions and Buildkite continue to run tests. On merge to main, Buildkite builds the image, pushes to ECR, and deploys to ECS via OIDC/IAM (no long-lived credentials).

```mermaid
flowchart LR
    subgraph CI
        GH[GitHub Actions<br>tests]
        BK[Buildkite<br>tests + deploy]
    end

    subgraph AWS
        ECR[ECR]
        ECS[ECS Fargate]
    end

    GH -->|on PR| GH
    BK -->|on merge| ECR
    ECR --> ECS
    BK -.->|OIDC / IAM role| ECR
```

### Rollout phases

| Phase | Components |
| --- | --- |
| **Now** | In-memory store, tests (GitHub Actions + Buildkite) |
| **Phase 1** | VPC, IAM, ECR, ECS Fargate, ALB, RDS PostgreSQL, Secrets Manager, CloudWatch, CloudTrail |
| **Phase 2** | API Gateway, Cognito, DynamoDB (idempotency), S3, SQS/SNS/EventBridge, KMS, WAF, X-Ray, GuardDuty |
| **Later** | Step Functions, SES, or Textract only if claims workflow or document processing requires them |

## Run

Java 17 required.

```bash
./mvnw test
./mvnw spring-boot:run
```

- Create a claim: `POST /claims` with header `Idempotency-Key` and JSON body
- Fetch a claim: `GET /claims/{claimId}`
- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: http://localhost:8080/swagger-ui.html

### Claim JSON

```json
{
  "claimId": "generated-on-create",
  "lossDate": "2026-08-20",
  "description": "Rear-end collision at a roundabout",
  "status": "draft",
  "reporterName": "Shashi Chin"
}
```

`status` is `draft` or `open`. Omit it on create and it defaults to `draft`.

### Idempotency (`POST /claims`)

| `Idempotency-Key` | Body | Result |
| --- | --- | --- |
| missing / blank | any | `400` |
| new key | valid | `201` + `claimId` |
| same key | same body | `200` + same `claimId` |
| same key | different body | `409` |

## OpenAPI notes

springdoc-openapi serves the spec at **`GET /v3/api-docs`**. That is the machine-readable contract for this mini API (not a dumped ClaimCenter Cloud API spec). Use it to see `POST /claims`, `GET /claims/{claimId}`, the `Idempotency-Key` header, and the claim schema. Swagger UI is a convenience wrapper around the same spec.

## Tonight

Get tests green:

```bash
./mvnw test
```

The tests cover missing idempotency key, first create, replay, conflict, and GET of the created claim.

## CI

**GitHub Actions runs now** (`.github/workflows/ci.yml`: Java 17, `./mvnw -B test`).

`.buildkite/pipeline.yml` is the same Maven test step, ready when a Buildkite agent exists. It is **not** connected: there is no agent on this account yet, and this repo does not pretend otherwise.

## Next weeks (not in this repo yet)

Auth on the API, persist claims to RDS instead of the in-memory map, IAM for the app role, and a real deploy path. Week 1 stops at a green local/CI test suite.
