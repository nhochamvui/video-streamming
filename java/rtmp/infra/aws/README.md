# AWS RTMP Demo Infra

This CDK app deploys the Java RTMP demo in two cost profiles.

## Modes

- `cheap`: default. ECS on one public EC2 instance, Nginx, Redis container, Java RTMP tasks, S3, and CloudFront. It avoids NAT Gateway, ALB, NLB, and ElastiCache.
- `managed`: ECS Fargate, local Redis sidecar by default, S3, CloudFront, and optional ALB. Run this only for short demos unless you accept the hourly cost.

## Prerequisites

Build and push the application image first:

```sh
docker build -t rtmp-demo ../../
aws ecr create-repository --repository-name rtmp-demo
docker tag rtmp-demo:latest <account>.dkr.ecr.<region>.amazonaws.com/rtmp-demo:latest
docker push <account>.dkr.ecr.<region>.amazonaws.com/rtmp-demo:latest
```

Create the HMAC secret:

```sh
aws ssm put-parameter \
  --name /rtmp/demo/hmac-secret \
  --type SecureString \
  --value '<at-least-32-random-bytes>'
```

For GitHub Actions deployment, also configure:

- Repository secret `AWS_GITHUB_DEPLOY_ROLE_ARN`: IAM role ARN trusted by GitHub OIDC.
- CDK bootstrap in the target account/region: `npx cdk bootstrap aws://<account>/<region>`.

The workflow `.github/workflows/rtmp-aws-deploy.yml` builds the app image, pushes it to ECR repository `rtmp-demo`, then runs CDK `synth`, `deploy`, or `destroy` from manual dispatch inputs.

## Cheap Mode

Install and synthesize:

```sh
npm install
npm run synth:cheap -- -c appImage=<account>.dkr.ecr.<region>.amazonaws.com/rtmp-demo:latest
```

Deploy one node:

```sh
npx cdk deploy \
  -c deployMode=cheap \
  -c appImage=<account>.dkr.ecr.<region>.amazonaws.com/rtmp-demo:latest \
  -c desiredAppCount=1
```

After the EC2 instance is running, get its public DNS/IP from the EC2 console or CloudFormation resources, then redeploy with:

```sh
npx cdk deploy \
  -c deployMode=cheap \
  -c appImage=<account>.dkr.ecr.<region>.amazonaws.com/rtmp-demo:latest \
  -c desiredAppCount=1 \
  -c rtmpHost=<instance-public-dns-or-ip>
```

For a short scaling demo on the same EC2 host:

```sh
npx cdk deploy \
  -c deployMode=cheap \
  -c appImage=<account>.dkr.ecr.<region>.amazonaws.com/rtmp-demo:latest \
  -c desiredAppCount=3 \
  -c rtmpHost=<instance-public-dns-or-ip>
```

Cheap mode maps demo nodes to:

- HTTP app ports: `8888`, `8889`, `8890`
- RTMP ports: `1935`, `1936`, `1937`
- Nginx public HTTP: `80`

Use only one active stream on free-tier-size EC2 instances. FFmpeg remains the real CPU/RAM floor.

## Managed Mode

Managed mode is for short-lived cloud-native demos:

```sh
npx cdk deploy \
  -c deployMode=managed \
  -c appImage=<account>.dkr.ecr.<region>.amazonaws.com/rtmp-demo:latest \
  -c desiredAppCount=2 \
  -c enableElastiCache=true \
  -c enableAlb=true \
  -c enableNlb=true \
  -c rtmpHost=<rtmp-hostname>
```

For a single-task managed demo, leave `enableElastiCache=false` and the task uses a Redis sidecar. For `desiredAppCount > 1`, enable ElastiCache so all app tasks share stream-session and node state.

Destroy when finished:

```sh
npx cdk destroy -c deployMode=managed
```

## Cost Guardrails

- No NAT Gateway is created.
- Cheap mode does not create ALB, NLB, or ElastiCache.
- CloudWatch log retention is three days.
- Buckets are destroyable by default for demo cleanup.
- Public IPv4, CloudFront, S3 requests, and running compute can still create charges.
