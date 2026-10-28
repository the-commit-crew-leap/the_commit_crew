#!/usr/bin/env bash
# Runs the Module 11 pipeline stages by hand, stopping at the first failure.
# Usage: ./deploy.sh [path-to-mission-service-source]
set -euo pipefail

PREFIX="${PREFIX:-team7-pat}"
export AWS_REGION="${AWS_REGION:-eu-west-1}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_DIR="${1:-$SCRIPT_DIR/../../../leap-sprint6/solutions/13-mission-build-containerise-integration-test-wrap-up}"

ECR_REPOSITORY="${PREFIX}-mission-service"
ECS_CLUSTER="${PREFIX}-mission-cluster"
ECS_SERVICE="${PREFIX}-mission-service"
TASK_FAMILY="${PREFIX}-mission-service"
ALB_NAME="${PREFIX}-mission-alb"
PLATFORM="${PLATFORM:-linux/arm64}"

stage() { echo; echo "== Stage: $1 =="; }
trap 'echo; echo "!! PIPELINE FAILED in stage: ${CURRENT_STAGE:-setup}"' ERR

CURRENT_STAGE="Setup"
stage "$CURRENT_STAGE"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
if git -C "$SOURCE_DIR" rev-parse --short HEAD >/dev/null 2>&1; then
  IMAGE_TAG=$(git -C "$SOURCE_DIR" rev-parse --short HEAD)
else
  IMAGE_TAG="build-$(date +%Y%m%d-%H%M%S)"
fi
IMAGE_URI="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
echo "Source:   $SOURCE_DIR"
echo "Image:    ${ECR_REPOSITORY}:${IMAGE_TAG} (${PLATFORM})"
echo "Service:  ${ECS_CLUSTER}/${ECS_SERVICE}"

CURRENT_STAGE="Build and Push"
stage "$CURRENT_STAGE"
aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker build --platform "$PLATFORM" -t "$IMAGE_URI" "$SOURCE_DIR"
docker push "$IMAGE_URI"

CURRENT_STAGE="Render Task Definition"
stage "$CURRENT_STAGE"
WORK_DIR=$(mktemp -d)
aws ecs describe-task-definition --task-definition "$TASK_FAMILY" \
  --query taskDefinition > "$WORK_DIR/current-taskdef.json"
python3 "$SCRIPT_DIR/render_taskdef.py.example" "$WORK_DIR/current-taskdef.json" "$IMAGE_URI" \
  > "$WORK_DIR/new-taskdef.json"
NEW_TASKDEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json "file://$WORK_DIR/new-taskdef.json" \
  --query taskDefinition.taskDefinitionArn --output text)
echo "Registered: ${NEW_TASKDEF_ARN##*/}"

CURRENT_STAGE="Deploy"
stage "$CURRENT_STAGE"
aws ecs update-service --cluster "$ECS_CLUSTER" --service "$ECS_SERVICE" \
  --task-definition "$NEW_TASKDEF_ARN" --force-new-deployment \
  --health-check-grace-period-seconds 90 \
  --query 'service.serviceName' --output text
echo "Waiting for the service to stabilise (usually 5-10 minutes, up to 30)..."
for attempt in 1 2 3; do
  if aws ecs wait services-stable --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" 2>/dev/null; then
    break
  fi
  ROLLOUT=$(aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
    --query "services[0].deployments[?taskDefinition=='$NEW_TASKDEF_ARN'].rolloutState | [0]" --output text)
  echo "  still waiting (rollout: $ROLLOUT, attempt $attempt of 3)"
  [ "$ROLLOUT" = "FAILED" ] && { echo "Deployment rolled back - check: aws ecs describe-services ... events"; false; }
  [ "$attempt" = "3" ] && { echo "Gave up after 30 minutes"; false; }
done

CURRENT_STAGE="Verify"
stage "$CURRENT_STAGE"
RUNNING_TD=$(aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
  --query 'services[0].taskDefinition' --output text)
[ "$RUNNING_TD" = "$NEW_TASKDEF_ARN" ] || { echo "Service is running ${RUNNING_TD##*/}, expected ${NEW_TASKDEF_ARN##*/}"; false; }
ALB_DNS=$(aws elbv2 describe-load-balancers --names "$ALB_NAME" \
  --query 'LoadBalancers[0].DNSName' --output text)
CODE=$(curl -s -o /dev/null -w '%{http_code}' -m 10 "http://${ALB_DNS}/actuator/health")
echo "ALB health check: HTTP $CODE"
[ "$CODE" = "401" ] || [ "$CODE" = "200" ] || { echo "Expected 401 or 200 from the ALB"; false; }

rm -rf "$WORK_DIR"
echo
echo "== PIPELINE PASSED: ${NEW_TASKDEF_ARN##*/} is live behind http://${ALB_DNS} =="
