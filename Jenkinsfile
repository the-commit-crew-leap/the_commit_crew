pipeline {
    agent any
    tools {
        jdk "JDK21"
        maven "Maven3"
    }
    stages {  
        stage('Checkout') { 
            steps { 
                checkout scm 
            } 
        }
        stage('Secret Detection') {
            steps {
                script {
                    sh '''
                        docker run --rm \
                            -v "\$(pwd)":/repo \
                            zricethezav/gitleaks:latest \
                            detect \
                            --source /repo \
                            --report-path /repo/gitleaks-report.json \
                            --report-format json || EXIT_CODE=$?
                        
                        if [ "\${EXIT_CODE:-0}" -eq 1 ]; then
                            echo "Secrets detected!"
                            exit 1
                        fi
                        exit 0
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
                }
            }
        }
        stage('Build') {
            steps {
                sh 'mvn -B clean package'
            }
        }
        stage('Dependency Scanning') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    echo 'Dependency Scanning: entering catchError'

                    withCredentials([
                        string(
                            credentialsId: 'nvd-api-key',
                            variable: 'NVD_API_KEY'
                        )
                    ]) {
                        echo 'Dependency Scanning: credentials bound'

                        sh(
                            script: '''
                                set +e

                                echo "Dependency Scanning: shell started"

                                mvn -B -e dependency-check:check \
                                    -DnvdApiKey="$NVD_API_KEY" \
                                    -Ddependency-check.fail.build.on.cvss=5.0

                                EXIT_CODE=$?

                                echo "Dependency-Check exit code: $EXIT_CODE"
                                find . -type f -path '*target/dependency-check/*' -print

                                python3 - <<'PY'
                        import json
                        from pathlib import Path

                        reports = list(Path(".").glob("**/dependency-check-report.json"))

                        with Path("dependency-check-summary.txt").open("w", encoding="utf-8") as output:
                            total = 0

                            for report in reports:
                                data = json.loads(report.read_text(encoding="utf-8"))
                                output.write(f"Report: {report}\\n")

                                for dependency in data.get("dependencies", []):
                                    file_name = dependency.get("fileName", "unknown")

                                    for vulnerability in dependency.get("vulnerabilities", []):
                                        cvss3 = vulnerability.get("cvssv3") or {}
                                        cvss2 = vulnerability.get("cvssv2") or {}
                                        score = cvss3.get(
                                            "baseScore",
                                            cvss2.get("score", "N/A")
                                        )

                                        output.write(
                                            f"{score} | "
                                            f"{vulnerability.get('severity', 'unknown')} | "
                                            f"{vulnerability.get('name', 'unknown')} | "
                                            f"{file_name}\\n"
                                        )
                                        total += 1

                                output.write("\\n")

                        print(f"Dependency findings written to summary: {total}")
                        PY

                                echo "Summary file:"
                                ls -lh dependency-check-summary.txt
                                cat dependency-check-summary.txt

                                exit "$EXIT_CODE"
                            '''.stripIndent()
                        )
                    }
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts: '**/dependency-check-report.json,dependency-check-summary.txt',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }
        stage('Generate Analytics Seeds') {
            steps {
                sh '''
                    set -e
                    python3 -m venv .analytics-venv
                    .analytics-venv/bin/python -m pip install --quiet --upgrade pip
                    .analytics-venv/bin/python -m pip install --quiet -r analytics/requirements.txt
                    cd analytics
                    ../.analytics-venv/bin/python -m src.main run
                '''
            }
        }
        stage('Build Image') {
            steps { sh 'docker build -t the-commit-crew:${BUILD_NUMBER} .' }
        }
        stage('Start Database') {
            steps {
                script {
                    def environment = 'dev'
                    if (env.GIT_BRANCH == 'main' || env.GIT_BRANCH == 'origin/main') {
                        environment = 'prod'
                    }
                    echo "Starting ${environment} database environment..."

                    def credentialsId = (environment == 'prod') ? 'env-prod-file' : 'env-dev-file'
                    
                    withCredentials([file(credentialsId: credentialsId, variable: 'ENV_FILE_PATH')]) {
                        sh """
                            set -e
                            
                            echo "Cleaning up previous database state..."
                            docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" down -v || true

                            # Remove dangling images, containers, and volumes
                            docker system prune -f --volumes || true

                            # Kill any containers still using the network
                            docker ps -a --filter "network=the-commit-crew_default" --format "{{.ID}}" | xargs -r docker rm -f 2>/dev/null || true
                            sleep 1
                            docker network rm the-commit-crew_default 2>/dev/null || true

                            # Extra safety: manually remove any lingering db containers/volumes
                            docker ps -a --filter "name=the_commit_crew" --format "{{.ID}}" | xargs -r docker rm -f 2>/dev/null || true
                            docker volume ls --filter "name=the_commit_crew" --format "{{.Name}}" | xargs -r docker volume rm 2>/dev/null || true

                            sleep 2

                            # Check if DB container already exists and is running
                            RUNNING=\$(docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" ps db 2>/dev/null | grep -q "Up" && echo "true" || echo "false")
                            
                            POSTGRES_DB=\$(grep "^POSTGRES_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            POSTGRES_PASSWORD=\$(grep "^POSTGRES_PASSWORD=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            POSTGRES_PORT=\$(grep "^POSTGRES_PORT=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs || echo "5432")
                            
                            if [ "\$RUNNING" = "false" ]; then
                                echo "Database container not running, initializing from scratch..."
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" down db --remove-orphans || true
                                
                                # Remove all postgres/db containers across all projects
                                docker ps -a --filter "ancestor=postgres" --format "{{.ID}}" | xargs -r docker rm -f 2>/dev/null || true

                                # Remove containers with "db" in the name
                                docker ps -a --filter "name=db" --format "{{.ID}}" | xargs -r docker rm -f 2>/dev/null || true

                                # Wait for OS to release the port
                                sleep 3
                                
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" build --no-cache db
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" up -d db
                                
                                sleep 2
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" ps db
                                
                                echo "Waiting for PostgreSQL to be ready..."
                                for i in {1..30}; do
                                    if docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T db pg_isready -U postgres > /dev/null 2>&1; then
                                        echo "PostgreSQL is ready!"
                                        break
                                    fi
                                    echo "Attempt \$i/30: Waiting for database..."
                                    sleep 2
                                done
                                
                                echo "Waiting for database entrypoint initialization to fully complete..."
                                sleep 10

                                echo "Verifying database initialization..."
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f init-db.sql && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f update-data.sql"

                                AUTH_DB=\$(grep "^AUTH_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)

                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${AUTH_DB}\" -f init-auth-db.sql && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${AUTH_DB}\" -f update-auth-data.sql"

                                echo "Database initialization completed"
                            else
                                echo "Database already running, checking if data exists..."
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" ps db
                                
                                POSTGRES_DB=\$(grep "^POSTGRES_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                                AUTH_DB=\$(grep "^AUTH_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                                
                                # Check if trading database has data (instruments table)
                                DATA_COUNT=\$(docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db psql -U postgres -d "\${POSTGRES_DB}" -t -c "SELECT COUNT(*) FROM instruments;" 2>/dev/null || echo "0")
                                
                                if [ "\$DATA_COUNT" -eq 0 ]; then
                                    echo "Trading database exists but is unpopulated, loading seed data..."
                                    docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                        -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                        db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f update-data.sql"
                                fi
                                
                                # Check if auth database has data (users table)
                                AUTH_DATA_COUNT=\$(docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db psql -U postgres -d "\${AUTH_DB}" -t -c "SELECT COUNT(*) FROM users;" 2>/dev/null || echo "0")
                                
                                if [ "\$AUTH_DATA_COUNT" -eq 0 ]; then
                                    echo "Auth database exists but is unpopulated, loading seed data..."
                                    docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                        -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                        db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${AUTH_DB}\" -f update-auth-data.sql"
                                fi
                                
                                echo "Database check completed"
                            fi

                                                        echo "Starting Kafka..."
                            docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" up -d kafka

                            echo "Waiting for Kafka to become ready..."
                            KAFKA_READY=false

                            for i in {1..30}; do
                                if docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T kafka \
                                    /opt/kafka/bin/kafka-broker-api-versions.sh \
                                    --bootstrap-server localhost:9092 \
                                    >/dev/null 2>&1; then
                                    echo "Kafka is ready!"
                                    KAFKA_READY=true
                                    break
                                fi

                                echo "Attempt \$i/30: Waiting for Kafka..."
                                sleep 2
                            done

                            if [ "\$KAFKA_READY" != "true" ]; then
                                echo "Kafka failed to become ready"
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" logs kafka
                                exit 1
                            fi
                        """
                    }
                }
            }
            post {
                failure {
                    sh 'docker-compose -p the_commit_crew logs db 2>/dev/null || true'
                }
            }
        }
        stage('Update Database') {
            when {
                changeset pattern: "db/**"
            }
            steps {
                script {
                    def environment = 'dev'
                    if (env.GIT_BRANCH == 'main' || env.GIT_BRANCH == 'origin/main') {
                        environment = 'prod'
                    }
                    echo "Updating database data for ${environment} environment..."
                    
                    def credentialsId = (environment == 'prod') ? 'env-prod-file' : 'env-dev-file'
                    
                    withCredentials([file(credentialsId: credentialsId, variable: 'ENV_FILE_PATH')]) {
                        sh """
                            set -e
                            
                            if [ ! -f "db/update-data.sql" ]; then
                                echo "WARN: db/update-data.sql not found, skipping data update"
                                exit 0
                            fi
                            
                            echo "Running database update scripts..."

                            POSTGRES_DB=\$(grep "^POSTGRES_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            AUTH_DB=\$(grep "^AUTH_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            POSTGRES_PASSWORD=\$(grep "^POSTGRES_PASSWORD=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)

                            # Update trading database
                            if [ -f "db/update-data.sql" ]; then
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f update-data.sql"
                            fi

                            # Update auth database
                            if [ -f "db/update-auth-data.sql" ]; then
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${AUTH_DB}\" -f update-auth-data.sql"
                            fi

                            echo "Database update completed successfully"
                        """
                    }
                }
            }
            post {
                failure {
                    sh 'docker-compose -p the_commit_crew logs db 2>/dev/null || true'
                }
            }
        }
        stage('Smoke Test') {
            steps {
                script {
                    def environment = 'dev'
                    if (env.GIT_BRANCH == 'main' || env.GIT_BRANCH == 'origin/main') {
                        environment = 'prod'
                    }
                    def credentialsId = (environment == 'prod') ? 'env-prod-file' : 'env-dev-file'
                    
                    withCredentials([file(credentialsId: credentialsId, variable: 'ENV_FILE_PATH')]) {
                        sh '''
                            set e

                            # Extract credentials from env file
                            POSTGRES_DB=$(grep "^POSTGRES_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            POSTGRES_PASSWORD=$(grep "^POSTGRES_PASSWORD=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            AUTH_DB=$(grep "^AUTH_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            AUTH_PASSWORD=$(grep "^AUTH_DB_PASSWORD=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs || echo "$POSTGRES_PASSWORD")
                            
                            if [ -z "$AUTH_PASSWORD" ]; then
                                AUTH_PASSWORD="$POSTGRES_PASSWORD"
                            fi

                            echo "Verifying Kafka is available..."

                            for i in $(seq 1 30); do
                                if docker-compose -p the_commit_crew --env-file "$ENV_FILE_PATH" exec -T kafka \
                                    /opt/kafka/bin/kafka-broker-api-versions.sh \
                                    --bootstrap-server localhost:9092 \
                                    >/dev/null 2>&1; then
                                    echo "Kafka is ready"
                                    break
                                fi

                                if [ "$i" -eq 30 ]; then
                                    echo "Kafka is not available"
                                    docker-compose -p the_commit_crew --env-file "$ENV_FILE_PATH" logs kafka
                                    exit 1
                                fi

                                echo "Waiting for Kafka... attempt $i/30"
                                sleep 2
                            done

                            echo "Starting application smoke-test container..."

                            CONTAINER_ID=$(docker run -d \
                                --name "the-commit-crew-smoke-${BUILD_NUMBER}" \
                                -p 8081:8081 \
                                --network=the-commit-crew_default \
                                -e "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}" \
                                -e "SPRING_DATASOURCE_USERNAME=postgres" \
                                -e "SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}" \
                                -e "SPRING_AUTH_DATASOURCE_URL=jdbc:postgresql://db:5432/${AUTH_DB}" \
                                -e "SPRING_AUTH_DATASOURCE_USERNAME=postgres" \
                                -e "SPRING_AUTH_DATASOURCE_PASSWORD=${AUTH_PASSWORD}" \
                                -e "SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092" \
                                -e "SPRING_KAFKA_ADMIN_FAIL_FAST=true" \
                                -e "SPRING_KAFKA_PROPERTIES_REQUEST_TIMEOUT_MS=5000" \
                                -e "SERVER_SHUTDOWN=graceful" \
                                "the-commit-crew:${BUILD_NUMBER}")

                            cleanup() {
                                echo "Cleaning up smoke-test container..."
                                docker logs "$CONTAINER_ID" 2>/dev/null || true
                                docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
                            }

                            trap cleanup EXIT

                            echo "Waiting for Spring Boot health endpoint..."

                            for i in $(seq 1 90); do
                                HEALTH_RESPONSE=$(curl -sS \
                                    --connect-timeout 2 \
                                    --max-time 5 \
                                    http://localhost:8081/actuator/health \
                                    2>/dev/null || true)

                                if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"'; then
                                    echo "Smoke test passed"
                                    exit 0
                                fi

                                CONTAINER_RUNNING=$(
                                    docker inspect \
                                        --format '{{.State.Running}}' \
                                        "$CONTAINER_ID" \
                                        2>/dev/null || echo "false"
                                )

                                if [ "$CONTAINER_RUNNING" != "true" ]; then
                                    echo "Application container stopped unexpectedly"
                                    echo "Container state:"
                                    docker inspect \
                                        --format '{{json .State}}' \
                                        "$CONTAINER_ID" \
                                        2>/dev/null || true
                                    echo "Application logs:"
                                    docker logs "$CONTAINER_ID" 2>&1 || true
                                    exit 1
                                fi

                                echo "Attempt $i/90: application is not ready"
                                sleep 2
                            done

                            echo "Smoke test failed: application did not become healthy"
                            echo "Last health response:"
                            echo "$HEALTH_RESPONSE"
                            echo "Application logs:"
                            docker logs "$CONTAINER_ID" 2>&1 || true
                            exit 1
                        '''
                    }
                }
            }
        }
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'app/target/*.jar', fingerprint: true
            }
        }
        stage('Test') {
            steps { sh 'mvn -B test' }
                post { always { junit 'app/target/surefire-reports/*.xml' } }
        }
        stage('Quality Gate') {
            steps {
                withSonarQubeEnv('sonarserver') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        sh 'mvn -B sonar:sonar -Dsonar.token=$SONAR_TOKEN -Dsonar.qualitygate.wait=true'
                    }
                }
            }
        }
        stage('Integration Tests') {
            when {
                not {
                    anyOf {
                        branch 'main'
                        branch 'origin/main'
                    }
                }
            }
            steps {
                withCredentials([file(credentialsId: 'env-dev-file', variable: 'ENV_FILE_PATH')]) {
                    sh '''
                        chmod +x integration-test.sh
                        ./integration-test.sh the-commit-crew:${BUILD_NUMBER} dev "\${ENV_FILE_PATH}" the_commit_crew-db-1
                    '''
                }
            }
        }
    }
}