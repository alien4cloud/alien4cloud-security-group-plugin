#!/bin/bash -xe

EXISTING_SECGROUP_IDS=$(aws ec2 describe-instance-attribute --instance-id $AWS_INSTANCE_ID --attribut groupSet --output json | grep sg- | sed 's/.*: "\(sg-.*\)"/\1/')

aws ec2 modify-instance-attribute  --instance-id $AWS_INSTANCE_ID --groups $EXISTING_SECGROUP_IDS $AWS_SECGROUP_ID
