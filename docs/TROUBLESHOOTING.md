# Catxi Backend - 트러블슈팅 가이드

## 📋 목차

1. [Terraform 문제](#terraform-문제)
2. [Jenkins 파이프라인 문제](#jenkins-파이프라인-문제)
3. [Docker 문제](#docker-문제)
4. [애플리케이션 문제](#애플리케이션-문제)
5. [네트워크 문제](#네트워크-문제)
6. [데이터베이스 문제](#데이터베이스-문제)

---

## Terraform 문제

### ❌ Error: State lock acquisition failed

**증상:**
```
Error: Error acquiring the state lock
Lock Info:
  ID: xxxxxxxxxx
  Path: catxi-terraform-state/prod/terraform.tfstate
  Operation: OperationTypeApply
  Who: user@hostname
```

**원인:** 이전 Terraform 실행이 비정상 종료되어 DynamoDB lock이 남아있음

**해결:**
```bash
# 1. Lock 확인
aws dynamodb scan --table-name catxi-terraform-locks --region ap-northeast-2

# 2. Lock 삭제
aws dynamodb delete-item \
  --table-name catxi-terraform-locks \
  --key '{"LockID": {"S": "catxi-terraform-state/prod/terraform.tfstate"}}' \
  --region ap-northeast-2

# 3. 재시도
terraform apply
```

---

### ❌ Error: No valid credential sources found

**증상:**
```
Error: No valid credential sources found for AWS Provider
```

**원인:** AWS credentials가 설정되지 않음

**해결:**
```bash
# AWS CLI 설정
aws configure

# 또는 환경변수 설정
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_DEFAULT_REGION="ap-northeast-2"

# 확인
aws sts get-caller-identity
```

---

### ❌ Error: Backend initialization required

**증상:**
```
Error: Backend initialization required, please run "terraform init"
```

**원인:** S3 bucket 또는 DynamoDB table이 존재하지 않음

**해결:**
```bash
# Backend 초기화 스크립트 실행
./scripts/init-terraform-backend.sh

# Terraform 초기화
cd terraform
terraform init
```

---

### ❌ Error: Resource already exists

**증상:**
```
Error: Error creating ECR repository: RepositoryAlreadyExistsException
```

**원인:** 이미 생성된 리소스를 다시 생성하려고 시도

**해결:**
```bash
# 1. 기존 리소스 import
terraform import module.ecr.aws_ecr_repository.main catxi-backend

# 2. 또는 기존 리소스 삭제 후 재생성
aws ecr delete-repository --repository-name catxi-backend --region ap-northeast-2 --force
terraform apply
```

---

### ❌ Error: InvalidParameterValue (RDS password)

**증상:**
```
Error: Error creating DB Instance: InvalidParameterValue:
The parameter MasterUserPassword is not a valid password
```

**원인:** RDS 비밀번호가 요구사항을 만족하지 않음

**해결:**
```bash
# terraform.tfvars 수정
rds_password = "Strong-Password-Min-8-Chars-1234"

# 요구사항:
# - 최소 8자
# - 영문 대소문자, 숫자 포함
# - 특수문자 포함 가능: ! # $ % & * + , - . / : ; = ? @ [ ] ^ _ ` { | } ~
# - "/" 는 사용 불가
```

---

## Jenkins 파이프라인 문제

### ❌ Stage 2: Build Failed - Gradle Permission Denied

**증상:**
```
./gradlew: Permission denied
```

**원인:** gradlew 실행 권한 없음

**해결:**
```bash
# 로컬에서
chmod +x gradlew
git add gradlew
git commit -m "fix: Add execute permission to gradlew"
git push
```

---

### ❌ Stage 4: ECR Login Failed

**증상:**
```
Error: Cannot perform an interactive login from a non TTY device
```

**원인:** AWS credentials가 Jenkins에 설정되지 않음

**해결:**
1. Jenkins → Manage Jenkins → Credentials
2. `aws-credentials` 확인
3. Access Key ID와 Secret Access Key 재입력
4. `aws-account-id` credential도 확인

---

### ❌ Stage 5: Terraform Failed - Invalid Syntax

**증상:**
```
Error: Invalid expression
```

**원인:** Jenkinsfile에서 변수 참조 오류

**해결:**
```groovy
# Jenkinsfile에서 변수 확인
withCredentials([
    string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID')
]) {
    sh "echo ${AWS_ACCOUNT_ID}"  # 올바른 문법
}
```

---

### ❌ Stage 6: SSH Connection Failed

**증상:**
```
Host key verification failed
```

**원인:** SSH known_hosts에 EC2 호스트 키가 없음

**해결:**
```groovy
# Jenkinsfile에 이미 설정됨
ssh -o StrictHostKeyChecking=no ubuntu@${ec2Ip}

# 또는 Jenkins 서버에서 수동 추가
ssh-keyscan -H <ec2-ip> >> ~/.ssh/known_hosts
```

---

### ❌ Stage 7: Health Check Timeout

**증상:**
```
curl: (7) Failed to connect to <ec2-ip> port 8080: Connection refused
```

**원인:**
1. 애플리케이션이 시작되지 않음
2. Security Group이 8080 포트를 차단

**해결:**
```bash
# EC2 SSH 접속
ssh -i popol-key.pem ubuntu@<ec2-ip>

# 1. 컨테이너 상태 확인
docker-compose -f /home/ubuntu/catxi/docker-compose.prod.yml ps

# 2. 로그 확인
docker-compose -f /home/ubuntu/catxi/docker-compose.prod.yml logs app

# 3. Security Group 확인
aws ec2 describe-security-groups \
  --group-ids <sg-id> \
  --region ap-northeast-2
```

---

## Docker 문제

### ❌ Docker: Cannot connect to the Docker daemon

**증상:**
```
Cannot connect to the Docker daemon at unix:///var/run/docker.sock
```

**원인:** Docker가 실행되지 않거나 권한 문제

**해결:**
```bash
# Docker 상태 확인
sudo systemctl status docker

# Docker 시작
sudo systemctl start docker

# 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER

# 로그아웃 후 재로그인
exit
ssh -i popol-key.pem ubuntu@<ec2-ip>
```

---

### ❌ Docker: No space left on device

**증상:**
```
Error: No space left on device
```

**원인:** EC2 디스크 공간 부족

**해결:**
```bash
# 디스크 사용량 확인
df -h

# Docker 이미지/컨테이너 정리
docker system prune -af --volumes

# 오래된 이미지만 삭제
docker image prune -af --filter "until=24h"

# 로그 파일 정리
sudo find /var/log -type f -name "*.log" -mtime +7 -delete
```

---

### ❌ Docker Compose: service 'app' depends on service 'redis' which is undefined

**증상:**
```
ERROR: Service 'app' depends on service 'redis' which is undefined
```

**원인:** docker-compose.prod.yml 파일이 손상되었거나 잘못된 파일 사용

**해결:**
```bash
# EC2에서 docker-compose.prod.yml 확인
cat /home/ubuntu/catxi/docker-compose.prod.yml

# 파일이 없거나 손상된 경우, 로컬에서 재전송
scp -i popol-key.pem docker-compose.prod.yml ubuntu@<ec2-ip>:/home/ubuntu/catxi/

# 또는 Jenkins 재배포
```

---

## 애플리케이션 문제

### ❌ Application: Failed to connect to database

**증상:**
```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago
```

**원인:**
1. RDS endpoint가 잘못됨
2. Security Group이 3306 포트를 차단
3. RDS가 시작되지 않음

**해결:**
```bash
# 1. EC2에서 .env 파일 확인
ssh -i popol-key.pem ubuntu@<ec2-ip>
cat /home/ubuntu/catxi/.env | grep DB_HOST

# 2. RDS 연결 테스트
nc -zv $DB_HOST 3306

# 3. Security Group 확인
aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=catxi-rds-sg" \
  --region ap-northeast-2

# 4. RDS 상태 확인
aws rds describe-db-instances \
  --db-instance-identifier catxi-db \
  --region ap-northeast-2 | jq '.DBInstances[0].DBInstanceStatus'
```

---

### ❌ Application: Redis connection refused

**증상:**
```
Unable to connect to Redis; nested exception is
io.lettuce.core.RedisConnectionException: Unable to connect to localhost:6379
```

**원인:** Redis 컨테이너가 실행되지 않거나 비밀번호 불일치

**해결:**
```bash
# EC2 SSH 접속
ssh -i popol-key.pem ubuntu@<ec2-ip>
cd /home/ubuntu/catxi

# 1. Redis 컨테이너 상태 확인
docker-compose -f docker-compose.prod.yml ps redis

# 2. Redis 로그 확인
docker-compose -f docker-compose.prod.yml logs redis

# 3. Redis 재시작
docker-compose -f docker-compose.prod.yml restart redis

# 4. Redis 연결 테스트
docker exec -it catxi-redis redis-cli -a ${REDIS_PASSWORD} ping
```

---

### ❌ Application: Liquibase migration failed

**증상:**
```
Liquibase: liquibase.exception.LockException:
Could not acquire change log lock
```

**원인:** 이전 migration이 실패하여 lock이 남아있음

**해결:**
```bash
# RDS 접속
mysql -h <rds-endpoint> -u catxi_admin -p

# Lock 해제
USE catxi;
UPDATE DATABASECHANGELOGLOCK SET LOCKED=0, LOCKGRANTED=NULL, LOCKEDBY=NULL WHERE ID=1;

# 확인
SELECT * FROM DATABASECHANGELOGLOCK;

# 애플리케이션 재시작
docker-compose -f /home/ubuntu/catxi/docker-compose.prod.yml restart app
```

---

### ❌ Application: OutOfMemoryError

**증상:**
```
java.lang.OutOfMemoryError: Java heap space
```

**원인:** EC2 t2.micro 메모리 부족 (1GB)

**해결:**

**임시 방법:**
```bash
# docker-compose.prod.yml 수정
services:
  app:
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m  # 힙 메모리 제한
```

**영구 방법:**
```hcl
# terraform.tfvars
ec2_instance_type = "t3.small"  # 2GB RAM

# Terraform 재적용
terraform apply
```

---

## 네트워크 문제

### ❌ Cannot access EC2 from internet

**증상:** http://<ec2-ip>:8080 접속 불가

**원인:**
1. Security Group이 8080 포트를 차단
2. Elastic IP가 할당되지 않음
3. 애플리케이션이 실행되지 않음

**해결:**
```bash
# 1. Elastic IP 확인
aws ec2 describe-addresses --region ap-northeast-2

# 2. Security Group 확인 (8080 포트)
aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=catxi-app-sg" \
  --region ap-northeast-2

# 3. EC2 인스턴스 상태
aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=catxi-app-server" \
  --region ap-northeast-2

# 4. 애플리케이션 로그
ssh -i popol-key.pem ubuntu@<ec2-ip>
docker-compose -f /home/ubuntu/catxi/docker-compose.prod.yml logs app
```

---

### ❌ SSH Connection Timeout

**증상:**
```
ssh: connect to host <ec2-ip> port 22: Connection timed out
```

**원인:** Security Group이 SSH를 차단

**해결:**
```bash
# Security Group에서 Jenkins IP 확인
aws ec2 describe-security-groups \
  --group-ids <sg-id> \
  --region ap-northeast-2

# Jenkins IP 추가 (terraform.tfvars 수정)
jenkins_ip = "3.34.123.45/32"  # 실제 Jenkins IP

# Terraform 재적용
cd terraform
terraform apply
```

---

## 데이터베이스 문제

### ❌ RDS: Too many connections

**증상:**
```
java.sql.SQLException: Too many connections
```

**원인:**
1. Connection pool 설정이 RDS max_connections를 초과
2. Connection leak

**해결:**
```bash
# 1. RDS Parameter Group 확인
aws rds describe-db-parameters \
  --db-parameter-group-name catxi-mysql-params \
  --region ap-northeast-2 | grep max_connections

# 2. application-prod.yml 수정
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # max_connections(100)보다 작게

# 3. Connection 모니터링
mysql -h <rds-endpoint> -u catxi_admin -p
SHOW STATUS WHERE `variable_name` = 'Threads_connected';
SHOW PROCESSLIST;
```

---

### ❌ RDS: Slow queries

**증상:** 애플리케이션 응답 시간이 느림

**원인:** 인덱스가 없거나 비효율적인 쿼리

**해결:**
```bash
# 1. Slow Query Log 확인
aws rds describe-db-log-files \
  --db-instance-identifier catxi-db \
  --region ap-northeast-2

# 2. Slow Query 다운로드
aws rds download-db-log-file-portion \
  --db-instance-identifier catxi-db \
  --log-file-name slowquery/mysql-slowquery.log \
  --region ap-northeast-2

# 3. Performance Insights 활성화
# RDS Console → catxi-db → Configuration → Performance Insights

# 4. 쿼리 최적화
# - 인덱스 추가
# - N+1 쿼리 해결
# - QueryDSL fetch join 사용
```

---

## 일반적인 디버깅 체크리스트

### 애플리케이션이 시작되지 않을 때

```bash
# 1. EC2 SSH 접속
ssh -i popol-key.pem ubuntu@<ec2-ip>

# 2. 컨테이너 상태 확인
cd /home/ubuntu/catxi
docker-compose -f docker-compose.prod.yml ps

# 3. 로그 확인
docker-compose -f docker-compose.prod.yml logs -f app
docker-compose -f docker-compose.prod.yml logs -f redis

# 4. 환경변수 확인
cat .env

# 5. 네트워크 확인
docker network ls
docker network inspect catxi_catxi-network

# 6. 리소스 확인
docker stats

# 7. 재시작
docker-compose -f docker-compose.prod.yml restart
```

### Jenkins 파이프라인 실패 시

```
1. Console Output 확인 (어느 Stage에서 실패했는지)
2. 해당 Stage의 에러 메시지 복사
3. Credentials 확인
4. Terraform state 확인
5. EC2 SSH 접속 가능 여부 확인
6. 수동으로 실패한 명령어 실행해보기
```

### 성능 문제 시

```bash
# 1. CPU/Memory 사용률
htop

# 2. Docker 리소스
docker stats

# 3. 네트워크 I/O
iftop

# 4. 디스크 I/O
iotop

# 5. 프로세스 확인
ps aux | grep java

# 6. CloudWatch 메트릭 확인
# AWS Console → CloudWatch → Metrics
```

---

## 긴급 복구 절차

### 애플리케이션 완전 다운 시

```bash
# 1. 이전 버전으로 롤백
./scripts/rollback.sh <previous-build-number>

# 2. 롤백 실패 시 수동 재배포
ssh -i popol-key.pem ubuntu@<ec2-ip>
cd /home/ubuntu/catxi
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d

# 3. Health Check
curl http://localhost:8080/actuator/health
```

### RDS 장애 시

```bash
# 1. RDS 상태 확인
aws rds describe-db-instances \
  --db-instance-identifier catxi-db \
  --region ap-northeast-2

# 2. Multi-AZ 자동 failover 대기 (1-2분)

# 3. 복구 안 될 경우 snapshot에서 복구
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier catxi-db-restored \
  --db-snapshot-identifier <snapshot-id>

# 4. Terraform state 업데이트
terraform import module.rds.aws_db_instance.main catxi-db-restored
```

---

## 로그 위치

| 컴포넌트 | 로그 위치 |
|----------|----------|
| **Spring Boot** | `/home/ubuntu/catxi/logs/catxi-backend-prod.log` |
| **Docker Logs** | `docker-compose logs app` |
| **RDS Error Log** | CloudWatch Logs → `/aws/rds/instance/catxi-db/error` |
| **RDS Slow Query** | CloudWatch Logs → `/aws/rds/instance/catxi-db/slowquery` |
| **Jenkins** | Jenkins UI → Console Output |
| **Terraform** | Jenkins Console Output (Stage 5) |

---

## 유용한 명령어

### Docker
```bash
# 컨테이너 재시작
docker-compose -f docker-compose.prod.yml restart app

# 로그 실시간 확인
docker-compose -f docker-compose.prod.yml logs -f --tail=100 app

# 컨테이너 내부 접속
docker exec -it catxi-app bash

# 리소스 사용량
docker stats

# 네트워크 확인
docker network inspect catxi_catxi-network
```

### AWS CLI
```bash
# EC2 인스턴스 정보
aws ec2 describe-instances --filters "Name=tag:Name,Values=catxi-app-server"

# RDS 정보
aws rds describe-db-instances --db-instance-identifier catxi-db

# Security Group 규칙
aws ec2 describe-security-groups --group-ids <sg-id>

# ECR 이미지 목록
aws ecr list-images --repository-name catxi-backend
```

### MySQL
```bash
# 연결
mysql -h <rds-endpoint> -u catxi_admin -p

# 데이터베이스 확인
SHOW DATABASES;
USE catxi;
SHOW TABLES;

# Connection 확인
SHOW PROCESSLIST;
SHOW STATUS LIKE 'Threads%';
```

---

## 추가 지원

문제가 해결되지 않으면:

1. **GitHub Issues**: https://github.com/Team-Catxi/Catxi/issues
2. **AWS Support**: AWS Console → Support
3. **문서 확인**: [DEPLOYMENT.md](./DEPLOYMENT.md), [INFRASTRUCTURE.md](./INFRASTRUCTURE.md)
