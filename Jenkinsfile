pipeline {
    agent any

    environment {
        // AWS Configuration
        AWS_REGION = 'ap-northeast-2'
        ECR_REPOSITORY = 'catxi-backend'
        IMAGE_TAG = "${BUILD_NUMBER}"

        // Project Configuration
        PROJECT_NAME = 'catxi'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '============================================'
                echo 'Stage 1: Checkout Code from GitHub'
                echo '============================================'

                // Clean workspace
                deleteDir()

                // Clone repository
                checkout scm

                // Display commit information
                script {
                    def gitCommit = sh(returnStdout: true, script: 'git log -1 --pretty=format:"%h - %an, %ar : %s"').trim()
                    echo "Latest commit: ${gitCommit}"
                }
            }
        }

        stage('Build') {
            steps {
                echo '============================================'
                echo 'Stage 2: Build Application with Gradle'
                echo '============================================'

                script {
                    // Ensure gradlew has execute permissions
                    sh 'chmod +x gradlew'

                    // Clean and build (skip tests for faster builds)
                    sh './gradlew clean build -x test --no-daemon'

                    // Archive the JAR artifact
                    archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true

                    echo "Build completed successfully!"
                }
            }
        }

        stage('Terraform') {
            steps {
                echo '============================================'
                echo 'Stage 3: Provision Infrastructure with Terraform'
                echo '============================================'

                script {
                    dir('terraform') {
                        withCredentials([
                            [
                                $class: 'AmazonWebServicesCredentialsBinding',
                                credentialsId: 'aws-credentials',
                                accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                            ],
                            string(credentialsId: 'db-password', variable: 'DB_PASSWORD')
                        ]) {
                            // Initialize Terraform with S3 backend
                            sh 'terraform init -reconfigure'

                            // Validate configuration
                            sh 'terraform validate'

                            // Plan infrastructure changes
                            sh "terraform plan -var='rds_password=${DB_PASSWORD}' -out=tfplan"

                            // Apply only if plan succeeds
                            sh 'terraform apply -auto-approve tfplan'

                            // Extract outputs for deployment
                            sh 'terraform output -json > terraform-outputs.json'

                            echo "Terraform infrastructure provisioned successfully!"
                        }
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo '============================================'
                echo 'Stage 4: Build Docker Image'
                echo '============================================'

                script {
                    // Build multi-stage Docker image
                    sh """
                        docker build -t ${ECR_REPOSITORY}:${IMAGE_TAG} .
                        docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${ECR_REPOSITORY}:latest
                    """

                    echo "Docker image built: ${ECR_REPOSITORY}:${IMAGE_TAG}"
                }
            }
        }

        stage('Push to ECR') {
            steps {
                echo '============================================'
                echo 'Stage 5: Push Docker Image to AWS ECR'
                echo '============================================'

                script {
                    // Use AWS credentials stored in Jenkins
                    withCredentials([
                        [
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: 'aws-credentials',
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ],
                        string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID')
                    ]) {
                        def ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        // Login to ECR
                        sh """
                            aws ecr get-login-password --region ${AWS_REGION} | \
                            docker login --username AWS --password-stdin ${ECR_REGISTRY}
                        """

                        // Tag image for ECR
                        sh """
                            docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
                            docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest
                        """

                        // Push both tags
                        sh """
                            docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
                            docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest
                        """

                        echo "Docker image pushed to ECR: ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                echo '============================================'
                echo 'Stage 6: Deploy to EC2 Instance'
                echo '============================================'

                script {
                    // Extract EC2 public IP from Terraform outputs
                    def ec2Ip
                    withCredentials([
                        [
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: 'aws-credentials',
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ]
                    ]) {
                        ec2Ip = sh(
                            script: "cd terraform && terraform output -raw ec2_public_ip",
                            returnStdout: true
                        ).trim()
                    }

                    echo "Deploying to EC2: ${ec2Ip}"

                    // Use SSH credentials to connect to EC2
                    sshagent(['ec2-ssh-key']) {
                        // Create deployment directory if not exists
                        sh """
                            ssh -o StrictHostKeyChecking=no ubuntu@${ec2Ip} '
                                mkdir -p /home/ubuntu/catxi
                            '
                        """

                        // Wait for user-data script to complete (Docker installation)
                        echo "Waiting for Docker installation to complete..."
                        def dockerReady = false
                        for (int i = 1; i <= 30; i++) {
                            try {
                                sh """
                                    ssh -o StrictHostKeyChecking=no ubuntu@${ec2Ip} '
                                        which docker && which docker-compose && which aws
                                    '
                                """
                                dockerReady = true
                                echo "Docker is ready!"
                                break
                            } catch (Exception e) {
                                echo "Waiting for Docker... attempt ${i}/30"
                                sleep(time: 10, unit: 'SECONDS')
                            }
                        }
                        if (!dockerReady) {
                            error("Docker installation timed out after 5 minutes")
                        }

                        // Copy docker-compose file
                        sh """
                            scp -o StrictHostKeyChecking=no \
                            docker-compose.prod.yml \
                            ubuntu@${ec2Ip}:/home/ubuntu/catxi/docker-compose.prod.yml
                        """

                        // Create .env file with all secrets
                        withCredentials([
                            string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID'),
                            string(credentialsId: 'db-password', variable: 'DB_PASSWORD'),
                            string(credentialsId: 'redis-password', variable: 'REDIS_PASSWORD'),
                            string(credentialsId: 'jwt-secret-key', variable: 'JWT_SECRET_KEY'),
                            string(credentialsId: 'kakao-client-id', variable: 'KAKAO_CLIENT_ID'),
                            string(credentialsId: 'kakao-client-secret', variable: 'KAKAO_CLIENT_SECRET'),
                            string(credentialsId: 'discord-webhook-url', variable: 'DISCORD_WEBHOOK_URL'),
                            [
                                $class: 'AmazonWebServicesCredentialsBinding',
                                credentialsId: 'aws-credentials',
                                accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                            ]
                        ]) {
                            // Get RDS endpoint from Terraform
                            def rdsEndpoint = sh(
                                script: "cd terraform && terraform output -raw rds_endpoint",
                                returnStdout: true
                            ).trim()

                            // Remove port from endpoint if present
                            def rdsHost = rdsEndpoint.split(':')[0]

                            // Create .env file locally and copy to remote
                            writeFile file: '.env.prod', text: """SPRING_PROFILES_ACTIVE=prod
BUILD_NUMBER=${env.BUILD_NUMBER}
AWS_ACCOUNT_ID=${env.AWS_ACCOUNT_ID}
AWS_REGION=${env.AWS_REGION}
DB_HOST=${rdsHost}
DB_PORT=3306
DB_USER=catxi_admin
DB_PW=${env.DB_PASSWORD}
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=${env.REDIS_PASSWORD}
SECRET_KEY=${env.JWT_SECRET_KEY}
KAKAO_CLIENT_ID=${env.KAKAO_CLIENT_ID}
KAKAO_CLIENT_SECRET=${env.KAKAO_CLIENT_SECRET}
FCM_SERVICE_ACCOUNT_FILE=/app/config/firebase-service-account.json
DISCORD_WEBHOOK_URL=${env.DISCORD_WEBHOOK_URL}
TZ=Asia/Seoul
"""
                            sh "scp -o StrictHostKeyChecking=no .env.prod ubuntu@${ec2Ip}:/home/ubuntu/catxi/.env"

                            // Set secure permissions on .env
                            sh """
                                ssh -o StrictHostKeyChecking=no ubuntu@${ec2Ip} '
                                    chmod 600 /home/ubuntu/catxi/.env
                                '
                            """

                            def ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                            // Deploy on EC2
                            sh """
                                ssh -o StrictHostKeyChecking=no ubuntu@${ec2Ip} '
                                    cd /home/ubuntu/catxi

                                    # Set PATH for non-interactive shell
                                    export PATH=/usr/local/bin:/usr/bin:\$PATH

                                    # Configure AWS CLI
                                    export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
                                    export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
                                    export AWS_DEFAULT_REGION=${AWS_REGION}

                                    # Login to ECR
                                    /usr/local/bin/aws ecr get-login-password --region ${AWS_REGION} | \
                                    /usr/bin/docker login --username AWS --password-stdin ${ECR_REGISTRY}

                                    # Pull latest images
                                    /usr/local/bin/docker-compose -f docker-compose.prod.yml pull

                                    # Graceful shutdown and restart
                                    /usr/local/bin/docker-compose -f docker-compose.prod.yml down
                                    /usr/local/bin/docker-compose -f docker-compose.prod.yml up -d

                                    # Wait for containers to start
                                    sleep 10

                                    # Show container status
                                    /usr/local/bin/docker-compose -f docker-compose.prod.yml ps

                                    # Clean up old images to save disk space
                                    /usr/bin/docker image prune -af --filter "until=24h"
                                '
                            """
                        }

                        echo "Deployment to EC2 completed successfully!"
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                echo '============================================'
                echo 'Stage 7: Verify Application Health'
                echo '============================================'

                script {
                    def ec2Ip
                    withCredentials([
                        [
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: 'aws-credentials',
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ]
                    ]) {
                        ec2Ip = sh(
                            script: "cd terraform && terraform output -raw ec2_public_ip",
                            returnStdout: true
                        ).trim()
                    }

                    // Retry health check up to 10 times with 10 second intervals
                    def healthCheckPassed = false
                    def maxRetries = 10

                    for (int i = 1; i <= maxRetries; i++) {
                        try {
                            echo "Health check attempt ${i}/${maxRetries}..."
                            sleep(time: 10, unit: 'SECONDS')

                            sh """
                                curl -f http://${ec2Ip}:8080/actuator/health
                            """

                            healthCheckPassed = true
                            echo "Health check passed!"
                            break
                        } catch (Exception e) {
                            if (i == maxRetries) {
                                error("Health check failed after ${maxRetries} attempts")
                            }
                            echo "Health check failed, retrying..."
                        }
                    }

                    // Send success notification to Discord
                    withCredentials([string(credentialsId: 'discord-webhook-url', variable: 'DISCORD_WEBHOOK_URL')]) {
                        sh """
                            curl -X POST '${DISCORD_WEBHOOK_URL}' \
                            -H 'Content-Type: application/json' \
                            -d '{
                                "content": "Catxi Backend deployed successfully! Build: #${BUILD_NUMBER}, URL: http://${ec2Ip}:8080"
                            }'
                        """
                    }

                    echo "============================================"
                    echo "Deployment completed successfully!"
                    echo "Application URL: http://${ec2Ip}:8080"
                    echo "Health Check: http://${ec2Ip}:8080/actuator/health"
                    echo "============================================"
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline succeeded!'
            script {
                // Clean up Docker images on Jenkins server
                sh 'docker image prune -af --filter "until=24h"'
            }
        }

        failure {
            echo 'Pipeline failed!'
            script {
                // Send failure notification to Discord
                withCredentials([string(credentialsId: 'discord-webhook-url', variable: 'DISCORD_WEBHOOK_URL')]) {
                    sh """
                        curl -X POST '${DISCORD_WEBHOOK_URL}' \
                        -H 'Content-Type: application/json' \
                        -d '{
                            "content": "Catxi Backend deployment FAILED! Build: #${BUILD_NUMBER}, Jenkins: ${BUILD_URL}"
                        }'
                    """
                }
            }
        }

        always {
            echo 'Cleaning up workspace...'
            // Clean workspace to save disk space
            cleanWs()
        }
    }
}
