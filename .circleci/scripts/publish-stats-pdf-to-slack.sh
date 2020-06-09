#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

SOURCE_DESC=$1
cd $STATS_PARENT_DIR
LINK_TXT="CircleCi Job #${CIRCLE_BUILD_NUM}"
echo ":bulb: Stats for <${CIRCLE_BUILD_URL}|${LINK_TXT}> of Branch ${CIRCLE_BRANCH} Build ($SOURCE_DESC):" > msg.txt

# To default channel (hedera-regression)
DEFAULT_CHANNEL="hedera-regression"
${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
    -c $DEFAULT_CHANNEL \
    -a \
    -t "$STATS_PARENT_DIR/msg.txt"
${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
    -c $DEFAULT_CHANNEL \
    -f $INSIGHT_PY_PDF_PATH \
    -n SelectedPlatformStats

${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
    -c $DEFAULT_CHANNEL \
    -f ${CLIENT_LOG_DIR}/regression-test-summary.txt \
    -s $OVERALL_STATUS
