#!/usr/bin/env bash
set -Eeuo pipefail

# The Commit Crew: AWS ECS/Fargate deployment
# Run from the repository root with an AWS CLI profile authorized to provision
# ECS, ECR, EC2 networking, ELB, IAM, Cloud Map, CloudWatch Logs, and Secrets Manager.

AWS_REGION="${AWS_REGION:-eu-west-1}"
CLUSTER="${CLUSTER:-thecommitcrew-cluster}"
STACK="${STACK:-thecommitcrew}"

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
SEC_ENABLED="${SEC_ENABLED:-true}"
SEC_DATA_URL="${SEC_DATA_URL:-https://data.sec.gov}"
SEC_USER_AGENT="${SEC_USER_AGENT:-The Commit Crew giuly.fatti@gmail.com}"
ALPACA_ENABLED="${ALPACA_ENABLED:-true}"
ALPACA_SECRET_ARN="${ALPACA_SECRET_ARN:?Set ALPACA_SECRET_ARN to a Secrets Manager secret containing apiKey and apiSecret}"
ALPACA_DATA_URL="${ALPACA_DATA_URL:-https://data.alpaca.markets}"
ALPACA_FEED="${ALPACA_FEED:-iex}"

SERVICE_COUNT="${SERVICE_COUNT:-1}"
NAMESPACE_NAME="${NAMESPACE_NAME:-thecommitcrew.internal}"
LOG_PREFIX="/ecs/${STACK}"
TASK_EXECUTION_ROLE_NAME="${TASK_EXECUTION_ROLE_NAME:-thecommitcrew-ecs-task-execution-role}"

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
echo "No NAT gateways or route-table associations will be created or changed."
echo "Fargate private subnets require existing ECR, S3, CloudWatch Logs, and Secrets Manager VPC endpoints."

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
ALPACA_KEY_REF="${ALPACA_SECRET_ARN}:apiKey::"
ALPACA_SECRET_REF="${ALPACA_SECRET_ARN}:apiSecret::"
if [[ "$ALPACA_ENABLED" != "true" && "$ALPACA_ENABLED" != "false" ]]; then
  echo "ALPACA_ENABLED must be true or false." >&2
  exit 1
fi
aws secretsmanager describe-secret --region "$AWS_REGION" \
  --secret-id "$ALPACA_SECRET_ARN" >/dev/null

require_interface_endpoint() {
  local service="$1" endpoint_json
  endpoint_json="$(aws ec2 describe-vpc-endpoints --region "$AWS_REGION" \
    --filters "Name=vpc-id,Values=${VPC_ID}" "Name=service-name,Values=com.amazonaws.${AWS_REGION}.${service}" \
    --output json)"
  if ! jq -e '.VpcEndpoints | any(.State == "available" and .VpcEndpointType == "Interface" and .PrivateDnsEnabled == true)' \
    >/dev/null <<<"$endpoint_json"; then
    echo "Missing available private-DNS interface endpoint: com.amazonaws.${AWS_REGION}.${service}" >&2
    return 1
  fi
}

for endpoint_service in ecr.api ecr.dkr logs secretsmanager; do
  require_interface_endpoint "$endpoint_service"
done

S3_ENDPOINT_JSON="$(aws ec2 describe-vpc-endpoints --region "$AWS_REGION" \
  --filters "Name=vpc-id,Values=${VPC_ID}" "Name=service-name,Values=com.amazonaws.${AWS_REGION}.s3" \
  --output json)"
if ! jq -e '.VpcEndpoints | any(.State == "available" and .VpcEndpointType == "Gateway")' \
  >/dev/null <<<"$S3_ENDPOINT_JSON"; then
  echo "Missing available S3 gateway VPC endpoint for ${VPC_ID}." >&2
  exit 1
fi

for subnet in "${PRIVATE_SUBNETS[@]}"; do
  route_table="$(aws ec2 describe-route-tables --region "$AWS_REGION" \
    --filters "Name=association.subnet-id,Values=${subnet}" \
    --query 'RouteTables[0].RouteTableId' --output text)"
  if [[ "$route_table" == "None" || -z "$route_table" ]]; then
    route_table="$(aws ec2 describe-route-tables --region "$AWS_REGION" \
      --filters "Name=vpc-id,Values=${VPC_ID}" "Name=association.main,Values=true" \
      --query 'RouteTables[0].RouteTableId' --output text)"
  fi
  if ! jq -e --arg route_table "$route_table" \
    '.VpcEndpoints | any(.State == "available" and .VpcEndpointType == "Gateway" and (.RouteTableIds | index($route_table) != null))' \
    >/dev/null <<<"$S3_ENDPOINT_JSON"; then
    echo "S3 gateway endpoint is not associated with route table ${route_table} for subnet ${subnet}." >&2
    exit 1
  fi
done

ensure_repo() {
  local repo="$1" error_file
  error_file="$(mktemp)"
  if aws ecr describe-repositories --region "$AWS_REGION" \
    --repository-names "$repo" >/dev/null 2>"$error_file"; then
    rm -f "$error_file"
    return
  fi
  if ! grep -q 'RepositoryNotFoundException' "$error_file"; then
    cat "$error_file" >&2
    rm -f "$error_file"
    return 1
  fi
  rm -f "$error_file"
  aws ecr create-repository --region "$AWS_REGION" \
    --repository-name "$repo" \
    --image-scanning-configuration scanOnPush=true >/dev/null
}

push_image() {
  local repo="$1" dockerfile="$2" context="$3" image="${ECR_REGISTRY}/${repo}:${IMAGE_TAG}"
  ensure_repo "$repo"
  docker build --platform linux/amd64 -f "$dockerfile" -t "$image" "$context" >&2
  docker push "$image" >&2
  printf '%s' "$image"
}

aws ecr get-login-password --region "$AWS_REGION" |
  docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "Building and pushing images..."
AUTH_IMAGE="$(push_image thecommitcrew-auth-service auth/Dockerfile .)"
APP_IMAGE="$(push_image thecommitcrew-app-service Dockerfile .)"
FRONTEND_IMAGE="$(push_image thecommitcrew-frontend-service frontend/Dockerfile .)"
ANALYTICS_IMAGE="$(push_image thecommitcrew-analytics-service analytics/Dockerfile .)"
ENGINE_IMAGE="$(push_image thecommitcrew-execution-engine-service execution-engine/Dockerfile .)"

# Mirror Kafka into this account's ECR as well.
KAFKA_REPO="thecommitcrew-kafka-service"
ensure_repo "$KAFKA_REPO"
KAFKA_IMAGE="${ECR_REGISTRY}/${KAFKA_REPO}:${IMAGE_TAG}"
docker pull --platform linux/amd64 apache/kafka:4.3.1
docker tag apache/kafka:4.3.1 "$KAFKA_IMAGE"
docker push "$KAFKA_IMAGE"

# Build a one-off image containing the existing idempotent seed SQL.
SEED_REPO="thecommitcrew-price-history-import"
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

ensure_sg_ingress() {
  local group="$1" port="$2" source_type="$3" source="$4" permissions
  permissions="$(aws ec2 describe-security-groups --region "$AWS_REGION" \
    --group-ids "$group" --output json)"
  if [[ "$source_type" == "cidr" ]] &&
    jq -e --arg cidr "$source" --argjson port "$port" '
      any(.SecurityGroups[].IpPermissions[]?;
        (.IpProtocol == "-1" or (.IpProtocol == "tcp" and .FromPort <= $port and .ToPort >= $port))
        and any(.IpRanges[]?; .CidrIp == $cidr))
    ' >/dev/null <<<"$permissions"; then
    return
  elif [[ "$source_type" == "group" ]] &&
    jq -e --arg source_group "$source" --argjson port "$port" '
      any(.SecurityGroups[].IpPermissions[]?;
        (.IpProtocol == "-1" or (.IpProtocol == "tcp" and .FromPort <= $port and .ToPort >= $port))
        and any(.UserIdGroupPairs[]?; .GroupId == $source_group))
    ' >/dev/null <<<"$permissions"; then
    return
  fi

  if [[ "$source_type" == "cidr" ]]; then
    aws ec2 authorize-security-group-ingress --region "$AWS_REGION" \
      --group-id "$group" --protocol tcp --port "$port" --cidr "$source" >/dev/null
  else
    aws ec2 authorize-security-group-ingress --region "$AWS_REGION" \
      --group-id "$group" --protocol tcp --port "$port" --source-group "$source" >/dev/null
  fi
}

ensure_sg_ingress "$ALB_SG" 80 cidr 0.0.0.0/0

# Service-to-service traffic only within the ECS task security group.
aws ec2 authorize-security-group-ingress --region "$AWS_REGION" \
  --group-id "$TASK_SG" --protocol -1 --source-group "$TASK_SG" >/dev/null 2>&1 || {
    permissions="$(aws ec2 describe-security-groups --region "$AWS_REGION" \
      --group-ids "$TASK_SG" --output json)"
    jq -e --arg source_group "$TASK_SG" \
      'any(.SecurityGroups[].IpPermissions[]?; .IpProtocol == "-1" and any(.UserIdGroupPairs[]?; .GroupId == $source_group))' \
      >/dev/null <<<"$permissions" || exit 1
  }

# Allow the ALB to reach the frontend tasks.
ensure_sg_ingress "$TASK_SG" 80 group "$ALB_SG"

task_sg_rules="$(aws ec2 describe-security-groups --region "$AWS_REGION" \
  --group-ids "$TASK_SG" --output json)"
if ! jq -e '
  any(.SecurityGroups[].IpPermissionsEgress[]?;
    (.IpProtocol == "-1" or (.IpProtocol == "tcp" and .FromPort <= 443 and .ToPort >= 443))
    and any(.IpRanges[]?; .CidrIp == "0.0.0.0/0")
  )
' >/dev/null <<<"$task_sg_rules"; then
  aws ec2 authorize-security-group-egress --region "$AWS_REGION" \
    --group-id "$TASK_SG" --protocol tcp --port 443 --cidr 0.0.0.0/0 >/dev/null
fi

# Confirm the ALB and Fargate subnets are valid without changing their routes.
for subnet in "${PUBLIC_SUBNETS[@]}" "${PRIVATE_SUBNETS[@]}"; do
  az="$(aws ec2 describe-subnets --region "$AWS_REGION" --subnet-ids "$subnet" \
    --query 'Subnets[0].AvailabilityZone' --output text)"
  [[ -n "$az" && "$az" != "None" ]] || { echo "Invalid subnet: $subnet" >&2; exit 1; }
done

for subnet in "${PUBLIC_SUBNETS[@]}"; do
  route_tables="$(aws ec2 describe-route-tables --region "$AWS_REGION" \
    --filters "Name=association.subnet-id,Values=${subnet}" --output json)"
  if [[ "$(jq '.RouteTables | length' <<<"$route_tables")" == "0" ]]; then
    route_tables="$(aws ec2 describe-route-tables --region "$AWS_REGION" \
      --filters "Name=vpc-id,Values=${VPC_ID}" "Name=association.main,Values=true" --output json)"
  fi
  if ! jq -e '.RouteTables | any(.Routes[]?; .DestinationCidrBlock == "0.0.0.0/0" and ((.GatewayId // "") | startswith("igw-")) and .State == "active")' \
    >/dev/null <<<"$route_tables"; then
    echo "Public subnet ${subnet} has no active Internet Gateway default route; no routes were changed." >&2
    exit 1
  fi
done

TASK_SUBNET_CIDRS="$(aws ec2 describe-subnets --region "$AWS_REGION" \
  --subnet-ids "${PRIVATE_SUBNETS[@]}" --query 'Subnets[].CidrBlock' --output json)"
VPC_CIDR="$(aws ec2 describe-vpcs --region "$AWS_REGION" --vpc-ids "$VPC_ID" \
  --query 'Vpcs[0].CidrBlock' --output text)"
INTERFACE_ENDPOINTS="$(aws ec2 describe-vpc-endpoints --region "$AWS_REGION" \
  --filters "Name=vpc-id,Values=${VPC_ID}" --output json)"
for endpoint_service in ecr.api ecr.dkr logs secretsmanager; do
  service_name="com.amazonaws.${AWS_REGION}.${endpoint_service}"
  endpoint_groups="$(jq -r --arg service "$service_name" \
    '.VpcEndpoints[] | select(.ServiceName == $service and .VpcEndpointType == "Interface" and .State == "available") | .Groups[].GroupId' \
    <<<"$INTERFACE_ENDPOINTS" | sort -u)"
  [[ -n "$endpoint_groups" ]] || {
    echo "Could not identify security groups for interface endpoint ${service_name}." >&2
    exit 1
  }
  endpoint_accessible=false
  while IFS= read -r endpoint_sg; do
    endpoint_sg_rules="$(aws ec2 describe-security-groups --region "$AWS_REGION" \
      --group-ids "$endpoint_sg" --output json)"
    if jq -e --arg task_sg "$TASK_SG" --arg vpc_cidr "$VPC_CIDR" --argjson subnets "$TASK_SUBNET_CIDRS" '
      any(.SecurityGroups[].IpPermissions[]?;
        (.IpProtocol == "-1" or (.IpProtocol == "tcp" and .FromPort <= 443 and .ToPort >= 443))
        and (
          any(.UserIdGroupPairs[]?; .GroupId == $task_sg)
          or any(.IpRanges[]?; .CidrIp == $vpc_cidr or (.CidrIp as $cidr | ($subnets | index($cidr)) != null))
        )
      )
    ' >/dev/null <<<"$endpoint_sg_rules"; then
      endpoint_accessible=true
      break
    fi
  done <<<"$endpoint_groups"
  if [[ "$endpoint_accessible" != true ]]; then
    echo "At least one security group on ${service_name} must allow TCP 443 from ${TASK_SG} or the configured private subnet CIDRs." >&2
    exit 1
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
ROLE_NAME="$TASK_EXECUTION_ROLE_NAME"
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
jq -n --arg rds "$RDS_SECRET_ARN" --arg jwt "$JWT_SECRET_ARN" --arg alpaca "$ALPACA_SECRET_ARN" \
  '{Version:"2012-10-17",Statement:[{Effect:"Allow",Action:["secretsmanager:GetSecretValue"],Resource:[$rds,$jwt,$alpaca]}]}' \
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
  --names "${STACK}-frontend-tg" --query 'TargetGroups[0].TargetGroupArn' \
  --output text 2>/dev/null || true)"
if [[ "$TG_ARN" == "None" || -z "$TG_ARN" ]]; then
  TG_ARN="$(aws elbv2 create-target-group --region "$AWS_REGION" \
    --name "${STACK}-frontend-tg" --protocol HTTP --port 80 \
    --target-type ip --vpc-id "$VPC_ID" --health-check-path / \
    --matcher HttpCode=200-399 --query 'TargetGroups[0].TargetGroupArn' \
    --output text)"
fi

LISTENER_ARN="$(aws elbv2 describe-listeners --region "$AWS_REGION" \
  --load-balancer-arn "$ALB_ARN" --query 'Listeners[?Port==`80`].ListenerArn | [0]' \
  --output text)"
if [[ "$LISTENER_ARN" == "None" || -z "$LISTENER_ARN" ]]; then
  LISTENER_ARN="$(aws elbv2 create-listener --region "$AWS_REGION" \
    --load-balancer-arn "$ALB_ARN" --protocol HTTP --port 80 \
    --default-actions "Type=forward,TargetGroupArn=${TG_ARN}" \
    --query 'Listeners[0].ListenerArn' --output text)"
else
  aws elbv2 modify-listener --region "$AWS_REGION" --listener-arn "$LISTENER_ARN" \
    --default-actions "Type=forward,TargetGroupArn=${TG_ARN}" >/dev/null
fi

# Logs.
for service in kafka auth app analytics execution-engine frontend price-history-import; do
  if ! aws logs describe-log-groups --region "$AWS_REGION" \
    --log-group-name-prefix "${LOG_PREFIX}/${service}" \
    --query "logGroups[?logGroupName=='${LOG_PREFIX}/${service}'].logGroupName | [0]" \
    --output text | grep -Fxq "${LOG_PREFIX}/${service}"; then
    aws logs create-log-group --region "$AWS_REGION" \
      --log-group-name "${LOG_PREFIX}/${service}" >/dev/null
  fi
done

NETWORK_FILE="$(mktemp)"
jq -n --arg a "${PRIVATE_SUBNETS[0]}" --arg b "${PRIVATE_SUBNETS[1]}" --arg sg "$TASK_SG" \
  '{awsvpcConfiguration:{subnets:[$a,$b],securityGroups:[$sg],assignPublicIp:"DISABLED"}}' \
  >"$NETWORK_FILE"

APP_NETWORK_FILE="$(mktemp)"
jq -n --arg a "${PUBLIC_SUBNETS[0]}" --arg b "${PUBLIC_SUBNETS[1]}" --arg sg "$TASK_SG" \
  '{awsvpcConfiguration:{subnets:[$a,$b],securityGroups:[$sg],assignPublicIp:"ENABLED"}}' \
  >"$APP_NETWORK_FILE"

# Helpers for task definitions and ECS services.
register_task() {
  local name="$1" cpu="$2" memory="$3" container_json="$4" file
  local family
  case "$name" in
    app) family="${STACK}-service" ;;
    *) family="${STACK}-${name}-service" ;;
  esac
  file="$(mktemp)"
  jq -n --arg family "$family" --arg role "$EXECUTION_ROLE_ARN" \
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
  local sc_file service_arn network_file="$NETWORK_FILE"
  if [[ "$name" == "app" ]]; then
    network_file="$APP_NETWORK_FILE"
  fi
  sc_file="$(mktemp)"
  if [[ "$port" -eq 0 ]]; then
    jq -n --arg ns "$NAMESPACE_ARN" '{enabled:true,namespace:$ns}' >"$sc_file"
  else
    jq -n --arg ns "$NAMESPACE_ARN" --arg name "$discovery" --argjson port "$port" \
      '{enabled:true,namespace:$ns,services:[{portName:"service",discoveryName:$name,
       clientAliases:[{port:$port,dnsName:$name}]}]}' >"$sc_file"
  fi

  service_arn="$(aws ecs describe-services --region "$AWS_REGION" --cluster "$CLUSTER" \
    --services "${STACK}-${name}-service" --query 'services[0].serviceArn' --output text)"
  if [[ "$service_arn" == "None" || -z "$service_arn" ]]; then
    if [[ "$frontend" == "true" ]]; then
      aws ecs create-service --region "$AWS_REGION" --cluster "$CLUSTER" \
        --service-name "${STACK}-${name}-service" --task-definition "$task_arn" \
        --desired-count "$SERVICE_COUNT" --launch-type FARGATE \
        --network-configuration "file://${network_file}" \
        --service-connect-configuration "file://${sc_file}" \
        --load-balancers "targetGroupArn=${TG_ARN},containerName=frontend,containerPort=80" \
        --health-check-grace-period-seconds 90 >/dev/null
    else
      aws ecs create-service --region "$AWS_REGION" --cluster "$CLUSTER" \
        --service-name "${STACK}-${name}-service" --task-definition "$task_arn" \
        --desired-count "$SERVICE_COUNT" --launch-type FARGATE \
        --network-configuration "file://${network_file}" \
        --service-connect-configuration "file://${sc_file}" >/dev/null
    fi
  else
    if [[ "$frontend" == "true" ]]; then
      aws ecs update-service --region "$AWS_REGION" --cluster "$CLUSTER" \
        --service "${STACK}-${name}-service" --task-definition "$task_arn" \
        --desired-count "$SERVICE_COUNT" \
        --network-configuration "file://${network_file}" \
        --service-connect-configuration "file://${sc_file}" \
        --load-balancers "targetGroupArn=${TG_ARN},containerName=frontend,containerPort=80" \
        --health-check-grace-period-seconds 90 >/dev/null
    else
      aws ecs update-service --region "$AWS_REGION" --cluster "$CLUSTER" \
        --service "${STACK}-${name}-service" --task-definition "$task_arn" \
        --desired-count "$SERVICE_COUNT" \
        --network-configuration "file://${network_file}" \
        --service-connect-configuration "file://${sc_file}" >/dev/null
    fi
  fi
  rm -f "$sc_file"
  aws ecs wait services-stable --region "$AWS_REGION" --cluster "$CLUSTER" \
    --services "${STACK}-${name}-service"
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
app_env="$(jq -n --arg host "$RDS_HOST" --arg db "$TRADING_DB_NAME" --arg auth_db "$AUTH_DB_NAME" \
  --arg sec_enabled "$SEC_ENABLED" --arg sec_data_url "$SEC_DATA_URL" \
  --arg sec_user_agent "$SEC_USER_AGENT" '[
  --arg alpaca_enabled "$ALPACA_ENABLED" --arg alpaca_url "$ALPACA_DATA_URL" --arg alpaca_feed "$ALPACA_FEED" '[
  {name:"SPRING_DATASOURCE_URL",value:("jdbc:postgresql://"+$host+":5432/"+$db+"?sslmode=require")},
  {name:"SPRING_AUTH_DATASOURCE_URL",value:("jdbc:postgresql://"+$host+":5432/"+$auth_db+"?sslmode=require")},
  {name:"SPRING_KAFKA_BOOTSTRAP_SERVERS",value:"kafka:9092"},
  {name:"AUTH_SERVICE_URL",value:"http://auth:3000"},
  {name:"SEC_ENABLED",value:$sec_enabled},
  {name:"SEC_DATA_URL",value:$sec_data_url},
  {name:"SEC_USER_AGENT",value:$sec_user_agent}
  {name:"ALPACA_ENABLED",value:$alpaca_enabled},
  {name:"ALPACA_DATA_URL",value:$alpaca_url},
  {name:"ALPACA_FEED",value:$alpaca_feed}
]')"
app_secrets="$(jq -n --arg user "$RDS_USER_REF" --arg pass "$RDS_PASSWORD_REF" --arg jwt "$JWT_REF" \
  --arg alpaca_key "$ALPACA_KEY_REF" --arg alpaca_secret "$ALPACA_SECRET_REF" '[
  {name:"SPRING_DATASOURCE_USERNAME",valueFrom:$user},
  {name:"SPRING_DATASOURCE_PASSWORD",valueFrom:$pass},
  {name:"SPRING_AUTH_DATASOURCE_USERNAME",valueFrom:$user},
  {name:"SPRING_AUTH_DATASOURCE_PASSWORD",valueFrom:$pass},
  {name:"ALPACA_API_KEY",valueFrom:$alpaca_key},
  {name:"ALPACA_API_SECRET",valueFrom:$alpaca_secret},
  {name:"JWT_SECRET",valueFrom:$jwt}
]')"
app_health='{"command":["CMD-SHELL","curl -fsS http://localhost:8081/actuator/health | grep -q UP || exit 1"],"interval":30,"timeout":5,"retries":5,"startPeriod":90}'
c="$(make_container app "$APP_IMAGE" 8081 "$app_env" "$app_secrets" "$app_health")"
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
frontend_health='{"command":["CMD-SHELL","wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1"],"interval":30,"timeout":5,"retries":3,"startPeriod":10}'
c="$(make_container frontend "$FRONTEND_IMAGE" 80 "$empty" "$empty" "$frontend_health")"
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

rm -f "$NETWORK_FILE" "$APP_NETWORK_FILE"
echo
echo "Deployment complete: http://${ALB_DNS}"
echo "Cluster: ${CLUSTER}"
echo "Price-history import completed without truncating existing rows."