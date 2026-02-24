# sdk-test-rust

Compatibility tests for [Atlas](https://github.com/atlas/aws-emulator) using the **AWS SDK for Rust (1.8.15)**.

## Services Covered

| Group              | Description                                             |
| ------------------ | ------------------------------------------------------- |
| `ssm`              | Parameter Store — put, get, path                        |
| `sqs`              | Queues, send/receive/delete, visibility                 |
| `sns`              | Topics, subscriptions, publish                          |
| `s3`               | Buckets, objects, tagging, copy, delete                 |
| `s3-cors`          | CORS configuration                                      |
| `s3-notifications` | S3 event notifications                                  |
| `dynamodb`         | Tables, CRUD, batch                                     |
| `lambda`           | Create/invoke/update/delete functions                   |
| `iam`              | Users, roles, policies, access keys                     |
| `sts`              | GetCallerIdentity                                       |
| `kms`              | Keys, aliases, encrypt/decrypt                          |
| `secretsmanager`   | Create/get/put/list/delete secrets                      |
| `kinesis`          | Streams, shards, PutRecord/GetRecords                   |
| `cloudwatch`       | PutMetricData, ListMetrics, GetMetricStatistics, alarms |
| `cloudformation`   | Stack operations                                        |

## Requirements

- Rust (stable)
- Cargo
- cargo-nextest

## Running

```bash
# All groups
cargo nextest run --profile ci

# Specific groups
cargo nextest run --profile ci -E 'test(ssm) | test(sqs) | test(s3)'

# Via just (from compatibility-tests/)
just test-rust
```

## Configuration

| Variable         | Default                 | Description             |
| ---------------- | ----------------------- | ----------------------- |
| `ATLAS_ENDPOINT` | `http://localhost:4566` | Atlas emulator endpoint |

AWS credentials are always `test` / `test` / `us-east-1`.

## Docker

```bash
docker build -t atlas-sdk-rust .
docker run --rm --network host atlas-sdk-rust

# Custom endpoint (macOS/Windows)
docker run --rm -e ATLAS_ENDPOINT=http://host.docker.internal:4566 atlas-sdk-rust
```
