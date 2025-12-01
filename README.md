# Atlas AWS Emulator

APAC Atlas local AWS API emulator.

## Quick start

```bash
# Dev mode (requires JDK 25)
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./mvnw quarkus:dev

# Docker
docker compose up --build
```

Endpoint: **http://localhost:4566**

**Demo UI:** **http://localhost:4566/demo** — service status, demo checklist, and AWS CLI commands.

Configure via `atlas.*` properties or `ATLAS_*` environment variables (e.g. `ATLAS_PORT`, `ATLAS_HOSTNAME`).

## Demo your 8 services

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
EP="--endpoint-url http://localhost:4566"

aws $EP s3api list-buckets
aws $EP dynamodb list-tables
aws $EP iam list-users
aws $EP ec2 describe-instances
aws $EP lambda list-functions
aws $EP ecs list-clusters
aws $EP eks list-clusters
aws $EP apigateway get-rest-apis
```

## Project layout

| Item | Value |
| --- | --- |
| Maven group | `com.apac.atlas` |
| Maven artifact | `aws-emulator` |
| Java package | `com.apac.atlas` |
| Config prefix | `atlas` |

Browsing `http://localhost:4566/` in a browser returns S3 XML — that is normal. Open **`/demo`** for the manager demo page, or use the AWS CLI / SDK.
