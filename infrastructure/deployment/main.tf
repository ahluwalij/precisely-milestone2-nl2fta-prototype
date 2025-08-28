terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  backend "s3" {
    bucket = "nl2fta-terraform-state-850995549138"
    key    = "simple/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" {
  region = var.aws_region
}

# Security group for EC2
resource "aws_security_group" "app" {
  name_prefix = "nl2fta-simple-"
  description = "Security group for NL2FTA app"

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "SSH"
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP"
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound"
  }

  tags = {
    Name = "nl2fta-simple-sg"
  }
}

# EC2 instance
resource "aws_instance" "app" {
  ami           = "ami-0e86e20dae9224db8" # Ubuntu 24.04 LTS in us-east-1
  instance_type = "t3.medium"
  
  key_name = var.ssh_key_name
  
  vpc_security_group_ids = [aws_security_group.app.id]
  
  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }
  
  user_data = templatefile("${path.module}/user-data.sh", {
    github_token = var.github_token
  })
  
  tags = {
    Name = "nl2fta-simple"
  }
}

# Elastic IP
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"
  
  tags = {
    Name = "nl2fta-simple-eip"
  }
}

# Outputs
output "elastic_ip" {
  value = aws_eip.app.public_ip
  description = "The Elastic IP address of the EC2 instance"
}

output "instance_id" {
  value = aws_instance.app.id
  description = "The ID of the EC2 instance"
}

variable "aws_region" {
  default = "us-east-1"
}

variable "ssh_key_name" {
  description = "Name of the SSH key pair in AWS"
}

variable "github_token" {
  description = "GitHub token for pulling private repos"
  sensitive   = true
}