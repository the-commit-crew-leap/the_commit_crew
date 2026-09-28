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
        stage('Build') {
            steps {
                sh 'mvn -B clean package'
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
                                
                                echo "Initializing database schema and data..."
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f init-db.sql && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f update-data.sql"
                                
                                echo "Database initialization completed"
                            else
                                echo "Database already running, checking if data exists..."
                                docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" ps db
                                
                                # Check if instruments table has data (as a proxy for overall population)
                                DATA_COUNT=\$(docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                    -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                    db psql -U postgres -d "\${POSTGRES_DB}" -t -c "SELECT COUNT(*) FROM instruments;" 2>/dev/null || echo "0")
                                
                                if [ "\$DATA_COUNT" -eq 0 ]; then
                                    echo "Database exists but is unpopulated, loading seed data..."
                                    docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                        -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                        db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f update-data.sql"
                                    echo "Seed data loaded successfully"
                                else
                                    echo "Database already populated with data, skipping initialization"
                                fi
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
                            
                            echo "Running database update script..."
                            
                            POSTGRES_DB=\$(grep "^POSTGRES_DB=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)
                            POSTGRES_PASSWORD=\$(grep "^POSTGRES_PASSWORD=" "\${ENV_FILE_PATH}" | cut -d'=' -f2 | tr -d '\r' | xargs)

                            # Set ON_ERROR_STOP to exit on first error
                            docker-compose -p the_commit_crew --env-file "\${ENV_FILE_PATH}" exec -T \
                                -e PGPASSWORD="\${POSTGRES_PASSWORD}" \
                                db sh -c "cd /docker-entrypoint-initdb.d && psql -v ON_ERROR_STOP=1 -U postgres -d \"\${POSTGRES_DB}\" -f update-data.sql"
                            
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
                sh '''
                    CONTAINER_ID=$(docker run -d \
                        -p 8081:8081 \
                        --network=the-commit-crew_default \
                        the-commit-crew:${BUILD_NUMBER})
                    
                    echo "Waiting for Spring Boot to start..."
                    for i in {1..30}; do
                        if curl -f http://localhost:8081/actuator/health > /dev/null 2>&1; then
                            echo "Health check passed"
                            docker rm -f $CONTAINER_ID
                            exit 0
                        fi
                        echo "Attempt $i/30: Waiting for application to be ready..."
                        sleep 1
                    done
                    
                    echo "Health check failed"
                    docker logs $CONTAINER_ID
                    docker rm -f $CONTAINER_ID
                    exit 1
                '''
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
                        ./integration-test.sh the-commit-crew:${BUILD_NUMBER} dev "${ENV_FILE_PATH}" the_commit_crew-db-1
                    '''
                }
            }
        }
    }
}