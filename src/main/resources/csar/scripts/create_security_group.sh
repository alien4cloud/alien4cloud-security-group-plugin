#!/bin/bash -xe

if [ -z "$VPC_ID" ] ; then
  output=$(aws ec2 create-security-group --group-name $SECGROUP_NAME --description "Alien4Cloud generated security group $NODE/$INSTANCE")
else
  output=$(aws ec2 create-security-group --group-name $SECGROUP_NAME --vpc-id $VPC_ID --description "Alien4Cloud generated security group $NODE/$INSTANCE")
fi

export AWS_SECURITY_GROUP_ID=$(echo $output | sed 's#.*"GroupId": "\(.*\)".*#\1#')
