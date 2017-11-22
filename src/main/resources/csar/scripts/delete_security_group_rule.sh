#!/bin/bash -xe

#
# inputs from tosca:
#
# RULE_NAME
# SEC_GROUP_ID
# PROTOCOL
# DIRECTION
# PORT
# REMOTE

if [ "$PROTOCOL" = "icmp" ] ; then
  echo "icmp not supported yet"
  exit 1
fi

AWS_EC2_CMD="aws ec2"

if [ "$DIRECTION" = "inbound" ] ; then
  SUB_CMD=revoke-security-group-ingress
else
  SUB_CMD=revoke-security-group-egress
fi

if $(echo $REMOTE | grep -E "([0-9]{1,3}\.){3}[0-9]{1,3}/[0-9]{0,2}") ; then
  # the remote is a CIDR 
  REMOTE_VALUE="--cidr $REMOTE"
else
  REMOTE_VALUE="--source-group $REMOTE"
fi

$AWS_EC2_CMD $SUB_CMD \
    --group-id $SEC_GROUP_ID \
    --protocol $PROTOCOL \
    --port $PORT \
    $REMOTE_VALUE

