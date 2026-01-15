# ECR Module Variables

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "repository_name" {
  description = "ECR repository name"
  type        = string
}

variable "image_tag_mutability" {
  description = "Image tag mutability setting"
  type        = string
  default     = "MUTABLE"
}

variable "image_retention_count" {
  description = "Number of images to retain"
  type        = number
  default     = 10
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
}
