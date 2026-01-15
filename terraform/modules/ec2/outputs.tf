# EC2 Module Outputs

output "instance_id" {
  description = "EC2 instance ID"
  value       = aws_instance.app.id
}

output "public_ip" {
  description = "Elastic IP (public) - 수동 할당"
  value       = var.elastic_ip
}

output "private_ip" {
  description = "Private IP address"
  value       = aws_instance.app.private_ip
}

output "public_dns" {
  description = "Public DNS name"
  value       = aws_instance.app.public_dns
}

output "availability_zone" {
  description = "Availability zone"
  value       = aws_instance.app.availability_zone
}

output "ami_id" {
  description = "AMI ID used"
  value       = aws_instance.app.ami
}
