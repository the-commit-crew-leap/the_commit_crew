#!/usr/bin/env bash
set -Eeuo pipefail

# The Commit Crew: AWS ECS/Fargate deployment
# Run from the repository root with an AWS CLI profile authorized to provision
# ECS, ECR, EC2 networking, ELB, IAM, Cloud Map, CloudWatch Logs, and Secrets Manager.

AWS_REGION="${AWS_REGION:-eu-west-1}"
CLUSTER="${CLUSTER:-commitcrew-prod}"
STACK="${STACK:-commitcrew-prod}"

VPC_ID="${VPC_ID:-vpc-07c5d2a3f09dffc30}"
PUBLIC_SUBNETS=(
  "${PUBLIC_SUBNET_A:-subnet-048ea092ac9ad358b}"
  "${PUBLIC_SUBNET_B:-subnet-05c0d4425955ba550}"
)
PRIVATE_SUBNETS=(
  "${PRIVATE_SUBNET_A:-subnet-05ab71181c3288f8f}"
  "${PRIVATE_SUBNET_B:-subnet-084c7b53a1d0554d0}"
)
RDS_SECURITY_GROUP="${RDS_SECURITY_GROUP:-sg-003996f17e1bdb938}"

TRADING_DB_NAME="${TRADING_DB_NAME:-the-commit-crew-prod}"
AUTH_DB_NAME="${AUTH_DB_NAME:-auth_db-prod}"
RDS_SECRET_ARN="${RDS_SECRET_ARN:?Set RDS_SECRET_ARN to the RDS login secret ARN}"
JWT_SECRET_ARN="${JWT_SECRET_ARN:?Set JWT_SECRET_ARN to a secret containing the JSON field jwtSecret}"

SERVICE_COUNT="${SERVICE_COUNT:-1}"
NAMESPACE_NAME="${NAMESPACE_NAME:-commitcrew.local}"
LOG_PREFIX="/ecs/${STACK}"

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

for tool in aws docker jq; do require "$tool"; done
[[ -f docker-compose.yaml ]] || { echo "Run this from the repository root." >&2; exit 1; }
[[ -f db/seeds/trading-db/price_history-data.sql ]] || {
  echo "Missing generated price-history SQL seed." >&2
  exit 1
}

aws sts get-caller-identity >/dev/null
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M%S)}"

echo "Deploying ${STACK} to AWS account ${ACCOUNT_ID}, region ${AWS_REGION}."
echo "Database names: ${TRADING_DB_NAME}, ${AUTH_DB_NAME}"
echo "WARNING: two NAT gateways, an ALB, Fargate tasks, and CloudWatch logs incur charges."

# Read only the RDS endpoint from its secret; never print the secret value.
RDS_HOST="$(
  aws secretsmanager get-secret-value \
    --region "$AWS_REGION" \
    --secret-id "$RDS_SECRET_ARN" \
    --query SecretString --output text |
    jq -er '.host'
)"

RDS_USER_REF="${RDS_SECRET_ARN}:username::"
RDS_PASSWORD_REF="${RDS_SECRET_ARN}:password::"
JWT_REF="${JWT_SECRET_ARN}:jwtSecret::"

ensure_repo() {
  local repo="$1"
  aws ecr describe-repositories --region "$AWS_REGION" \
    --repository-names "$repo" >/dev/null 2>&1 ||
    aws ecr create-repository --region "$AWS_REGION" \
      --repository-name "$repo" \
      --image-scanning-configuration scanOnPush=true >/dev/null
}

push_image() {
  local repo="$1" dockerfile="$2" context="$3" image="${ECR_REGISTRY}/${repo}:${IMAGE_TAG}"
  ensure_repo "$repo"
  docker build --platform linux/amd64 -f "$dockerfile" -t "$image" "$context"
  docker push "$image"
  printf '%s' "$image"
}

aws ecr get-login-password --region "$AWS_REGION" |
  docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "Building and pushing images..."
AUTH_IMAGE="$(push_image commitcrew-auth auth/Dockerfile .)"
APP_IMAGE="$(push_image commitcrew-app Dockerfile .)"
FRONTEND_IMAGE="$(push_image commitcrew-frontend frontend/Dockerfile .)"
ANALYTICS_IMAGE="$(push_image commitcrew-analytics analytics/Dockerfile .)"
ENGINE_IMAGE="$(push_image commitcrew-execution-engine execution-engine/Dockerfile .)"

# Mirror Kafka into this account's ECR as well.
KAFKA_REPO="commitcrew-kafka"
ensure_repo "$KAFKA_REPO"
KAFKA_IMAGE="${ECR_REGISTRY}/${KAFKA_REPO}:${IMAGE_TAG}"
docker pull --platform linux/amd64 apache/kafka:4.3.1
docker tag apache/kafka:4.3.1 "$KAFKA_IMAGE"
docker push "$KAFKA_IMAGE"

# Build a one-off image containing the existing idempotent seed SQL.
SEED_REPO="commitcrew-price-history-import"
ensure_repo "$SEED_REPO"
SEED_IMAGE="${ECR_REGISTRY}/${SEED_REPO}:${IMAGE_TAG}"
docker build --platform linux/amd64 -f- -t "$SEED_IMAGE" . <<'DOCKERFILE'
FROM postgres:15-alpine
COPY db/seeds/trading-db/price_history-data.sql /seed.sql
DOCKERFILE
docker push "$SEED_IMAGE"

# Find or create security groups.
find_or_create_sg() {
  local name="$1" id
  id="$(aws ec2 describe-security-groups --region "$AWS_REGION" \
    --filters "Name=vpc-id,Values=${VPC_ID}" "Name=group-name,Values=${name}" \
    --query 'SecurityGroups[0].GroupId' --output text)"
  if [[ "$id" == "None" || -z "$id" ]]; then
    id="$(aws ec2 create-security-group --region "$AWS_REGION" \
      --group-name "$name" --description "$name" --vpc-id "$VPC_ID" \
      --query GroupId --output text)"
  fi
  printf '%s' "$id"
}

ALB_SG="$(find_or_create_sg "${STACK}-alb-sg")"
TASK_SG="$(find_or_create_sg "${STACK}-tasks-sg")"

aws ec2 authorize-security-group-ingress --region "$AWS_REGION" \
  --group-id "$ALB_SG" --protocol tcp --port 80 --cidr 0.0.0.0/0 \
  >/dev/null 2>&1 || true

# Service-to-service traffic only within the ECS task security group.
aws ec2 authorize-security-group-ingress --region "$AWS_REGION" \
  --group-id "$TASK_SG" --protocol -1 --source-group "$TASK_SG" \
  >/dev/null 2>&1 || true

# Allow the ALB to reach the frontend tasks.
aws ec2 authorize-security-group-ingress --region "$AWS_REGION" \
  --group-id "$TASK_SG" --protocol tcp --port 80 --source-group "$ALB_SG" \
  >/dev/null 2>&1 || true

# Check that the supplied public subnets already have an Internet Gateway route.
for subnet in "${PUBLIC_SUBNETS[@]}"; do
  az="$(aws ec2 describe-subnets --region "$AWS_REGION" --subnet-ids "$subnet" \
    --query 'Subnets[0].AvailabilityZone' --output text)"
  [[ -n "$az" && "$az" != "None" ]] || { echo "Invalid public subnet: $subnet" >&2; exit 1; }
done

# Create one NAT gateway and a dedicated private route table for each subnet pair.
for i in 0 1; do
  pub="${PUBLIC_SUBNETS[$i]}"
  priv="${PRIVATE_SUBNETS[$i]}"
  nat_name="${STACK}-nat-${i}"
  rt_name="${STACK}-private-rt-${i}"

  nat_id="$(aws ec2 describe-nat-gateways --region "$AWS_REGION" \
    --filter "Name=vpc-id,Values=${VPC_ID}" "Name=tag:Name,Values=${nat_name}" \
    --query 'NatGateways[?State==`available`][0].NatGatewayId' --output text)"

  if [[ "$nat_id" == "None" || -z "$nat_id" ]]; then
    eip_alloc="$(aws ec2 allocate-address --region "$AWS_REGION" \
      --domain vpc --query AllocationId --output text)"
    nat_id="$(aws ec2 create-nat-gateway --region "$AWS_REGION" \
      --subnet-id "$pub" --allocation-id "$eip_alloc" \
      --tag-specifications "ResourceType=natgateway,Tags=[{Key=Name,Value=${nat_name}}]" \
      --query NatGateway.NatGatewayId --output text)"
    aws ec2 wait nat-gateway-available --region "$AWS_REGION" \
      --nat-gateway-ids "$nat_id"
  fi

  rt_id="$(aws ec2 describe-route-tables --region "$AWS_REGION" \
    --filters "Name=vpc-id,Values=${VPC_ID}" "Name=tag:Name,Values=${rt_name}" \
    --query 'RouteTables[0].RouteTableId' --output text)"
  if [[ "$rt_id" == "None" || -z "$rt_id" ]]; then
    rt_id="$(aws ec2 create-route-table --region "$AWS_REGION" \
      --vpc-id "$VPC_ID" --tag-specifications \
      "ResourceType=route-table,Tags=[{Key=Name,Value=${rt_name}}]" \
      --query RouteTable.RouteTableId --output text)"
  fi

  aws ec2 create-route --region "$AWS_REGION" --route-table-id "$rt_id" \
    --destination-cidr-block 0.0.0.0/0 --nat-gateway-id "$nat_id" \
    >/dev/null 2>&1 ||
  aws ec2 replace-route --region "$AWS_REGION" --route-table-id "$rt_id" \
    --destination-cidr-block 0.0.0.0/0 --nat-gateway-id "$nat_id" \
    >/dev/null

  association="$(aws ec2 describe-route-tables --region "$AWS_REGION" \
    --filters "Name=association.subnet-id,Values=${priv}" \
    --query 'RouteTables[0].Associations[?SubnetId==`'"$priv"'`].RouteTableAssociationId | [0]' \
    --output text 2>/dev/null || true)"
  if [[ "$association" == "None" || -z "$association" ]]; then
    aws ec2 associate-route-table --region "$AWS_REGION" \
      --route-table-id "$rt_id" --subnet-id "$priv" >/dev/null
  else
    aws ec2 replace-route-table-association --region "$AWS_REGION" \
      --association-id "$association" --route-table-id "$rt_id" >/dev/null
  fi
done

# RDS access is intentionally not changed by this script.
echo
echo "Before continuing, add this inbound rule to RDS security group ${RDS_SECURITY_GROUP}:"
echo "  TCP 5432, source security group ${TASK_SG}"
echo "Equivalent AWS CLI command:"
echo "  aws ec2 authorize-security-group-ingress --region ${AWS_REGION} --group-id ${RDS_SECURITY_GROUP} --protocol tcp --port 5432 --source-group ${TASK_SG}"
read -r -p "After applying that rule, press Enter to continue (Ctrl-C to abort): " _

# ECS cluster.
aws ecs describe-clusters --region "$AWS_REGION" --clusters "$CLUSTER" \
  --query 'clusters[?status==`ACTIVE`].clusterArn' --output text |
  grep -q . || aws ecs create-cluster --region "$AWS_REGION" \
    --cluster-name "$CLUSTER" --settings name=containerInsights,value=enabled >/dev/null

# Cloud Map private DNS namespace for ECS Service Connect.
NAMESPACE_ARN="$(aws servicediscovery list-namespaces --region "$AWS_REGION" \
  --query "Namespaces[?Name=='${NAMESPACE_NAME}'].Arn | [0]" --output text)"
if [[ "$NAMESPACE_ARN" == "None" || -z "$NAMESPACE_ARN" ]]; then
  operation="$(aws servicediscovery create-private-dns-namespace \
    --region "$AWS_REGION" --name "$NAMESPACE_NAME" --vpc "$VPC_ID" \
    --query OperationId --output text)"
  until [[ "$(aws servicediscovery get-operation --region "$AWS_REGION" \
    --operation-id "$operation" --query Operation.Status --output text)" == "SUCCESS" ]]; do
    sleep 5
  done
  NAMESPACE_ARN="$(aws servicediscovery list-namespaces --region "$AWS_REGION" \
    --query "Namespaces[?Name=='${NAMESPACE_NAME}'].Arn | [0]" --output text)"
fi

# ECS task execution role, with permissions to pull images, write logs, and read secrets.
ROLE_NAME="${STACK}-task-execution"
TRUST_FILE="$(mktemp)"
POLICY_FILE="$(mktemp)"
trap 'rm -f "$TRUST_FILE" "$POLICY_FILE"' EXIT

cat >"$TRUST_FILE" <<'JSON'
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ecs-tasks.amazonaws.com"},"Action":"sts:AssumeRole"}]}
JSON

if ! aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  aws iam create-role --role-name "$ROLE_NAME" \
    --assume-role-policy-document "file://${TRUST_FILE}" >/dev/null
fi
aws iam attach-role-policy --role-name "$ROLE_NAME" \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
jq -n --arg rds "$RDS_SECRET_ARN" --arg jwt "$JWT_SECRET_ARN" \
  '{Version:"2012-10-17",Statement:[{Effect:"Allow",Action:["secretsmanager:GetSecretValue"],Resource:[$rds,$jwt]}]}' \
  >"$POLICY_FILE"
aws iam put-role-policy --role-name "$ROLE_NAME" \
  --policy-name "${STACK}-read-secrets" --policy-document "file://${POLICY_FILE}" >/dev/null
sleep 10
EXECUTION_ROLE_ARN="$(aws iam get-role --role-name "$ROLE_NAME" \
  --query Role.Arn --output text)"

# Application Load Balancer and frontend target group.
ALB_ARN="$(aws elbv2 describe-load-balancers --region "$AWS_REGION" \
  --names "${STACK}-alb" --query 'LoadBalancers[0].LoadBalancerArn' \
  --output text 2>/dev/null || true)"
if [[ "$ALB_ARN" == "None" || -z "$ALB_ARN" ]]; then
  ALB_ARN="$(aws elbv2 create-load-balancer --region "$AWS_REGION" \
    --name "${STACK}-alb" --type application --scheme internet-facing \
    --ip-address-type ipv4 --subnets "${PUBLIC_SUBNETS[@]}" \
    --security-groups "$ALB_SG" --query 'LoadBalancers[0].LoadBalancerArn' \
    --output text)"
fi
ALB_DNS="$(aws elbv2 describe-load-balancers --region "$AWS_REGION" \
  --load-balancer-arns "$ALB_ARN" --query 'LoadBalancers[0].DNSName' --output text)"

TG_ARN="$(aws elbv2 describe-target-groups --region "$AWS_REGION" \
  --names "${STACK}-frontend" --query 'TargetGroups[0].TargetGroupArn' \
  --output text 2>/dev/null || true)"
if [[ "$TG_ARN" == "None" || -z "$TG_ARN" ]]; then
  TG_ARN="$(aws elbv2 create-target-group --region "$AWS_REGION" \
    --name "${STACK}-frontend" --protocol HTTP --port 80 \
    --target-type ip --vpc-id "$VPC_ID" --health-check-path / \
    --matcher HttpCode=200-399 --query 'TargetGroups[0].TargetGroupArn' \
    --output text)"
fi

LISTENER_ARN="$(aws elbv2 describe-listeners --region "$AWS_REGION" \
  --load-balancer-arn "$ALB_ARN" --query 'Listeners[?Port==`80`].ListenerArn | [0]' \
  --output text)"
if [[ "$LISTENER_ARN" == "None" || -z "$LISTENER_ARN" ]]; then
  aws elbv2 create-listener --region "$AWS_REGION" --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP --port 80 --default-actions "Type=forward,TargetGroupArn=${TG_ARN}" \
    >/dev/null
fi

# Logs.
for service in kafka auth app analytics execution-engine frontend price-history-import; do
  aws logs create-log-group --region "$AWS_REGION" \
    --log-group-name "${LOG_PREFIX}/${service}" >/dev/null 2>&1 || true
done

NETWORK_FILE="$(mktemp)"
jq -n --arg a "${PRIVATE_SUBNETS[0]}" --arg b "${PRIVATE_SUBNETS[1]}" --arg sg "$TASK_SG" \
  '{awsvpcConfiguration:{subnets:[$a,$b],securityGroups:[$sg],assignPublicIp:"DISABLED"}}' \
  >"$NETWORK_FILE"

# Helpers for task definitions and ECS services.
register_task() {
  local name="$1" cpu="$2" memory="$3" container_json="$4" file
  file="$(mktemp)"
  jq -n --arg family "${STACK}-${name}" --arg role "$EXECUTION_ROLE_ARN" \
    --argjson cpu "$cpu" --argjson memory "$memory" --argjson container "$container_json" \
    '{family:$family,networkMode:"awsvpc",requiresCompatibilities:["FARGATE"],
      cpu:($cpu|tostring),memory:($memory|tostring),executionRoleArn:$role,
      containerDefinitions:[$container]}' >"$file"
  aws ecs register-task-definition --region "$AWS_REGION" \
    --cli-input-json "file://${file}" --query 'taskDefinition.taskDefinitionArn' --output text
  rm -f "$file"
}

make_container() {
  local name="$1" image="$2" port="$3" env_json="$4" secrets_json="$5" health_json="$6"
  jq -n --arg name "$name" --arg image "$image" --arg log "${LOG_PREFIX}/${name}" \
    --arg region "$AWS_REGION" --argjson port "$port" \
    --argjson env "$env_json" --argjson secrets "$secrets_json" \
    --argjson health "$health_json" '
    {name:$name,image:$image,essential:true,environment:$env,secrets:$secrets,
     logConfiguration:{logDriver:"awslogs",options:{
       "awslogs-group":$log,"awslogs-region":$region,"awslogs-stream-prefix":"ecs"}}}
     + (if $port == 0 then {} else {portMappings:[{name:"service",containerPort:$port,protocol:"tcp"}]} end)
     + (if ($health|length)==0 then {} else {healthCheck:$health} end)'
}

deploy_service() {
  local name="$1" task_arn="$2" port="$3" discovery="$4" frontend="${5:-false}"
  local sc_file service_arn
  sc_file="$(mktemp)"
  if [[ "$port" -eq 0 ]]; then
    jq -n --arg ns "$NAMESPACE_ARN" '{enabled:true,namespace:$ns}' >"$sc_file"
  else
    jq -n --arg ns "$NAMESPACE_ARN" --arg name "$discovery" --argjson port "$port" \
      '{enabled:true,namespace:$ns,services:[{portName:"service",discoveryName:$name,
       clientAliases:[{port:$port,dnsName:$name}]}]}' >"$sc_file"
  fi

  service_arn="$(aws ecs describe-services --region "$AWS_REGION" --cluster "$CLUSTER" \
    --services "${STACK}-${name}" --query 'services[0].serviceArn' --output text)"
  if [[ "$service_arn" == "None" || -z "$service_arn" ]]; then
    if [[ "$frontend" == "true" ]]; then
      aws ecs create-service --region "$AWS_REGION" --cluster "$CLUSTER" \
        --service-name "${STACK}-${name}" --task-definition "$task_arn" \
        --desired-count "$SERVICE_COUNT" --launch-type FARGATE \
        --network-configuration "file://${NETWORK_FILE}" \
        --service-connect-configuration "file://${sc_file}" \
        --load-balancers "targetGroupArn=${TG_ARN},containerName=frontend,containerPort=80" \
        --health-check-grace-period-seconds 90 >/dev/null
    else
      aws ecs create-service --region "$AWS_REGION" --cluster "$CLUSTER" \
        --service-name "${STACK}-${name}" --task-definition "$task_arn" \
        --desired-count "$SERVICE_COUNT" --launch-type FARGATE \
        --network-configuration "file://${NETWORK_FILE}" \
        --service-connect-configuration "file://${sc_file}" >/dev/null
    fi
  else
    aws ecs update-service --region "$AWS_REGION" --cluster "$CLUSTER" \
      --service "${STACK}-${name}" --task-definition "$task_arn" \
      --desired-count "$SERVICE_COUNT" \
      --service-connect-configuration "file://${sc_file}" >/dev/null
  fi
  rm -f "$sc_file"
  aws ecs wait services-stable --region "$AWS_REGION" --cluster "$CLUSTER" \
    --services "${STACK}-${name}"
}

empty='[]'
no_health='{}'

# Kafka: one ephemeral broker. Its data is lost if its task is replaced.
kafka_env="$(jq -n '[
  {name:"KAFKA_NODE_ID",value:"1"},
  {name:"KAFKA_PROCESS_ROLES",value:"broker,controller"},
  {name:"KAFKA_LISTENERS",value:"INTERNAL://:9092,CONTROLLER://:9093"},
  {name:"KAFKA_ADVERTISED_LISTENERS",value:"INTERNAL://kafka:9092"},
  {name:"KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",value:"INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT"},
  {name:"KAFKA_INTER_BROKER_LISTENER_NAME",value:"INTERNAL"},
  {name:"KAFKA_CONTROLLER_LISTENER_NAMES",value:"CONTROLLER"},
  {name:"KAFKA_CONTROLLER_QUORUM_VOTERS",value:"1@localhost:9093"},
  {name:"KAFKA_AUTO_CREATE_TOPICS_ENABLE",value:"true"},
  {name:"KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR",value:"1"},
  {name:"KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR",value:"1"},
  {name:"KAFKA_TRANSACTION_STATE_LOG_MIN_ISR",value:"1"}
]')"
kafka_health='{"command":["CMD-SHELL","/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 >/dev/null 2>&1 || exit 1"],"interval":30,"timeout":10,"retries":5,"startPeriod":60}'
c="$(make_container kafka "$KAFKA_IMAGE" 9092 "$kafka_env" "$empty" "$kafka_health")"
t="$(register_task kafka 1024 2048 "$c")"
deploy_service kafka "$t" 9092 kafka

# Auth service.
auth_env="$(jq -n --arg host "$RDS_HOST" --arg db "$AUTH_DB_NAME" --arg trading "$TRADING_DB_NAME" \
  --arg url "http://${ALB_DNS}/login" --arg cors "http://${ALB_DNS}" '[
  {name:"NODE_ENV",value:"prod"},{name:"ENVIRONMENT",value:"prod"},
  {name:"POSTGRES_HOST",value:$host},{name:"POSTGRES_PORT",value:"5432"},
  {name:"AUTH_DB",value:$db},{name:"TRADING_DB_HOST",value:$host},
  {name:"TRADING_DB_PORT",value:"5432"},{name:"TRADING_DB_NAME",value:$trading},
  {name:"FRONTEND_URL",value:$url},{name:"CORS_ORIGINS",value:$cors}
]')"
auth_secrets="$(jq -n --arg user "$RDS_USER_REF" --arg pass "$RDS_PASSWORD_REF" --arg jwt "$JWT_REF" '[
  {name:"POSTGRES_USER",valueFrom:$user},{name:"POSTGRES_PASSWORD",valueFrom:$pass},
  {name:"TRADING_DB_USER",valueFrom:$user},{name:"TRADING_DB_PASSWORD",valueFrom:$pass},
  {name:"JWT_SECRET",valueFrom:$jwt}
]')"
auth_health='{"command":["CMD-SHELL","curl -fsS http://localhost:3000/api/auth/health >/dev/null || exit 1"],"interval":30,"timeout":5,"retries":5,"startPeriod":45}'
c="$(make_container auth "$AUTH_IMAGE" 3000 "$auth_env" "$auth_secrets" "$auth_health")"
t="$(register_task auth 256 512 "$c")"
deploy_service auth "$t" 3000 auth

# Java API.
app_env="$(jq -n --arg host "$RDS_HOST" --arg db "$TRADING_DB_NAME" '[
  {name:"SPRING_DATASOURCE_URL",value:("jdbc:postgresql://"+$host+":5432/"+$db+"?sslmode=require")},
  {name:"SPRING_DATASOURCE_USERNAME",value:"postgres"},
  {name:"SPRING_KAFKA_BOOTSTRAP_SERVERS",value:"kafka:9092"},
  {name:"AUTH_SERVICE_URL",value:"http://auth:3000"}
]')"
app_secrets="$(jq -n --arg pass "$RDS_PASSWORD_REF" --arg jwt "$JWT_REF" '[
  {name:"SPRING_DATASOURCE_PASSWORD",valueFrom:$pass},{name:"JWT_SECRET",valueFrom:$jwt}
]')"
c="$(make_container app "$APP_IMAGE" 8081 "$app_env" "$app_secrets" "$no_health")"
t="$(register_task app 512 1024 "$c")"
deploy_service app "$t" 8081 app

# Analytics API.
analytics_env="$(jq -n --arg cors "http://${ALB_DNS}" '[{name:"ANALYTICS_CORS_ORIGINS",value:$cors}]')"
c="$(make_container analytics "$ANALYTICS_IMAGE" 8090 "$analytics_env" "$empty" "$no_health")"
t="$(register_task analytics 512 1024 "$c")"
deploy_service analytics "$t" 8090 analytics

# Execution engine.
engine_env="$(jq -n '[
  {name:"SPRING_KAFKA_BOOTSTRAP_SERVERS",value:"kafka:9092"},
  {name:"ENGINE_MIN_DELAY",value:"500ms"},
  {name:"ENGINE_MAX_DELAY",value:"2000ms"},
  {name:"ENGINE_MAX_PRICE_IMPROVEMENT_BPS",value:"50"}
]')"
engine_health='{"command":["CMD-SHELL","wget -qO- http://localhost:8082/actuator/health | grep -q UP || exit 1"],"interval":30,"timeout":5,"retries":5,"startPeriod":60}'
c="$(make_container execution-engine "$ENGINE_IMAGE" 8082 "$engine_env" "$empty" "$engine_health")"
t="$(register_task execution-engine 512 1024 "$c")"
deploy_service execution-engine "$t" 8082 execution-engine

# Frontend is the only service exposed through the public ALB.
c="$(make_container frontend "$FRONTEND_IMAGE" 80 "$empty" "$empty" "$no_health")"
t="$(register_task frontend 256 512 "$c")"
deploy_service frontend "$t" 0 "" true

# Run the idempotent seed. Existing (symbol, price_date) rows are preserved;
# the seed's ON CONFLICT DO NOTHING only inserts missing rows.
seed_env="$(jq -n --arg host "$RDS_HOST" --arg db "$TRADING_DB_NAME" '[
  {name:"PGHOST",value:$host},{name:"PGPORT",value:"5432"},{name:"PGDATABASE",value:$db}
]')"
seed_secrets="$(jq -n --arg user "$RDS_USER_REF" --arg pass "$RDS_PASSWORD_REF" '[
  {name:"PGUSER",valueFrom:$user},{name:"PGPASSWORD",valueFrom:$pass}
]')"
seed_cmd='["sh","-c","exec psql -v ON_ERROR_STOP=1 -h \"$PGHOST\" -p \"$PGPORT\" -U \"$PGUSER\" -d \"$PGDATABASE\" -f /seed.sql"]'
seed_container="$(jq -n --arg image "$SEED_IMAGE" --arg log "${LOG_PREFIX}/price-history-import" \
  --arg region "$AWS_REGION" --argjson env "$seed_env" --argjson secrets "$seed_secrets" \
  --argjson cmd "$seed_cmd" '{
    name:"price-history-import",image:$image,essential:true,command:$cmd,
    environment:$env,secrets:$secrets,
    logConfiguration:{logDriver:"awslogs",options:{
      "awslogs-group":$log,"awslogs-region":$region,"awslogs-stream-prefix":"ecs"}}
  }')"
seed_task="$(register_task price-history-import 256 512 "$seed_container")"
run_json="$(aws ecs run-task --region "$AWS_REGION" --cluster "$CLUSTER" \
  --launch-type FARGATE --task-definition "$seed_task" \
  --network-configuration "file://${NETWORK_FILE}")"
task_arn="$(jq -er '.tasks[0].taskArn' <<<"$run_json")"
aws ecs wait tasks-stopped --region "$AWS_REGION" --cluster "$CLUSTER" --tasks "$task_arn"
exit_code="$(aws ecs describe-tasks --region "$AWS_REGION" --cluster "$CLUSTER" \
  --tasks "$task_arn" --query 'tasks[0].containers[0].exitCode' --output text)"
[[ "$exit_code" == "0" ]] || {
  echo "Price-history import failed. Check CloudWatch log group ${LOG_PREFIX}/price-history-import." >&2
  exit 1
}

rm -f "$NETWORK_FILE"
echo
echo "Deployment complete: http://${ALB_DNS}"
echo "Cluster: ${CLUSTER}"
echo "Price-history import completed without truncating existing rows."