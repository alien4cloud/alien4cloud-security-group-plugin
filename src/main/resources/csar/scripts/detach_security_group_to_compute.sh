#!/bin/bash -xe

SECGROUP_IDS=$(aws ec2 describe-instance-attribute --instance-id $AWS_INSTANCE_ID --attribut groupSet --output json | grep sg- | grep -v $AWS_SECGROUP_ID | sed 's/.*: "\(sg-.*\)"/\1/')
aws ec2 modify-instance-attribute  --instance-id $AWS_INSTANCE_ID --groups $SECGROUP_IDS
