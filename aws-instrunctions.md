# Sprint 11: Deploying Mission Control to AWS - Student Guide

This guide walks you through Modules 2 to 10 of Sprint 11 using the AWS CLI. Every command is written for **your own** resources, so 25 people can work in the same AWS account without getting in each other's way.

Work through it in order. Each module builds on the one before.

---

## Before you start: the ground rules

### 1. Everything you create carries your name

You will choose a personal prefix in the form `teamN-yourname`, for example `team3-sam`. It must be:

- **lowercase**, letters, numbers and hyphens only (S3 bucket names reject capitals)
- **short**, 16 characters or fewer (load balancer names have a 32-character limit)
- **the same as your IAM user name**

Every security group, bucket, repository, cluster, role and database you create starts with this prefix. If a command ever fails with `AlreadyExists` or `Duplicate`, check you used your prefix and not someone else's.

### 2. What is shared, and what you must not touch

The class shares one AWS account and one VPC (the account's **default VPC**). Your instructor has already set up the following. **Do not create, change or delete any of these:**

| Shared resource | Why it is shared |
|---|---|
| The default VPC and its subnets | The account can only hold a handful of VPCs |
| Two **private** subnets (no internet route) | Used by your ECS service and database from Module 8 |
| VPC endpoints for `ecr.api`, `ecr.dkr`, `logs`, `secretsmanager` and `s3` | Only one set can exist per VPC; they let private tasks reach AWS without the internet |
| The endpoints' security group | Already allows HTTPS from anywhere inside the VPC |

If you find yourself about to run `create-vpc-endpoint`, `create-route-table` or `associate-route-table`, stop: those are already done.

### 3. You work on an EC2 instance, your browser stays on your laptop

You run every command on your own **Amazon Linux 2023 EC2 instance** (set up in the next section). Whenever the guide says "open it in a browser", use the browser on **your laptop**: the S3, CloudFront and load balancer addresses are all public.

### 4. Use `${VAR}` with braces, always

Every command in this guide writes variables as `${VAR}`. Keep it that way if you type your own: on some shells (zsh, the Mac default) `$ACCOUNT_ID:role` is silently mangled and produces errors like `Role is not valid`. Braces work everywhere.

### 5. Tidy up at the end

Load balancers, databases and running tasks cost money every hour. The **Teardown** section at the end removes everything you created. Do it before you leave.

---

## Setting up your EC2 instance

### The instance

Your instructor will tell you whether your instance is already running or whether you launch it yourself. If you launch it, use:

| Setting | Value |
|---|---|
| AMI | Amazon Linux 2023 |
| Instance type | `t3.medium` or larger (a `t3.micro` runs out of memory during the builds) |
| Storage | 30 GB gp3 (the default 8 GB fills up with Docker images and `node_modules`) |
| Name tag | `${ME}-workstation`, for example `team3-sam-workstation` |

Connect to it with **EC2 Instance Connect** in the console, or SSH, as `ec2-user`.

### Install the tools

Amazon Linux 2023 already has the AWS CLI v2, Python 3 and `curl`. Add Docker, git and Node.js:

```bash
sudo dnf install -y docker git nodejs22
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
newgrp docker
```

`newgrp docker` lets you use Docker without `sudo` straight away. In any **new** session, Docker works without it, because you log in as a member of the group.

Check everything is there:

```bash
aws --version          # aws-cli/2.x
docker run --rm hello-world | head -2
node -v                # v22.x (Angular 21 needs at least 20.19)
git --version
python3 --version
```

If `nodejs22` is not found, run `dnf list available 'nodejs*'` and install the newest `nodejs` package listed, then check `node -v` is 20.19 or later.

### Get the course repository

```bash
cd ~
git clone <course-repo-url> leap
cd leap
```

Your instructor will give you the repository address. Everything from here runs from this folder unless the guide says otherwise.

### Which credentials is the CLI using?

If your instance has an IAM role attached, the AWS CLI may quietly use that role instead of your own user. Module 2 sets up a profile with **your** access key, and `get-caller-identity` must show `user/teamN-yourname`. If it shows `assumed-role/...`, the profile is not being used: check `echo ${AWS_PROFILE}`.

### Build the three apps

From the repository root:

```bash
# Sprint 6 mission service (listens on 8080)
cd leap-sprint6/solutions/13-mission-build-containerise-integration-test-wrap-up
docker build -t leap-mission-service:latest .
cd -

# Sprint 8 auth service (listens on 3000)
cd leap-sprint8/solutions/15-mission-build-openapi-docs-and-replacing-the-sprint7-stub
docker build -t leap-auth-service:latest .
cd -

# Sprint 9 Angular frontend
cd leap-sprint9/solutions/mission-ui
npm install
npx ng build --configuration production
cd -
```

> Build the mission service from the **solutions** folder. The **labs** copy of Module 13 listens on port 8090, which will not match anything in this guide.

---

## Module 2: Connecting the CLI to AWS

### Configure your profile

Use the access key for your own IAM user (`teamN-yourname`):

```bash
aws configure --profile training
# AWS Access Key ID:     <your key>
# AWS Secret Access Key: <your secret>
# Default region name:   eu-west-1
# Default output format: json
```

Check who AWS thinks you are:

```bash
aws sts get-caller-identity --profile training
```

The `Arn` should end in `user/teamN-yourname`. Then look around:

```bash
aws configure list --profile training

aws ec2 describe-availability-zones --profile training --region eu-west-1 \
  --query 'AvailabilityZones[].{Name:ZoneName,State:State}' --output table

aws iam list-groups-for-user --user-name <your-iam-user> --profile training
aws iam list-attached-group-policies --group-name students --profile training
```

### Create your environment file

You will open several terminals over the day. Rather than retyping settings and IDs in each one, you will keep them in a small file and load it whenever you open a new terminal.

From the **repository root**, run these, changing `teamN-yourname` to your own prefix first:

```bash
echo 'export AWS_PROFILE=training'      >  ~/leap-env.sh
echo 'export AWS_REGION=eu-west-1'      >> ~/leap-env.sh
echo 'export ME=teamN-yourname'         >> ~/leap-env.sh
echo "export REPO=\"$(pwd)\""           >> ~/leap-env.sh

source ~/leap-env.sh

echo "export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)" >> ~/leap-env.sh
echo "export VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)" >> ~/leap-env.sh
source ~/leap-env.sh
echo "export REG=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com" >> ~/leap-env.sh

source ~/leap-env.sh
cat ~/leap-env.sh
```

You should see your profile, region, prefix, repo path, a 12-digit account number, a `vpc-` ID and an ECR registry address.

Make a folder for the JSON files you will create, and work from it:

```bash
mkdir -p ~/leap-work && cd ~/leap-work
```

> **Every time you open a new terminal:** run `source ~/leap-env.sh && cd ~/leap-work` first. Most "it worked a minute ago" problems are a terminal that has not done this.

### Find the public and private subnets

The default VPC contains both public subnets (with a route to the internet) and the shared private subnets. This loop shows which is which:

```bash
for s in $(aws ec2 describe-subnets --filters Name=vpc-id,Values=${VPC_ID} --query 'Subnets[].SubnetId' --output text); do
  az=$(aws ec2 describe-subnets --subnet-ids $s --query 'Subnets[0].AvailabilityZone' --output text)
  rt=$(aws ec2 describe-route-tables --filters Name=association.subnet-id,Values=$s --query 'RouteTables[0].RouteTableId' --output text)
  [ "$rt" = "None" ] && rt=$(aws ec2 describe-route-tables --filters Name=vpc-id,Values=${VPC_ID} Name=association.main,Values=true --query 'RouteTables[0].RouteTableId' --output text)
  igw=$(aws ec2 describe-route-tables --route-table-ids $rt --query "RouteTables[0].Routes[?DestinationCidrBlock=='0.0.0.0/0'].GatewayId" --output text)
  if [ -n "$igw" ]; then echo "$s  $az  PUBLIC"; else echo "$s  $az  PRIVATE"; fi
done
```

Pick **two public subnets in different availability zones** and **two private subnets in different availability zones**, and save them:

```bash
echo 'export PUB_A=subnet-xxxxxxxx'  >> ~/leap-env.sh
echo 'export PUB_B=subnet-xxxxxxxx'  >> ~/leap-env.sh
echo 'export PRIV_A=subnet-xxxxxxxx' >> ~/leap-env.sh
echo 'export PRIV_B=subnet-xxxxxxxx' >> ~/leap-env.sh
source ~/leap-env.sh
```

---

## Module 3: Security Groups

### It works on my machine

Start the two services locally:

```bash
docker run -d --name auth    -p 3000:3000 leap-auth-service:latest
docker run -d --name mission -p 8080:8080 leap-mission-service:latest
```

`-p host:container` is what publishes a port to your machine. Without it, `curl` gets `Connection refused` even though the container is running. Check with `docker ps`: the PORTS column must show `0.0.0.0:8080->8080/tcp`.

```bash
curl localhost:3000/health
# {"status":"up"}

curl -i localhost:8080/actuator/health
# HTTP/1.1 401 ... WWW-Authenticate: Bearer
```

The mission service returns `401` with an empty body for every unauthenticated request. Plain `curl` prints nothing for an empty body, so always use `-i` to see the status line. Any HTTP status, including 401, proves the app is up.

### Build a least-privilege pair of security groups

A web tier open to the internet on port 80, and an app tier that only accepts traffic **from the web tier's security group**:

```bash
WEB_SG=$(aws ec2 create-security-group --group-name ${ME}-example-web-sg \
  --description "${ME} public web traffic" --vpc-id ${VPC_ID} \
  --query GroupId --output text)

aws ec2 authorize-security-group-ingress --group-id ${WEB_SG} \
  --protocol tcp --port 80 --cidr 0.0.0.0/0

EXAMPLE_APP_SG=$(aws ec2 create-security-group --group-name ${ME}-example-app-sg \
  --description "${ME} app tier, reachable only from the web tier" --vpc-id ${VPC_ID} \
  --query GroupId --output text)

aws ec2 authorize-security-group-ingress --group-id ${EXAMPLE_APP_SG} \
  --protocol tcp --port 8080 --source-group ${WEB_SG}

echo "export WEB_SG=${WEB_SG}" >> ~/leap-env.sh
echo "export EXAMPLE_APP_SG=${EXAMPLE_APP_SG}" >> ~/leap-env.sh
```

Verify the app tier's rule:

```bash
aws ec2 describe-security-groups --group-ids ${EXAMPLE_APP_SG} \
  --query 'SecurityGroups[0].IpPermissions'
```

You should see port 8080 with your web group under `UserIdGroupPairs` and **an empty `IpRanges`**. Nothing on the internet can reach port 8080 directly; only something carrying the web group can.

Stop the local containers when you are done:

```bash
docker rm -f auth mission
```

---

## Module 4: S3 and a Public Static Website

Bucket names are global across **every** AWS account, so yours includes your prefix:

```bash
echo "export SITE_BUCKET=leap-${ME}-site" >> ~/leap-env.sh
source ~/leap-env.sh

aws s3api create-bucket --bucket ${SITE_BUCKET} \
  --create-bucket-configuration LocationConstraint=${AWS_REGION}
```

> Outside `us-east-1`, `create-bucket` needs `--create-bucket-configuration LocationConstraint=...`. Without it you get `IllegalLocationConstraintException`. If you get `BucketAlreadyExists`, someone in the world already has that name: add a few digits to the end.

Turn on website hosting:

```bash
aws s3api put-bucket-website --bucket ${SITE_BUCKET} --website-configuration \
  '{"IndexDocument": {"Suffix": "index.html"}, "ErrorDocument": {"Key": "index.html"}}'
```

Create a policy that lets anyone **read** objects (not list, upload or delete):

```bash
cat > public-read-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "PublicReadGetObject",
    "Effect": "Allow",
    "Principal": "*",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::${SITE_BUCKET}/*"
  }]
}
EOF

aws s3api put-bucket-policy --bucket ${SITE_BUCKET} --policy file://public-read-policy.json
```

**This is meant to fail** with `AccessDenied ... BlockPublicPolicy`. Every new bucket has Block Public Access switched on:

```bash
aws s3api get-public-access-block --bucket ${SITE_BUCKET}
```

All four settings are `true`. For this module only, switch them off deliberately and apply the policy again:

```bash
aws s3api put-public-access-block --bucket ${SITE_BUCKET} --public-access-block-configuration \
  BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false

aws s3api put-bucket-policy --bucket ${SITE_BUCKET} --policy file://public-read-policy.json
```

Upload the Angular build. Recent Angular versions put the files in a `browser` subfolder:

```bash
aws s3 sync "${REPO}/leap-sprint9/solutions/mission-ui/dist/mission-ui/browser" s3://${SITE_BUCKET}
```

Test the website endpoint. Note the region in the address:

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  http://${SITE_BUCKET}.s3-website-${AWS_REGION}.amazonaws.com
# HTTP 200
```

Open `http://<your-bucket>.s3-website-eu-west-1.amazonaws.com` in a browser to see the Mission Control login screen, served with no server at all. A `400` means the region in the URL is wrong; a `404` means website hosting is not on; a `403` means the policy is not applied.

**What is wrong with this:** the bucket is public and the site has no HTTPS. Module 5 fixes both.

---

## Module 5: CloudFront in Front of a Private Bucket

### A private bucket

This time Block Public Access stays **on**:

```bash
echo "export CF_BUCKET=leap-${ME}-cf" >> ~/leap-env.sh
source ~/leap-env.sh

aws s3api create-bucket --bucket ${CF_BUCKET} \
  --create-bucket-configuration LocationConstraint=${AWS_REGION}

aws s3api get-public-access-block --bucket ${CF_BUCKET}

aws s3 sync "${REPO}/leap-sprint9/solutions/mission-ui/dist/mission-ui/browser" s3://${CF_BUCKET}
```

Prove it is private:

```bash
curl -s "https://${CF_BUCKET}.s3.${AWS_REGION}.amazonaws.com/index.html"
# <Error><Code>AccessDenied</Code>...
```

`AccessDenied` is the result you want, and it will never change, however long you wait.

### Origin Access Control

An OAC is the identity CloudFront uses when it reads from S3. Create your own:

```bash
OAC_ID=$(aws cloudfront create-origin-access-control --origin-access-control-config "{
  \"Name\": \"${ME}-mission-ui-oac\", \"SigningProtocol\": \"sigv4\",
  \"SigningBehavior\": \"always\", \"OriginAccessControlOriginType\": \"s3\"
}" --query 'OriginAccessControl.Id' --output text)

echo "export OAC_ID=${OAC_ID}" >> ~/leap-env.sh
echo ${OAC_ID}
```

### The distribution

```bash
cat > dist-config.json <<EOF
{
  "CallerReference": "${ME}-$(date +%s)",
  "Comment": "${ME} mission-ui",
  "Enabled": true,
  "DefaultRootObject": "index.html",
  "Origins": {
    "Quantity": 1,
    "Items": [{
      "Id": "s3-${CF_BUCKET}",
      "DomainName": "${CF_BUCKET}.s3.${AWS_REGION}.amazonaws.com",
      "OriginAccessControlId": "${OAC_ID}",
      "S3OriginConfig": { "OriginAccessIdentity": "" }
    }]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "s3-${CF_BUCKET}",
    "ViewerProtocolPolicy": "redirect-to-https",
    "CachePolicyId": "658327ea-f89d-4fab-a63d-7e88639e58f6"
  }
}
EOF

grep -E 'OriginAccessControlId|DomainName' dist-config.json

DIST_ID=$(aws cloudfront create-distribution --distribution-config file://dist-config.json \
  --query 'Distribution.Id' --output text)

echo "export DIST_ID=${DIST_ID}" >> ~/leap-env.sh
echo "export DIST_DOMAIN=$(aws cloudfront get-distribution --id ${DIST_ID} --query 'Distribution.DomainName' --output text)" >> ~/leap-env.sh
source ~/leap-env.sh
echo ${DIST_ID} ${DIST_DOMAIN}
```

`DIST_ID` (like `E1ABC...`) is what you use in AWS commands. `DIST_DOMAIN` (like `d1abc....cloudfront.net`) is the web address. The cache policy ID is AWS's built-in "CachingOptimized" policy.

### The bucket policy

The policy needs the distribution's ARN, so it always comes **after** the distribution:

```bash
DIST_ARN=$(aws cloudfront get-distribution --id ${DIST_ID} --query 'Distribution.ARN' --output text)

cat > cf-bucket-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowCloudFrontServicePrincipal",
    "Effect": "Allow",
    "Principal": {"Service": "cloudfront.amazonaws.com"},
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::${CF_BUCKET}/*",
    "Condition": {"StringEquals": {"AWS:SourceArn": "${DIST_ARN}"}}
  }]
}
EOF

aws s3api put-bucket-policy --bucket ${CF_BUCKET} --policy file://cf-bucket-policy.json
```

This succeeds even with Block Public Access fully on: the principal is one named AWS service, limited to one distribution, so it was never public.

### Test it

Wait for the distribution to deploy (several minutes; the command prints nothing and returns when done):

```bash
aws cloudfront wait distribution-deployed --id ${DIST_ID}

curl -s -o /dev/null -w "HTTP %{http_code}\n" https://${DIST_DOMAIN}/
# HTTP 200

curl -sI https://${DIST_DOMAIN}/ | grep -i "x-cache\|via"
# x-cache: Miss from cloudfront   (Hit on repeat requests)
# via: 1.1 ....cloudfront.net (CloudFront)
```

Open `https://<your DIST_DOMAIN>/` in a browser: the same login screen, now over HTTPS, from a bucket nobody can read directly.

If you get `403` from CloudFront, check in this order: `get-distribution ... Status` says `Deployed`; `aws s3 ls s3://${CF_BUCKET}/` shows files; `aws s3api get-bucket-policy --bucket ${CF_BUCKET}` exists.

---

## Module 6: Pushing Images to ECR

### Log Docker in to ECR

The password is a 12-hour token generated from your IAM identity, never typed or stored:

```bash
aws ecr get-login-password | \
  docker login --username AWS --password-stdin ${REG}
# Login Succeeded
```

### Your repositories

Repository names are unique per account, so they carry your prefix. Your **local** image names (`leap-mission-service`) do not need to change: only the copy in ECR is shared.

```bash
aws ecr create-repository --repository-name ${ME}-mission-service
aws ecr create-repository --repository-name ${ME}-auth-service
```

### Tag and push

Two tags per image. The second push reports `Layer already exists`, because ECR stores layers once and reuses them.

```bash
docker tag leap-mission-service:latest ${REG}/${ME}-mission-service:latest
docker tag leap-mission-service:latest ${REG}/${ME}-mission-service:v1
docker push ${REG}/${ME}-mission-service:latest
docker push ${REG}/${ME}-mission-service:v1

docker tag leap-auth-service:latest ${REG}/${ME}-auth-service:latest
docker tag leap-auth-service:latest ${REG}/${ME}-auth-service:v1
docker push ${REG}/${ME}-auth-service:latest
docker push ${REG}/${ME}-auth-service:v1
```

### What actually landed

```bash
aws ecr describe-images --repository-name ${ME}-mission-service \
  --query 'imageDetails[].imageTags'
# [null, null, ["latest", "v1"]]
```

The two `null` entries are build attestations that newer Docker builders add. Amazon Linux's Docker may not add them, in which case you will only see `["latest", "v1"]`: that is fine. Either way, untagged images pile up over many builds and cost storage, so add a lifecycle policy:

```bash
cat > expire-untagged.json <<'EOF'
{
  "rules": [{
    "rulePriority": 1,
    "description": "Expire untagged images after 1 day",
    "selection": {"tagStatus": "untagged", "countType": "sinceImagePushed",
                  "countUnit": "days", "countNumber": 1},
    "action": {"type": "expire"}
  }]
}
EOF

aws ecr put-lifecycle-policy --repository-name ${ME}-mission-service \
  --lifecycle-policy-text file://expire-untagged.json
aws ecr put-lifecycle-policy --repository-name ${ME}-auth-service \
  --lifecycle-policy-text file://expire-untagged.json
```

### Which CPU architecture did you build?

Docker builds for the CPU of the machine it runs on. Most EC2 instance types (`t3`, `m5`) are Intel or AMD, so you get `amd64`; Graviton types (`t4g`, `m7g`) and Apple Silicon Macs give `arm64`. Fargate needs to be told which, so record it now:

```bash
ARCH=$(docker image inspect leap-mission-service:latest --format '{{.Architecture}}')
if [ "$ARCH" = "arm64" ]; then CPU_ARCH=ARM64; else CPU_ARCH=X86_64; fi
echo "export CPU_ARCH=${CPU_ARCH}" >> ~/leap-env.sh
echo ${ARCH} ${CPU_ARCH}
```

### Prove the pushed image is complete

Delete every local tag of the image, pull it back from ECR, and run it:

```bash
docker rmi ${REG}/${ME}-mission-service:latest ${REG}/${ME}-mission-service:v1 leap-mission-service:latest
docker pull ${REG}/${ME}-mission-service:latest
docker run -d --name ecr-test -p 18090:8080 ${REG}/${ME}-mission-service:latest

sleep 30; docker logs ecr-test | grep Started
# Started MissionServiceApplication in ... seconds
```

If `rmi` says a container is using the image, remove that container first (`docker ps -a`, then `docker rm -f <id>`). The digest printed by `pull` matches the image you built, which proves it is the same image.

This container runs **on your EC2 instance's own Docker**, not on ECS. The ECR address in its name only says where it was pulled from. Running it as a managed AWS task starts in Module 7.

```bash
docker rm -f ecr-test
docker tag ${REG}/${ME}-mission-service:latest leap-mission-service:latest
```

---

## Module 7: ECS - Cluster, Task Definition and a One-Off Task

### Cluster and log group

```bash
aws ecs create-cluster --cluster-name ${ME}-mission-cluster \
  --query 'cluster.clusterName' --output text

aws logs create-log-group --log-group-name /ecs/${ME}-mission-service
```

`create-cluster` does **not** fail if the name already exists; it silently hands you the existing cluster. That is why the prefix matters. The log group must exist before a task starts, or the task fails with "log group does not exist".

### Execution role

The identity ECS itself uses to pull your image and write logs:

```bash
aws iam create-role --role-name ${ME}-ecs-task-execution-role \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[
    {"Effect":"Allow","Principal":{"Service":"ecs-tasks.amazonaws.com"},
     "Action":"sts:AssumeRole"}]}' \
  --query 'Role.Arn' --output text

aws iam attach-role-policy --role-name ${ME}-ecs-task-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

### Task definition

The architecture comes from Module 6:

```bash
cat > task-def.json <<EOF
{
  "family": "${ME}-mission-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256", "memory": "512",
  "runtimePlatform": {"cpuArchitecture": "${CPU_ARCH}", "operatingSystemFamily": "LINUX"},
  "executionRoleArn": "arn:aws:iam::${ACCOUNT_ID}:role/${ME}-ecs-task-execution-role",
  "containerDefinitions": [{
    "name": "mission-service",
    "image": "${REG}/${ME}-mission-service:latest",
    "portMappings": [{"containerPort": 8080, "protocol": "tcp"}],
    "logConfiguration": {"logDriver": "awslogs", "options": {
      "awslogs-group": "/ecs/${ME}-mission-service",
      "awslogs-region": "${AWS_REGION}", "awslogs-stream-prefix": "ecs"}}
  }]
}
EOF

grep -E 'executionRoleArn|image|cpuArchitecture' task-def.json
```

Check before registering: you should see `:role/`, your 12-digit account number, and `ARM64` or `X86_64`. If you see a literal `$ACCOUNT_ID` or `ole/` instead of `:role/`, you are missing the braces or the environment file.

```bash
aws ecs register-task-definition --cli-input-json file://task-def.json \
  --query 'taskDefinition.{Family:family,Revision:revision,Status:status}'
# Revision 1, ACTIVE
```

If you get `Role is not valid` and the ARN looks right, the role is only seconds old: wait 30 seconds and try again.

Confirm the image matches the architecture you declared:

```bash
docker manifest inspect ${REG}/${ME}-mission-service:latest | grep architecture
```

A mismatch makes the task fail with `exec format error` in its logs.

### Run it in a public subnet

A security group open on 8080, for testing only:

```bash
PUBLIC_SG=$(aws ec2 create-security-group --group-name ${ME}-ecs-public-sg \
  --description "${ME} public ingress for run-task testing" \
  --vpc-id ${VPC_ID} --query GroupId --output text)

aws ec2 authorize-security-group-ingress --group-id ${PUBLIC_SG} \
  --protocol tcp --port 8080 --cidr 0.0.0.0/0

echo "export PUBLIC_SG=${PUBLIC_SG}" >> ~/leap-env.sh
```

Run one task, using one of the **public** subnets from Module 2:

```bash
TASK_ARN=$(aws ecs run-task --cluster ${ME}-mission-cluster \
  --task-definition ${ME}-mission-service --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[${PUB_A}],securityGroups=[${PUBLIC_SG}],assignPublicIp=ENABLED}" \
  --query 'tasks[0].taskArn' --output text)
echo ${TASK_ARN}

aws ecs wait tasks-running --cluster ${ME}-mission-cluster --tasks ${TASK_ARN}
```

> **About `wait` commands:** they print nothing. They sit silently until the condition is met, then hand you back the prompt. Getting the prompt back with no message means success. They only print something if they fail.

Check it, and if it stopped, why:

```bash
aws ecs describe-tasks --cluster ${ME}-mission-cluster --tasks ${TASK_ARN} \
  --query 'tasks[0].{Status:lastStatus,Reason:stoppedReason,Exit:containers[0].exitCode}'
```

### Logs and a real request

```bash
aws logs tail /ecs/${ME}-mission-service --since 10m | grep Started

ENI_ID=$(aws ecs describe-tasks --cluster ${ME}-mission-cluster --tasks ${TASK_ARN} \
  --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" --output text)
TASK_IP=$(aws ec2 describe-network-interfaces --network-interface-ids ${ENI_ID} \
  --query 'NetworkInterfaces[0].Association.PublicIp' --output text)
echo ${TASK_IP}

curl -i -m 10 http://${TASK_IP}:8080/actuator/health
# HTTP/1.1 401 ... WWW-Authenticate: Bearer
```

Spring takes 20 to 40 seconds to start on Fargate, so if `curl` gets nothing straight away, wait until the logs show `Started` and try again. If it still hangs, check the subnet really is public (the Module 2 loop) and that `PUBLIC_SG` allows 8080.

The container is now running **on Fargate**: same image, same `401`, no machine of yours involved. Stop it so it stops billing:

```bash
aws ecs stop-task --cluster ${ME}-mission-cluster --task ${TASK_ARN} >/dev/null
```

---

## Module 8: A Real Backend - Private Subnets, Load Balancer and ECS Service

The shared VPC endpoints and private route table already exist (see the ground rules), so you **skip** the guide's endpoint and route-table commands. Everything else you build yourself.

### Your app security group

This is the group your service's tasks will carry. It starts with no inbound rules at all:

```bash
APP_SG=$(aws ec2 create-security-group --group-name ${ME}-app-sg \
  --description "${ME} mission-service tasks" \
  --vpc-id ${VPC_ID} --query GroupId --output text)
echo "export APP_SG=${APP_SG}" >> ~/leap-env.sh
```

Confirm the shared endpoints are there and available:

```bash
aws ec2 describe-vpc-endpoints --filters Name=vpc-id,Values=${VPC_ID} \
  --query 'VpcEndpoints[].[ServiceName,State,PrivateDnsEnabled]' --output table
```

You should see `ecr.api`, `ecr.dkr`, `logs` and `secretsmanager` (`Interface`, private DNS `True`) and `s3`.

### The load balancer

Its own security group, open to the internet on port 80:

```bash
ALB_SG=$(aws ec2 create-security-group --group-name ${ME}-alb-sg \
  --description "${ME} public ALB" \
  --vpc-id ${VPC_ID} --query GroupId --output text)

aws ec2 authorize-security-group-ingress --group-id ${ALB_SG} \
  --protocol tcp --port 80 --cidr 0.0.0.0/0

echo "export ALB_SG=${ALB_SG}" >> ~/leap-env.sh
```

A target group that tracks tasks by IP address:

```bash
TG_ARN=$(aws elbv2 create-target-group --name ${ME}-mission-tg --protocol HTTP --port 8080 \
  --vpc-id ${VPC_ID} --target-type ip --health-check-path /actuator/health \
  --query 'TargetGroups[0].TargetGroupArn' --output text)
echo "export TG_ARN=${TG_ARN}" >> ~/leap-env.sh
```

The load balancer itself, in the two **public** subnets, and a listener on port 80:

```bash
ALB_ARN=$(aws elbv2 create-load-balancer --name ${ME}-mission-alb --type application \
  --subnets ${PUB_A} ${PUB_B} --security-groups ${ALB_SG} \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)
echo "export ALB_ARN=${ALB_ARN}" >> ~/leap-env.sh
echo "export ALB_DNS=$(aws elbv2 describe-load-balancers --load-balancer-arns ${ALB_ARN} --query 'LoadBalancers[0].DNSName' --output text)" >> ~/leap-env.sh
source ~/leap-env.sh

aws elbv2 create-listener --load-balancer-arn ${ALB_ARN} --protocol HTTP --port 80 \
  --default-actions Type=forward,TargetGroupArn=${TG_ARN} \
  --query 'Listeners[0].ListenerArn' --output text
```

The load balancer is billed by the hour from this point.

### The health check problem

The target group expects `200` by default, but `/actuator/health` returns `401` because Spring Security protects every path. Rather than treat a healthy app as broken, widen the matcher:

```bash
aws elbv2 modify-target-group --target-group-arn ${TG_ARN} --matcher '{"HttpCode":"200,401"}'
```

Also shorten how long old tasks are drained during a deployment, from 5 minutes to 30 seconds, so deployments in class do not drag:

```bash
aws elbv2 modify-target-group-attributes --target-group-arn ${TG_ARN} \
  --attributes Key=deregistration_delay.timeout_seconds,Value=30
```

> **Think about it:** the load balancer is now treating "authentication failed" as "healthy". What would you change in the application, rather than the load balancer, to make this cleaner?

Finally, let the load balancer (and only the load balancer) into the app tier:

```bash
aws ec2 authorize-security-group-ingress --group-id ${APP_SG} --protocol tcp --port 8080 \
  --source-group ${ALB_SG}
```

### The ECS service

Two tasks, in the **private** subnets, with no public IP. `--health-check-grace-period-seconds` gives Spring time to boot before the load balancer judges it:

```bash
aws ecs create-service --cluster ${ME}-mission-cluster --service-name ${ME}-mission-service \
  --task-definition ${ME}-mission-service:1 --desired-count 2 --launch-type FARGATE \
  --health-check-grace-period-seconds 90 \
  --network-configuration "awsvpcConfiguration={subnets=[${PRIV_A},${PRIV_B}],securityGroups=[${APP_SG}],assignPublicIp=DISABLED}" \
  --load-balancers targetGroupArn=${TG_ARN},containerName=mission-service,containerPort=8080 \
  --query 'service.{Name:serviceName,Status:status,Desired:desiredCount}'

aws ecs wait services-stable --cluster ${ME}-mission-cluster --services ${ME}-mission-service
```

This usually takes 2 to 5 minutes, silently. If you want to watch, in another terminal:

```bash
source ~/leap-env.sh
aws ecs describe-services --cluster ${ME}-mission-cluster --services ${ME}-mission-service \
  --query 'services[0].{Running:runningCount,Desired:desiredCount,Events:events[:3].message}'
```

### Verify end to end

```bash
aws elbv2 describe-target-health --target-group-arn ${TG_ARN} \
  --query 'TargetHealthDescriptions[].[Target.Id,TargetHealth.State]' --output table
# two targets, healthy

curl -i http://${ALB_DNS}/actuator/health
# HTTP/1.1 401
```

That `401` travelled from the internet, through your public load balancer, to a task in a private subnet that has no public IP and pulled its image without touching the internet.

Logs arrive over the shared `logs` endpoint:

```bash
aws logs tail /ecs/${ME}-mission-service --since 15m | grep Started
```

### A rolling deployment with no downtime

You need **two terminals** for this. Terminal 2 is just a window to watch.

**Terminal 2** - a request every second through the load balancer:

```bash
source ~/leap-env.sh
while true; do curl -s -o /dev/null -w "%{http_code} " http://${ALB_DNS}/actuator/health; sleep 1; done
```

**Terminal 1** - start the deployment and wait for it:

```bash
aws ecs update-service --cluster ${ME}-mission-cluster --service ${ME}-mission-service \
  --force-new-deployment --query 'service.serviceName' --output text

aws ecs wait services-stable --cluster ${ME}-mission-cluster --services ${ME}-mission-service
```

While Terminal 1 sits silently, Terminal 2 should show an unbroken row of `401`. ECS starts new tasks, waits for them to pass health checks, and only then drains and stops the old ones. When Terminal 1 gives you the prompt back, press **Ctrl+C** in Terminal 2.

A `000` in Terminal 2 means that terminal has not loaded your environment file (`ALB_DNS` is empty).

---

## Module 9: RDS and Secrets Manager

### A private database

A subnet group from the two **private** subnets:

```bash
aws rds create-db-subnet-group --db-subnet-group-name ${ME}-mission-db-subnets \
  --db-subnet-group-description "${ME} private subnets for the mission database" \
  --subnet-ids ${PRIV_A} ${PRIV_B} \
  --query 'DBSubnetGroup.{Name:DBSubnetGroupName,Status:SubnetGroupStatus}'
```

A security group that allows Postgres **only from your app tier**, not from any IP range:

```bash
RDS_SG=$(aws ec2 create-security-group --group-name ${ME}-rds-sg \
  --description "${ME} RDS, 5432 from app tier only" \
  --vpc-id ${VPC_ID} --query GroupId --output text)

aws ec2 authorize-security-group-ingress --group-id ${RDS_SG} --protocol tcp \
  --port 5432 --source-group ${APP_SG}

echo "export RDS_SG=${RDS_SG}" >> ~/leap-env.sh
```

The instance itself. `--manage-master-user-password` means AWS generates the password and stores it in Secrets Manager: it never appears in a command, a script or your terminal history.

```bash
PG_VERSION=$(aws rds describe-db-engine-versions --engine postgres --default-only \
  --query 'DBEngineVersions[0].EngineVersion' --output text)
echo ${PG_VERSION}

aws rds create-db-instance --db-instance-identifier ${ME}-mission-db \
  --db-instance-class db.t3.micro --engine postgres --engine-version ${PG_VERSION} \
  --allocated-storage 20 --storage-type gp3 --master-username missionadmin \
  --manage-master-user-password \
  --db-subnet-group-name ${ME}-mission-db-subnets \
  --vpc-security-group-ids ${RDS_SG} --no-publicly-accessible \
  --backup-retention-period 0 --no-multi-az \
  --query 'DBInstance.{Id:DBInstanceIdentifier,Status:DBInstanceStatus}'

aws rds wait db-instance-available --db-instance-identifier ${ME}-mission-db
```

The wait takes 5 to 10 minutes, silently.

### The secret

```bash
echo "export SECRET_ARN=$(aws rds describe-db-instances --db-instance-identifier ${ME}-mission-db --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)" >> ~/leap-env.sh
echo "export RDS_HOST=$(aws rds describe-db-instances --db-instance-identifier ${ME}-mission-db --query 'DBInstances[0].Endpoint.Address' --output text)" >> ~/leap-env.sh
source ~/leap-env.sh
echo ${SECRET_ARN}
echo ${RDS_HOST}

aws secretsmanager describe-secret --secret-id ${SECRET_ARN} \
  --query '{Name:Name,LastRotated:LastRotatedDate}'
```

The secret's name starts with `rds!db-`. You can see it exists without ever seeing the password.

> **Secrets Manager or Parameter Store?** Parameter Store's standard tier is free but has no automatic rotation. Secrets Manager costs about $0.40 per secret per month and can rotate a database password on a schedule. Use Parameter Store for plain configuration; use Secrets Manager for credentials.

### Let ECS read the secret

Reading a secret into a container is the **execution role's** job, like pulling the image. Grant it access to this one secret only:

```bash
aws iam put-role-policy --role-name ${ME}-ecs-task-execution-role \
  --policy-name ${ME}-read-db-secret \
  --policy-document "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"secretsmanager:GetSecretValue\",\"Resource\":\"${SECRET_ARN}\"}]}"

aws iam get-role-policy --role-name ${ME}-ecs-task-execution-role \
  --policy-name ${ME}-read-db-secret --query 'PolicyDocument.Statement[0].Resource' --output text
```

The second command prints your secret's ARN. `put-role-policy` itself prints nothing on success.

### A task definition that uses the database

Two details matter here:

- The secret holds JSON (`{"username": ..., "password": ...}`). Ending the reference with `:password::` injects just the password.
- The mission service reads Spring's standard `SPRING_DATASOURCE_*` variables. The `DB_*` variables are included to match the demo, but only the `SPRING_*` ones change what the app connects to.

```bash
cat > task-def-rds.json <<EOF
{
  "family": "${ME}-mission-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256", "memory": "512",
  "runtimePlatform": {"cpuArchitecture": "${CPU_ARCH}", "operatingSystemFamily": "LINUX"},
  "executionRoleArn": "arn:aws:iam::${ACCOUNT_ID}:role/${ME}-ecs-task-execution-role",
  "containerDefinitions": [{
    "name": "mission-service",
    "image": "${REG}/${ME}-mission-service:latest",
    "portMappings": [{"containerPort": 8080, "protocol": "tcp"}],
    "secrets": [
      {"name": "DB_PASSWORD", "valueFrom": "${SECRET_ARN}:password::"},
      {"name": "SPRING_DATASOURCE_PASSWORD", "valueFrom": "${SECRET_ARN}:password::"}
    ],
    "environment": [
      {"name": "DB_HOST", "value": "${RDS_HOST}"},
      {"name": "DB_PORT", "value": "5432"},
      {"name": "DB_NAME", "value": "postgres"},
      {"name": "DB_USERNAME", "value": "missionadmin"},
      {"name": "SPRING_DATASOURCE_URL", "value": "jdbc:postgresql://${RDS_HOST}:5432/postgres"},
      {"name": "SPRING_DATASOURCE_USERNAME", "value": "missionadmin"}
    ],
    "logConfiguration": {"logDriver": "awslogs", "options": {
      "awslogs-group": "/ecs/${ME}-mission-service",
      "awslogs-region": "${AWS_REGION}", "awslogs-stream-prefix": "ecs"}}
  }]
}
EOF

grep -E 'executionRoleArn|valueFrom|DATASOURCE_URL' task-def-rds.json
```

Check for `:role/`, two lines ending `:password::`, and your RDS host name. Then register it:

```bash
aws ecs register-task-definition --cli-input-json file://task-def-rds.json \
  --query 'taskDefinition.{Family:family,Revision:revision}'
# Revision 2
```

### Verify the network path to the database

A one-off task, carrying your app security group in a private subnet, that just tests TCP to port 5432. It reuses your own mission-service image (which includes `nc`), because the private subnets cannot pull from Docker Hub:

```bash
cat > connectivity-check-taskdef.json <<EOF
{
  "family": "${ME}-db-connectivity-check",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256", "memory": "512",
  "runtimePlatform": {"cpuArchitecture": "${CPU_ARCH}", "operatingSystemFamily": "LINUX"},
  "executionRoleArn": "arn:aws:iam::${ACCOUNT_ID}:role/${ME}-ecs-task-execution-role",
  "containerDefinitions": [{
    "name": "db-check",
    "image": "${REG}/${ME}-mission-service:latest",
    "essential": true,
    "entryPoint": ["sh", "-c"],
    "command": ["nc -zv -w 5 ${RDS_HOST} 5432 && echo REACHABLE || echo UNREACHABLE"],
    "logConfiguration": {"logDriver": "awslogs", "options": {
      "awslogs-group": "/ecs/${ME}-mission-service",
      "awslogs-region": "${AWS_REGION}", "awslogs-stream-prefix": "dbcheck"}}
  }]
}
EOF

grep -E 'role/|nc -zv' connectivity-check-taskdef.json

aws ecs register-task-definition --cli-input-json file://connectivity-check-taskdef.json \
  --query 'taskDefinition.revision'

CHECK_ARN=$(aws ecs run-task --cluster ${ME}-mission-cluster \
  --task-definition ${ME}-db-connectivity-check --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[${PRIV_A}],securityGroups=[${APP_SG}],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' --output text)

aws ecs wait tasks-stopped --cluster ${ME}-mission-cluster --tasks ${CHECK_ARN}

aws logs tail /ecs/${ME}-mission-service --since 10m --log-stream-name-prefix dbcheck
# ... (10.x.x.x:5432) open
# REACHABLE
```

### Put the service on the new revision

Now point the running service at revision 2, so the real app gets its database settings and secret:

```bash
aws ecs update-service --cluster ${ME}-mission-cluster --service ${ME}-mission-service \
  --task-definition ${ME}-mission-service:2 --query 'service.serviceName' --output text

aws ecs wait services-stable --cluster ${ME}-mission-cluster --services ${ME}-mission-service

curl -i http://${ALB_DNS}/actuator/health
```

If the deployment never settles, look at the events. `unable to retrieve secret from asm` means the task could not reach Secrets Manager: tell your instructor, because that is the shared `secretsmanager` endpoint.

```bash
aws ecs describe-services --cluster ${ME}-mission-cluster --services ${ME}-mission-service \
  --query 'services[0].events[:3].message'
```

---

## Module 10: Observability and Cost

### Metrics ECS already collects

```bash
aws cloudwatch list-metrics --namespace AWS/ECS \
  --query "Metrics[?Dimensions[?Value=='${ME}-mission-cluster']].MetricName"
# ["LiveTaskCount", "CPUUtilization", "MemoryUtilization", ...]
```

### An alarm

```bash
aws cloudwatch put-metric-alarm --alarm-name ${ME}-mission-high-cpu \
  --namespace AWS/ECS --metric-name CPUUtilization \
  --dimensions Name=ClusterName,Value=${ME}-mission-cluster \
  --statistic Average --period 300 --evaluation-periods 2 \
  --threshold 80 --comparison-operator GreaterThanThreshold \
  --treat-missing-data notBreaching

aws cloudwatch describe-alarms --alarm-names ${ME}-mission-high-cpu \
  --query 'MetricAlarms[0].StateValue' --output text
```

A new alarm starts as `INSUFFICIENT_DATA` and moves to `OK` once it has a few data points. `--treat-missing-data notBreaching` stops it going into `ALARM` just because nothing is running.

### A Logs Insights query

Find every startup in the last week. `start-query` only **starts** the query; `get-query-results` reads it:

```bash
QUERY_ID=$(aws logs start-query --log-group-name /ecs/${ME}-mission-service \
  --start-time $(date -v-7d +%s 2>/dev/null || date -d '7 days ago' +%s) --end-time $(date +%s) \
  --query-string 'fields @timestamp, @message
    | filter @message like /Started MissionServiceApplication/
    | sort @timestamp desc | limit 20' \
  --query queryId --output text)

sleep 5
aws logs get-query-results --query-id ${QUERY_ID} \
  --query '{Status:status,Stats:statistics,Rows:results[*][0].value}'
```

If `Status` is `Running`, run the second command again. Leave off `--query` to see the full log lines.

### A dashboard

```bash
cat > dashboard.json <<EOF
{
  "widgets": [
    {"type": "metric", "x": 0, "y": 0, "width": 12, "height": 6,
     "properties": {"title": "CPU / Memory", "region": "${AWS_REGION}", "stat": "Average", "period": 300,
       "metrics": [["AWS/ECS", "CPUUtilization", "ClusterName", "${ME}-mission-cluster"],
                   ["AWS/ECS", "MemoryUtilization", "ClusterName", "${ME}-mission-cluster"]]}},
    {"type": "metric", "x": 12, "y": 0, "width": 12, "height": 6,
     "properties": {"title": "Live tasks", "region": "${AWS_REGION}", "stat": "Maximum", "period": 300,
       "metrics": [["AWS/ECS", "LiveTaskCount", "ClusterName", "${ME}-mission-cluster"]]}},
    {"type": "log", "x": 0, "y": 6, "width": 24, "height": 6,
     "properties": {"title": "Startups", "region": "${AWS_REGION}", "view": "table",
       "query": "SOURCE '/ecs/${ME}-mission-service' | fields @timestamp, @message | filter @message like /Started MissionServiceApplication/ | sort @timestamp desc | limit 20"}}
  ]
}
EOF

aws cloudwatch put-dashboard --dashboard-name ${ME}-mission-observability \
  --dashboard-body file://dashboard.json
# "DashboardValidationMessages": []
```

Open it in the console: **CloudWatch > Dashboards > your dashboard** (region Europe (Ireland)).

### What is this costing? (optional)

Cost Explorer shows the **whole account**, so you will see the entire class's spending, not just yours. Each call costs $0.01, and your user may not have permission:

```bash
aws ce get-cost-and-usage \
  --time-period Start=$(date -v-14d +%Y-%m-%d 2>/dev/null || date -d '14 days ago' +%Y-%m-%d),End=$(date +%Y-%m-%d) \
  --granularity MONTHLY --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE \
  --query 'ResultsByTime[].Groups[].[Keys[0],Metrics.UnblendedCost.Amount]' --output table
```

### A budget (optional)

A budget only alerts; it never stops spending. Put **your own** email address in place of `you@example.com`:

```bash
aws budgets create-budget --account-id ${ACCOUNT_ID} \
  --budget "{\"BudgetName\":\"${ME}-mission-monthly-budget\",\"BudgetLimit\":{\"Amount\":\"10\",\"Unit\":\"USD\"},\"BudgetType\":\"COST\",\"TimeUnit\":\"MONTHLY\"}" \
  --notifications-with-subscribers '[{"Notification":{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":80,"ThresholdType":"PERCENTAGE"},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"you@example.com"}]}]'

aws budgets describe-budgets --account-id ${ACCOUNT_ID} \
  --query "Budgets[?BudgetName=='${ME}-mission-monthly-budget'].BudgetName" --output text
```

Budgets are account-wide, so this alerts on the whole class's spend.

---

## Module 11 (optional): One-Command Deployments

Everything you did by hand in Modules 6 to 9 (build, push, register a new task definition, deploy, verify) can run as a single script that stops at the first failure, the same way a CI/CD pipeline would. The repository includes one:

```bash
if [ "${CPU_ARCH}" = "ARM64" ]; then PLATFORM=linux/arm64; else PLATFORM=linux/amd64; fi

PREFIX=${ME} PLATFORM=${PLATFORM} \
  "${REPO}/leap-sprint11/demos/11-deployment-automation-scripting-the-full-pipeline/deploy.sh"
```

It prints each stage (`Setup`, `Build and Push`, `Render Task Definition`, `Deploy`, `Verify`) and finishes with `== PIPELINE PASSED ==`. The Deploy stage takes several minutes.

Run the Module 8 curl loop in a second terminal while it runs, to watch a new version go live with no downtime. Then check what is live:

```bash
aws ecs describe-services --cluster ${ME}-mission-cluster --services ${ME}-mission-service \
  --query 'services[0].{Revision:taskDefinition,Running:runningCount,Rollout:deployments[0].rolloutState}'

aws ecr describe-images --repository-name ${ME}-mission-service \
  --query 'sort_by(imageDetails,&imagePushedAt)[-3:].{Tags:imageTags,Pushed:imagePushedAt}' --output table
```

---

## Seeing it in the console

Set the region (top right) to **Europe (Ireland) eu-west-1**, then:

| Where | What you will see |
|---|---|
| **ECS > Clusters > your cluster > Services > your service** | **Deployments** tab (old and new side by side during a deploy), **Tasks**, **Events**, **Logs** |
| **EC2 > Target Groups > your target group > Targets** | Tasks moving through `initial`, `healthy`, `draining` |
| **EC2 > Load Balancers** | Your ALB and its DNS name |
| **ECR > Repositories** | Every image and tag you pushed |
| **RDS > Databases** | Your private database |
| **CloudFront > Distributions** | Your distribution and its domain |
| **CloudWatch > Dashboards / Alarms / Log groups** | Module 10's work |

---

## Troubleshooting

| You see | It means | Fix |
|---|---|---|
| `NoRegion` or `Unable to locate credentials` | This terminal has no environment loaded | `source ~/leap-env.sh` |
| `permission denied ... docker.sock` | Your session is not in the `docker` group yet | `newgrp docker`, or log out and back in |
| `no space left on device` during a build | The instance's disk is full | `docker system prune -af`, or use a larger volume |
| A build is killed or the instance freezes | Not enough memory | Use `t3.medium` or larger |
| `get-caller-identity` shows `assumed-role/...` | The CLI is using the instance's role, not your user | `export AWS_PROFILE=training`, or `source ~/leap-env.sh` |
| A command fails with an empty argument, or `curl` prints `000` | A variable is empty in this terminal | `source ~/leap-env.sh`, then `echo` the variable |
| `IllegalLocationConstraintException` | `create-bucket` outside `us-east-1` without a location | Add `--create-bucket-configuration LocationConstraint=eu-west-1` |
| `PermanentRedirect` or `HTTP 400` from S3 | The URL has the wrong region | Use `eu-west-1` in the address |
| `AlreadyExists`, `Duplicate` | You (or someone) already created it | Check you used `${ME}`; if it is yours, look it up instead of creating it |
| `Role is not valid` | The role ARN in your JSON is wrong, or the role is seconds old | `grep executionRoleArn` the file: it must contain `:role/`. Otherwise wait 30 seconds |
| A literal `$ACCOUNT_ID` in a file, or `ole/` instead of `:role/` | zsh mangled `$VAR:...`, or the heredoc was quoted | Use `${VAR}` and `<<EOF` (not `<<'EOF'`), then recreate the file |
| `wait` prints nothing | It worked | Getting the prompt back is success |
| `Waiter ... failed` | The thing you waited for did not happen | Check `describe-tasks` / `describe-services` events for the reason |
| `exec format error` in task logs | Image architecture does not match the task definition | Re-run the Module 6 architecture check and fix `cpuArchitecture` |
| `CannotPullContainerError` or `unable to pull registry auth` | The task cannot reach ECR | Public subnet: check it is really public. Private subnet: tell your instructor (shared endpoints) |
| `unable to retrieve secret from asm` | The task cannot reach Secrets Manager | Tell your instructor (shared endpoint) |
| `Task failed ELB health checks` | The load balancer killed a task before Spring started | Normal once or twice during a deploy; ECS replaces it. If it repeats, check `--health-check-grace-period-seconds` and the `200,401` matcher |
| `curl` to a task's public IP hangs | No route from the internet, or the port is closed | Check the subnet is public and the security group allows the port |
| `--max-items` output ends in `None` | The CLI adds a pagination line in text mode | Use `--limit` instead |

---

## Teardown: remove everything you created

Run these **in order** from a terminal that has done `source ~/leap-env.sh`. Several steps wait for the previous one to finish; let each one return before moving on.

**1. ECS service and tasks**

```bash
aws ecs update-service --cluster ${ME}-mission-cluster --service ${ME}-mission-service \
  --desired-count 0 --query 'service.serviceName' --output text
aws ecs delete-service --cluster ${ME}-mission-cluster --service ${ME}-mission-service --force \
  --query 'service.status' --output text
aws ecs wait services-inactive --cluster ${ME}-mission-cluster --services ${ME}-mission-service

for t in $(aws ecs list-tasks --cluster ${ME}-mission-cluster --query 'taskArns[]' --output text); do
  aws ecs stop-task --cluster ${ME}-mission-cluster --task $t >/dev/null
done
```

**2. Load balancer and target group**

```bash
aws elbv2 delete-load-balancer --load-balancer-arn ${ALB_ARN}
aws elbv2 wait load-balancers-deleted --load-balancer-arns ${ALB_ARN}
aws elbv2 delete-target-group --target-group-arn ${TG_ARN}
```

**3. Database** (its secret is deleted with it)

```bash
aws rds delete-db-instance --db-instance-identifier ${ME}-mission-db \
  --skip-final-snapshot --delete-automated-backups --query 'DBInstance.DBInstanceStatus' --output text
aws rds wait db-instance-deleted --db-instance-identifier ${ME}-mission-db
aws rds delete-db-subnet-group --db-subnet-group-name ${ME}-mission-db-subnets
```

**4. Cluster, logs, role and repositories**

```bash
aws ecs delete-cluster --cluster ${ME}-mission-cluster --query 'cluster.status' --output text
aws logs delete-log-group --log-group-name /ecs/${ME}-mission-service

aws iam delete-role-policy --role-name ${ME}-ecs-task-execution-role --policy-name ${ME}-read-db-secret
aws iam detach-role-policy --role-name ${ME}-ecs-task-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
aws iam delete-role --role-name ${ME}-ecs-task-execution-role

aws ecr delete-repository --repository-name ${ME}-mission-service --force --query 'repository.repositoryName' --output text
aws ecr delete-repository --repository-name ${ME}-auth-service --force --query 'repository.repositoryName' --output text
```

**5. CloudFront** (a distribution must be disabled, and that change deployed, before it can be deleted)

```bash
aws cloudfront get-distribution-config --id ${DIST_ID} > dist-current.json
python3 - <<'PY'
import json
d = json.load(open("dist-current.json"))
cfg = d["DistributionConfig"]; cfg["Enabled"] = False
json.dump(cfg, open("dist-disabled.json", "w"))
open("dist-etag.txt", "w").write(d["ETag"])
PY
aws cloudfront update-distribution --id ${DIST_ID} --if-match $(cat dist-etag.txt) \
  --distribution-config file://dist-disabled.json --query 'Distribution.Status' --output text
aws cloudfront wait distribution-deployed --id ${DIST_ID}

aws cloudfront delete-distribution --id ${DIST_ID} \
  --if-match $(aws cloudfront get-distribution --id ${DIST_ID} --query ETag --output text)
aws cloudfront delete-origin-access-control --id ${OAC_ID} \
  --if-match $(aws cloudfront get-origin-access-control --id ${OAC_ID} --query ETag --output text)
```

Disabling takes several minutes. If `delete-origin-access-control` says the OAC is in use, wait a minute and run it again.

**6. Buckets**

```bash
aws s3 rb s3://${SITE_BUCKET} --force
aws s3 rb s3://${CF_BUCKET} --force
```

**7. Alarm, dashboard and budget**

```bash
aws cloudwatch delete-alarms --alarm-names ${ME}-mission-high-cpu
aws cloudwatch delete-dashboards --dashboard-names ${ME}-mission-observability
aws budgets delete-budget --account-id ${ACCOUNT_ID} --budget-name ${ME}-mission-monthly-budget 2>/dev/null
```

**8. Security groups** (last, because the others reference them; if one says `DependencyViolation`, wait a minute for network interfaces to disappear and try again)

```bash
for sg in ${RDS_SG} ${APP_SG} ${ALB_SG} ${PUBLIC_SG} ${EXAMPLE_APP_SG} ${WEB_SG}; do
  echo "deleting $sg"; aws ec2 delete-security-group --group-id $sg
done
```

**9. Check nothing is left**

```bash
aws ec2 describe-security-groups --filters "Name=group-name,Values=${ME}-*" --query 'SecurityGroups[].GroupName'
aws ecs list-clusters --query "clusterArns[?contains(@, '${ME}')]"
aws rds describe-db-instances --query "DBInstances[?starts_with(DBInstanceIdentifier, '${ME}')].DBInstanceIdentifier"
aws elbv2 describe-load-balancers --query "LoadBalancers[?starts_with(LoadBalancerName, '${ME}')].LoadBalancerName"
```

Every list should come back empty (`[]`). Finally, remove your local files if you want to: `rm -rf ~/leap-work ~/leap-env.sh`.

**10. Your EC2 instance**

If you launched your own instance, and your instructor has said you will not need it again, terminate it from the console (**EC2 > Instances > your `${ME}-workstation` > Instance state > Terminate**). A stopped instance no longer bills for compute, but its disk still costs a little each month.