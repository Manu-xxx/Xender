#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ $# -lt 1 ]; then
  echo "USAGE: $0 branch"
  exit 1
fi

if [ -f ${INFRASTRUCTURE_REPO} ]; then
  ci_echo "Infrastructure already cloned from '$1', doing nothing." \
    | tee -a ${REPO}/test-clients/output/hapi-client.log
else
  BRANCH="$1"
  ci_echo "Using '$BRANCH' of infrastructure repo..." \
     | tee -a ${REPO}/test-clients/output/hapi-client.log
  cd /
  GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" \
    git clone git@github.com:swirlds${INFRASTRUCTURE_REPO}.git \
      --branch "$BRANCH" \
        | tee -a ${REPO}/test-clients/output/hapi-client.log
fi
