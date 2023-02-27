#!/usr/bin/env bash
#
# Copyright 2016-2022 Hedera Hashgraph, LLC
#
# This software is the confidential and proprietary information of
# Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
# disclose such Confidential Information and shall use it only in
# accordance with the terms of the license agreement you entered into
# with Hedera Hashgraph.
#
# HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
# THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
# TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
# ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
# DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#

cd "`dirname "$0"`"

. _config.sh; . _functions.sh; . _experimentConfig.sh

chooseTest

makeConfigFile 0 100 1024 150000 $testJar

echo "Uploading to node 0, ip ${publicAddresses[0]}"
uploadFromSdkToNode ${publicAddresses[0]} "$testDir/"

echo "Copying from node 0 to others"
runFunctionOnRemoteNode ${publicAddresses[0]} "uploadRemoteExperimentToAllNodes"

echo "Starting all nodes"
runFunctionOnRemoteNode ${publicAddresses[0]} 'startAllNodes'
