#!/usr/bin/env bash

pcli diagram \
    -l 'applicationTransactionPrehandler:futures:consensusRoundHandler' \
    -l 'eventDurabilityNexus:wait for durability:consensusRoundHandler' \
    -l 'eventCreationManager:get transactions:transactionPool' \
    -s 'eventWindowManager:non-ancient event window:🌀' \
    -s 'heartbeat:heartbeat:❤️' \
    -s 'applicationTransactionPrehandler:futures:🔮' \
    -s 'eventDurabilityNexus:wait for durability:🕑' \
    -s 'pcesReplayer:done streaming pces:✅' \
    -s 'inOrderLinker:events to gossip:📬' \
    -s 'getKeystoneEventSequenceNumber:flush request:🚽' \
    -s 'extractOldestMinimumGenerationOnDisk:minimum identifier to store:📀' \
    -s 'eventCreationManager:non-validated events:🍎' \
    -s 'postHasher_getState:state to sign:🖋️' \
    -g 'Event Validation:internalEventValidator,eventDeduplicator,eventSignatureValidator' \
    -g 'Event Hashing:eventHasher,postHashCollector' \
    -g 'Orphan Buffer:orphanBuffer,orphanBufferSplitter' \
    -g 'Consensus Engine:consensusEngine,consensusEngineSplitter,eventWindowManager,getKeystoneEventSequenceNumber' \
    -g 'State File Management:saveToDiskFilter,signedStateFileManager,extractOldestMinimumGenerationOnDisk,toStateWrittenToDiskAction,statusManager_submitStateWritten' \
    -g 'State Signature Collection:stateSignatureCollector,reservedStateSplitter,allStatesReserver,completeStateFilter,completeStatesReserver,extractConsensusSignatureTransactions,extractPreconsensusSignatureTransactions' \
    -g 'Preconsensus Event Stream:pcesSequencer,pcesWriter,eventDurabilityNexus,🕑' \
    -g 'Consensus Event Stream:getEvents,eventStreamManager' \
    -g 'Consensus Pipeline:inOrderLinker,Consensus Engine,📬,🌀,🚽' \
    -g 'Event Creation:futureEventBuffer,futureEventBufferSplitter,eventCreationManager,transactionPool,🍎' \
    -g 'Gossip:gossip,shadowgraph' \
    -g 'ISS Detector:issDetector,issNotificationSplitter,issHandler,issNotificationEngine,statusManager_submitCatastrophicFailure' \
    -g 'Heartbeat:heartbeat,❤️' \
    -g 'PCES Replay:pcesReplayer,✅' \
    -g 'Transaction Prehandling:applicationTransactionPrehandler,🔮' \
    -g 'Signature Management:State Signature Collection,stateSigner,ISS Detector,latestCompleteStateNotification' \
    -g 'Consensus Round Handler:consensusRoundHandler,postHandler_stateAndRoundReserver,postHandler_getRoundNumber,postHandler_stateReserver' \
    -g 'State Hasher:stateHasher,postHasher_stateAndRoundReserver,postHasher_getConsensusRound,postHasher_stateReserver' \
    -g 'State Modification:Consensus Round Handler,runningHashUpdate' \
    -c 'Consensus Event Stream' \
    -c 'Orphan Buffer' \
    -c 'Consensus Engine' \
    -c 'State Signature Collection' \
    -c 'State File Management' \
    -c 'Consensus Round Handler' \
    -c 'State Hasher' \
    -c 'ISS Detector'
