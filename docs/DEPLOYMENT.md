# Catxi Backend - 배포 가이드

## 📋 목차

1. [사전 요구사항](#사전-요구사항)
2. [배포 아키텍처](#배포-아키텍처)
3. [초기 설정](#초기-설정)
4. [배포 단계](#배포-단계)
5. [트러블슈팅](#트러블슈팅)

---

## 사전 요구사항

### 로컬 환경
- AWS CLI 설치 및 설정
- Terraform >= 1.0
- Git
- SSH 클라이언트

### AWS 계정
- AWS 계정 및 Access Key
- IAM 권한: EC2, RDS, VPC, ECR, S3, DynamoDB

### Jenkins 서버 (이미 설정됨)
- Ubuntu 24.04 LTS (t2.micro)
- Java 17
- Terraform 설치
- 플러그인: Terraform, SSH Agent
- Credentials: github-login, ec2-ssh-key

### 기타
- Kakao Developers 앱 (OAuth)
- Firebase 프로젝트 (FCM)
- Discord Webhook (선택사항)

---

## 배포 아키텍처

```
GitHub (main branch)
    ↓
Jenkins Pipeline (7 stages)
    ↓
AWS Infrastructure
    ├─ VPC (10.0.0.0/16)
    │   ├─ Public Subnet: EC2 App Server
    │   └─ Private Subnet: RDS MySQL
    ├─ ECR: Docker Images
    ├─ S3: Terraform State
    └─ DynamoDB: Terraform Locks
```

**비용 예상:** 월 $25-30
- EC2 t2.micro: ~$8.5
- RDS db.t3.micro: ~$15
- 기타 (S3, ECR 등): ~$2-5

---

## 초기 설정

### Step 1: Terraform Backend 초기화 (1회만)

S3 버킷과 DynamoDB 테이블을 생성합니다.

```bash
cd /path/to/Catxi
chmod +x scripts/*.sh
./scripts/init-terraform-backend.sh
```

**생성되는 리소스:**
- S3 버킷: `catxi-terraform-state`
  - Versioning 활성화
  - 암호화 활성화
  - Public 접근 차단
- DynamoDB 테이블: `catxi-terraform-locks`

### Step 2: Terraform 변수 설정

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
nano terraform.tfvars
```

**필수 수정 항목:**

```hcl
# Jenkins IP 제한 (보안 필수!)
jenkins_ip = "3.34.123.45/32"  # Jenkins 서버의 실제 IP

# RDS 비밀번호 (16자 이상 권장)
rds_password = "Strong-Password-Min-16-Chars!"

# 소유자 정보
common_tags = {
  Project     = "Catxi"
  Environment = "Production"
  ManagedBy   = "Terraform"
  Owner       = "YourName"  # 실제 이름
}
```

**선택 항목:**
- `rds_multi_az = true` - 고가용성 필요 시 (비용 2배)
- `ec2_instance_type = "t3.small"` - 성능 필요 시

### Step 3: AWS Credentials 설정

```bash
aws configure
# AWS Access Key ID: 입력
# AWS Secret Access Key: 입력
# Default region: ap-northeast-2
# Default output format: json
```

**확인:**
```bash
aws sts get-caller-identity
# 계정 ID 확인
```

### Step 4: Jenkins Credentials 추가

Jenkins UI → Manage Jenkins → Credentials → Global → Add Credentials

| Credential ID | Type | 값 | 비고 |
|--------------|------|-----|------|
| `aws-credentials` | AWS Credentials | Access Key + Secret | Terraform/ECR 접근 |
| `aws-account-id` | Secret Text | 12자리 숫자 | AWS 계정 ID |
| `db-password` | Secret Text | RDS 비밀번호 | terraform.tfvars와 동일 |
| `redis-password` | Secret Text | 랜덤 생성 | `openssl rand -base64 32` |
| `jwt-secret-key` | Secret Text | 랜덤 생성 | `openssl rand -base64 48` |
| `kakao-client-id` | Secret Text | Kakao OAuth ID | developers.kakao.com |
| `kakao-client-secret` | Secret Text | Kakao OAuth Secret | developers.kakao.com |
| `discord-webhook-url` | Secret Text | Discord Webhook URL | (선택사항) |

**랜덤 키 생성:**
```bash
# Redis Password
openssl rand -base64 32

# JWT Secret Key
openssl rand -base64 48
```

### Step 5: Firebase Service Account 저장

**로컬에서 Jenkins 서버로 전송:**

```bash
# Firebase JSON 파일 준비
# firebase-service-account.json 다운로드 (Firebase Console)

# Jenkins 서버에 업로드
scp -i popol-key.pem firebase-service-account.json ubuntu@<jenkins-ip>:/tmp/

# Jenkins 서버에 SSH 접속
ssh -i popol-key.pem ubuntu@<jenkins-ip>

# Jenkins secrets 디렉토리로 이동
sudo mkdir -p /var/jenkins_home/secrets
sudo mv /tmp/firebase-service-account.json /var/jenkins_home/secrets/
sudo chmod 600 /var/jenkins_home/secrets/firebase-service-account.json
sudo chown jenkins:jenkins /var/jenkins_home/secrets/firebase-service-account.json
```

---

## 배포 단계

### Step 6: Terraform 인프라 생성

**로컬에서 실행:**

```bash
cd terraform

# 초기화 (backend 설정)
terraform init

# 플랜 확인
terraform plan

# 인프라 생성 (5-10분 소요)
terraform apply
# yes 입력
```

**생성되는 리소스:**
- VPC (10.0.0.0/16)
- Public Subnet x2 (AZ-a, AZ-c)
- Private Subnet x2 (AZ-a, AZ-c)
- Internet Gateway
- Route Tables
- Security Groups (app, rds)
- IAM Role (EC2 → ECR)
- ECR Repository
- RDS MySQL (db.t3.micro)
- EC2 Instance (t2.micro)
- Elastic IP

**출력 확인:**
```bash
terraform output

# 주요 출력값
# ec2_public_ip: EC2 서버 IP
# rds_endpoint: RDS 주소
# ecr_repository_url: Docker 이미지 저장소
```

### Step 7: Jenkins Pipeline 설정

**Jenkins UI:**

1. **New Item** 클릭
2. **Item name:** `catxi-backend-pipeline`
3. **Type:** Pipeline 선택
4. **OK** 클릭

**Pipeline 설정:**

5. **Build Triggers:**
   - ✅ GitHub hook trigger for GITScm polling

6. **Pipeline:**
   - **Definition:** Pipeline script from SCM
   - **SCM:** Git
   - **Repository URL:** `https://github.com/Team-Catxi/Catxi.git`
   - **Credentials:** `github-login`
   - **Branch:** `*/main`
   - **Script Path:** `Jenkinsfile`

7. **Save** 클릭

### Step 8: 첫 배포 실행

**수동 실행:**
```
Jenkins → catxi-backend-pipeline → Build Now
```

**파이프라인 스테이지:**
1. ✅ Checkout - GitHub에서 코드 가져오기
2. ✅ Build - Gradle로 JAR 빌드
3. ✅ Docker Build - 이미지 생성
4. ✅ Push to ECR - AWS ECR에 푸시
5. ✅ Terraform - 인프라 확인/적용
6. ✅ Deploy - EC2에 배포
7. ✅ Health Check - /actuator/health 확인

**배포 성공 시:**
- Discord 알림 수신 (설정한 경우)
- Jenkins 콘솔에 성공 메시지
- EC2 IP로 접근 가능

**접속 확인:**
```bash
# EC2 IP는 terraform output에서 확인
curl http://<ec2-ip>:8080/actuator/health

# 또는 브라우저에서
http://<ec2-ip>:8080/actuator/health
```

### Step 9: GitHub Webhook 설정 (자동 배포)

**GitHub Repository:**

1. Settings → Webhooks → Add webhook
2. **Payload URL:** `http://<jenkins-ip>:8080/github-webhook/`
3. **Content type:** application/json
4. **Events:** Just the push event
5. **Add webhook**

**이제 `main` 브랜치에 push하면 자동 배포됩니다!**

---

## 일상 운영

### 코드 변경 후 배포

```bash
# 로컬에서 개발
git add .
git commit -m "feat: 새 기능 추가"
git push origin main

# Jenkins가 자동으로 배포 시작
# Discord로 결과 알림 수신
```

### 환경변수 변경

**Jenkins Credentials 수정 후:**

```bash
# Jenkins UI에서 수동 실행
Jenkins → catxi-backend-pipeline → Build Now
```

### 수동 롤백

```bash
# 이전 빌드 번호로 롤백
./scripts/rollback.sh 42
```

### EC2 SSH 접속

```bash
# EC2 IP는 terraform output에서 확인
ssh -i popol-key.pem ubuntu@<ec2-ip>

# 로그 확인
cd /home/ubuntu/catxi
docker-compose -f docker-compose.prod.yml logs -f app

# 컨테이너 상태
docker-compose -f docker-compose.prod.yml ps
```

### RDS 접속

```bash
# RDS endpoint는 terraform output에서 확인
mysql -h <rds-endpoint> -P 3306 -u catxi_admin -p
# 비밀번호 입력

# 또는 EC2에서
ssh -i popol-key.pem ubuntu@<ec2-ip>
docker exec -it catxi-app bash
mysql -h $DB_HOST -P 3306 -u $DB_USER -p$DB_PW
```

---

## 인프라 삭제

**주의: 모든 데이터가 삭제됩니다!**

```bash
cd terraform

# RDS 스냅샷 생성 (선택사항)
aws rds create-db-snapshot \
  --db-instance-identifier catxi-db \
  --db-snapshot-identifier catxi-backup-$(date +%Y%m%d)

# 인프라 삭제
terraform destroy
# yes 입력

# Backend 리소스도 삭제 시
aws s3 rb s3://catxi-terraform-state --force
aws dynamodb delete-table --table-name catxi-terraform-locks --region ap-northeast-2
```

---

## 모니터링

### CloudWatch Logs

```bash
# 애플리케이션 로그
aws logs tail /aws/ec2/catxi-app --follow --region ap-northeast-2

# RDS 로그
aws logs tail /aws/rds/instance/catxi-db/error --follow --region ap-northeast-2
```

### 메트릭 확인

- **EC2:** AWS Console → EC2 → Monitoring
- **RDS:** AWS Console → RDS → catxi-db → Monitoring
- **Application:** http://<ec2-ip>:8080/actuator/prometheus

### 알람 설정

CloudWatch Alarms 자동 생성됨:
- EC2 CPU > 80%
- EC2 Status Check Failed

**SNS Topic 추가 시:**
```bash
# terraform/modules/ec2/main.tf 수정
alarm_actions = [aws_sns_topic.alerts.arn]
```

---

## 보안 체크리스트

- [ ] Jenkins IP만 EC2 SSH 허용
- [ ] RDS는 private subnet
- [ ] RDS 비밀번호 16자 이상
- [ ] .env, terraform.tfvars gitignore 확인
- [ ] SSH 키 안전하게 보관
- [ ] AWS Access Key 정기 교체
- [ ] ECR 이미지 스캔 활성화
- [ ] CloudWatch 알람 설정
- [ ] RDS 백업 활성화 (7일)
- [ ] SSL/TLS 인증서 적용 (추천)

---

## 다음 단계

### 성능 개선
- [ ] ALB + Auto Scaling Group 구성
- [ ] CloudFront CDN 적용
- [ ] RDS Read Replica 추가

### 보안 강화
- [ ] AWS Secrets Manager 도입
- [ ] VPN/Bastion Host 구성
- [ ] WAF 설정

### 모니터링 강화
- [ ] Prometheus + Grafana 구축
- [ ] ELK Stack 로그 수집
- [ ] APM 도구 통합

---

## 참고 문서

- [Terraform 코드](../terraform/)
- [Jenkinsfile](../Jenkinsfile)
- [인프라 문서](./INFRASTRUCTURE.md)
- [트러블슈팅](./TROUBLESHOOTING.md)
