#!/usr/bin/env bash

pcli diagram \
    -l 'applicationTransactionPrehandler:futures:linkedEventIntake' \
    -s 'eventWindowManager:non-ancient event window:ʘ' \
    -s 'heartbeat:heartbeat:♡' \
    -s 'eventCreationManager:non-validated events:†' \
    -s 'applicationTransactionPrehandler:futures:★' \
    -s 'pcesReplayer:done streaming pces:@' \
    -s 'inOrderLinker:events to gossip:g' \
    -s 'runningHashUpdate:running hash update:§' \
    -s 'linkedEventIntake:flush request:Ξ' \
    -g 'Event Validation:internalEventValidator,eventDeduplicator,eventSignatureValidator' \
    -g 'Event Hashing:eventHasher,postHashCollector' \
    -g 'Orphan Buffer:orphanBuffer,orphanBufferSplitter' \
    -g 'Linked Event Intake:linkedEventIntake,linkedEventIntakeSplitter,eventWindowManager' \
    -g 'State File Management:saveToDiskFilter,signedStateFileManager,extractOldestMinimumGenerationOnDisk,toStateWrittenToDiskAction' \
    -g 'State Signature Collection:stateSignatureCollector,reservedStateSplitter,allStatesReserver,completeStateFilter,completeStatesReserver,extractConsensusSignatureTransactions,extractPreconsensusSignatureTransactions' \
    -g 'Intake Pipeline:Event Validation,Orphan Buffer,Event Hashing' \
    -g 'Preconsensus Event Stream:pcesSequencer,pcesWriter,eventDurabilityNexus' \
    -g 'Consensus Event Stream:getEvents,eventStreamManager' \
    -g 'Consensus Pipeline:inOrderLinker,Linked Event Intake,g' \
    -g 'Event Creation:futureEventBuffer,futureEventBufferSplitter,eventCreationManager' \
    -g 'Gossip:gossip,shadowgraph' \
    -c 'Consensus Event Stream' \
    -c 'Orphan Buffer' \
    -c 'Linked Event Intake' \
    -c 'State Signature Collection' \
    -c 'State File Management'
