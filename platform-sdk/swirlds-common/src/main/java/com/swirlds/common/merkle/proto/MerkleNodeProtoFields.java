package com.swirlds.common.merkle.proto;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;

public final class MerkleNodeProtoFields {

    // Common node fields

    public static final int NUM_NODE_HASH = 1;

    public static final FieldDefinition FIELD_NODE_HASH = new FieldDefinition(
            "hash", FieldType.BYTES, false, false, false, NUM_NODE_HASH);

    // Hash

    // message Hash {
    //     uint32 digestType = 1;
    //     bytes data = 2;
    // }

    public static final int NUM_HASH_DIGESTTYPE = 1;
    public static final int NUM_HASH_DATA = 2;

    public static final FieldDefinition FIELD_HASH_DIGESTTYPE = new FieldDefinition(
            "digestType", FieldType.UINT32, false, true, false, NUM_HASH_DIGESTTYPE);

    public static final FieldDefinition FIELD_HASH_DATA = new FieldDefinition(
            "data", FieldType.BYTES, false, false, false, NUM_HASH_DATA);

    // VirtualLeafRecord

    // message VirtualLeafRecord {
    //     uint64 path = 1;
    //     <key message type> key = 2;
    //     <value message type> value = 3;
    // }

    public static final int NUM_VLEAFRECORD_PATH = 1;
    public static final int NUM_VLEAFRECORD_KEY = 2;
    public static final int NUM_VLEAFRECORD_VALUE = 3;

    public static final FieldDefinition FIELD_VLEAFRECORD_PATH = new FieldDefinition(
            "path", FieldType.UINT64, false, true, false, NUM_VLEAFRECORD_PATH);

    public static final FieldDefinition FIELD_VLEAFRECORD_KEY = new FieldDefinition(
            "key", FieldType.MESSAGE, false, false, false, NUM_VLEAFRECORD_KEY);

    public static final FieldDefinition FIELD_VLEAFRECORD_VALUE = new FieldDefinition(
            "value", FieldType.MESSAGE, false, false, false, NUM_VLEAFRECORD_VALUE);

    // Queue (FCQueue)

    // message Queue {
    //     Hash hash = 1;
    //     repeated <value type> value = 2;
    // }

    public static final int NUM_QUEUE_VALUE = 2;

    public static final FieldDefinition FIELD_QUEUE_VALUE = new FieldDefinition(
            "value", FieldType.MESSAGE, true, true, false, NUM_QUEUE_VALUE);

    // VirtualMap

    // message VirtualMap {
    //     Hash hash = 1;
    //     VirtualMapState state = 2;
    //     VirtualRootNode virtualRoot = 3;
    // }

    public static final int NUM_VIRTUALMAP_STATE = 2;
    public static final int NUM_VIRTUALMAP_VIRTUALROOT = 3;

    public static final FieldDefinition FIELD_VIRTUALMAP_STATE = new FieldDefinition(
            "state", FieldType.MESSAGE, false, false, false, NUM_VIRTUALMAP_STATE);

    public static final FieldDefinition FIELD_VIRTUALMAP_VIRTUALROOT = new FieldDefinition(
            "virtualRoot", FieldType.MESSAGE, false, false, false, NUM_VIRTUALMAP_VIRTUALROOT);

    // Virtual map state

    // message VirtualMapState {
    //     Hash hash = 1;
    //     bytes label = 11;
    //     uint32 firstLeafPath = 12;
    //     unit32 lastLeafPath = 13;
    // }

    public static final int NUM_VMSTATE_LABEL = 11;
    public static final int NUM_VMSTATE_FIRSTLEAFPATH = 12;
    public static final int NUM_VMSTATE_LASTLEAFPATH = 13;

    public static final FieldDefinition FIELD_VMSTATE_LABEL = new FieldDefinition(
            "label", FieldType.STRING, false, true, false, NUM_VMSTATE_LABEL);

    public static final FieldDefinition FIELD_VMSTATE_FIRSTLEAFPATH = new FieldDefinition(
            "firstLeafPath", FieldType.UINT32, false, true, false, NUM_VMSTATE_FIRSTLEAFPATH);

    public static final FieldDefinition FIELD_VMSTATE_LASTLEAFPATH = new FieldDefinition(
            "lastLeafPath", FieldType.UINT32, false, true, false, NUM_VMSTATE_LASTLEAFPATH);

    // Virtual root node

    // message VirtualRootNode {
    //     Hash hash = 1;
    //     // No virtual root children fields
    //     VirtualCache cache = 11;
    // }

    public static final int NUM_VRNODE_CACHE = 11;

    public static final FieldDefinition FIELD_VRNODE_CACHE = new FieldDefinition(
            "cache", FieldType.MESSAGE, false, true, false, NUM_VRNODE_CACHE);

    // Virtual node cache

    // message VirtualCache {
    //     uint64 fastCopyVersion = 1;
    //     repeated CacheKeyToLeafEntry keyToLeaf = 2;
    //     repeated CachePathToKeyEntry pathToKey = 3;
    //     repeated CachePathToHashEntry pathToHash = 4;
    // }

    public static final int NUM_VNODECACHE_COPYVERSION = 1;
    public static final int NUM_VNODECACHE_KEYTOLEAF = 2;
    public static final int NUM_VNODECACHE_PATHTOKEY = 3;
    public static final int NUM_VNODECACHE_PATHTOHASH = 4;

    public static final FieldDefinition FIELD_VNODECACHE_COPYVERSION = new FieldDefinition(
            "fastCopyVersion", FieldType.UINT64, false, true, false, NUM_VNODECACHE_COPYVERSION);

    public static final FieldDefinition FIELD_VNODECACHE_KEYTOLEAF = new FieldDefinition(
            "keyToLeaf", FieldType.MESSAGE, true, true, false, NUM_VNODECACHE_KEYTOLEAF);

    public static final FieldDefinition FIELD_VNODECACHE_PATHTOKEY = new FieldDefinition(
            "pathToKey", FieldType.MESSAGE, true, true, false, NUM_VNODECACHE_PATHTOKEY);

    public static final FieldDefinition FIELD_VNODECACHE_PATHTOHASH = new FieldDefinition(
            "pathToHash", FieldType.MESSAGE, true, true, false, NUM_VNODECACHE_PATHTOHASH);

    // message CacheKeyToLeafEntry {
    //     uint64 version = 1;
    //     VirtualLeafRecord record = 2;
    //     int32 deleted = 3;
    // }

    public static final int NUM_VNCKEYTOLEAF_VERSION = 1;
    public static final int NUM_VNCKEYTOLEAF_RECORD = 2;
    public static final int NUM_VNCKEYTOLEAF_DELETED = 3;

    public static final FieldDefinition FIELD_VNCKEYTOLEAF_VERSION = new FieldDefinition(
            "version", FieldType.UINT64, false, true, false, NUM_VNCKEYTOLEAF_VERSION);

    public static final FieldDefinition FIELD_VNCKEYTOLEAF_RECORD = new FieldDefinition(
            "record", FieldType.MESSAGE, false, false, false, NUM_VNCKEYTOLEAF_RECORD);

    public static final FieldDefinition FIELD_VNCKEYTOLEAF_DELETED = new FieldDefinition(
            "deleted", FieldType.UINT32, false, true, false, NUM_VNCKEYTOLEAF_DELETED);

    // message CachePathToKeyEntry {
    //     uint64 version = 1;
    //     uint64 path = 2;
    //     VirtualLeafRecord record = 3;
    //     int32 deleted = 4;
    // }

    public static final int NUM_VNCPATHTOKEY_VERSION = 1;
    public static final int NUM_VNCPATHTOKEY_PATH = 2;
    public static final int NUM_VNCPATHTOKEY_KEY = 3;
    public static final int NUM_VNCPATHTOKEY_DELETED = 4;

    public static final FieldDefinition FIELD_VNCPATHTOKEY_VERSION = new FieldDefinition(
            "version", FieldType.UINT64, false, true, false, NUM_VNCPATHTOKEY_VERSION);

    public static final FieldDefinition FIELD_VNCPATHTOKEY_PATH = new FieldDefinition(
            "path", FieldType.UINT64, false, true, false, NUM_VNCPATHTOKEY_PATH);

    public static final FieldDefinition FIELD_VNCPATHTOKEY_KEY = new FieldDefinition(
            "key", FieldType.MESSAGE, false, false, false, NUM_VNCPATHTOKEY_KEY);

    public static final FieldDefinition FIELD_VNCPATHTOKEY_DELETED = new FieldDefinition(
            "deleted", FieldType.UINT32, false, true, false, NUM_VNCPATHTOKEY_DELETED);

    // message CachePathToHashEntry {
    //     uint64 version = 1;
    //     uint64 path = 2;
    //     Hash hash = 3;
    //     int32 deleted = 4;
    // }

    public static final int NUM_VNCPATHTOHASH_VERSION = 1;
    public static final int NUM_VNCPATHTOHASH_PATH = 2;
    public static final int NUM_VNCPATHTOHASH_HASH = 3;
    public static final int NUM_VNCPATHTOHASH_DELETED = 4;

    public static final FieldDefinition FIELD_VNCPATHTOHASH_VERSION = new FieldDefinition(
            "version", FieldType.UINT64, false, true, false, NUM_VNCPATHTOHASH_VERSION);

    public static final FieldDefinition FIELD_VNCPATHTOHASH_PATH = new FieldDefinition(
            "path", FieldType.UINT64, false, true, false, NUM_VNCPATHTOHASH_PATH);

    public static final FieldDefinition FIELD_VNCPATHTOHASH_HASH = new FieldDefinition(
            "hash", FieldType.MESSAGE, false, false, false, NUM_VNCPATHTOHASH_HASH);

    public static final FieldDefinition FIELD_VNCPATHTOHASH_DELETED = new FieldDefinition(
            "deleted", FieldType.UINT32, false, true, false, NUM_VNCPATHTOHASH_DELETED);

    // Hedera root node

    // message MerkleHederaState {
    //     Hash hash = 1;
    //     repeated StateNode stateNode = 2;
    // }

    public static final int NUM_HEDERASTATE_STATENODE = 2;

    public static final FieldDefinition FIELD_HEDERASTATE_STATENODE = new FieldDefinition(
            "stateNode", FieldType.MESSAGE, true, true, false, NUM_HEDERASTATE_STATENODE);

    // message StateNode {
    //     oneof stateNode {
    //         SingletonStateNode singleton = 10;
    //         QueueStateNode queue = 11;
    //         // https://github.com/hashgraph/hedera-services/issues/13781
    //         // KeyValueStateNode keyValue = 12;
    //         VirtualMap kvTokens = 301;
    //         VirtualMap kvAccounts = 302;
    //         VirtualMap kvAliases = 303;
    //         VirtualMap kvNfts = 304;
    //         VirtualMap kvTokenRels = 305;
    //         VirtualMap kvStakingInfo = 306;
    //         VirtualMap kvScheduledById = 307;
    //         VirtualMap kvSchedulesByExpirySec = 308;
    //         VirtualMap kvSchedulesByEquality = 309;
    //         VirtualMap kvBlobs = 310;
    //         VirtualMap kvEvmStorage = 311;
    //         VirtualMap kvEvmBytecode = 312;
    //         VirtualMap kvTopics = 313;
    //     }
    // }

    public static final int NUM_STATENODE_SINGLETON = 10;
    public static final int NUM_STATENODE_QUEUE = 11;
    // https://github.com/hashgraph/hedera-services/issues/13781
    // public static final int NUM_STATENODE_KEYVALUE = 12;
    public static final int NUM_STATENODE_KVTOKENS = 301;
    public static final int NUM_STATENODE_KVACCOUNTS = 302;
    public static final int NUM_STATENODE_KVALIASES = 303;
    public static final int NUM_STATENODE_KVNFTS = 304;
    public static final int NUM_STATENODE_KVTOKENRELS = 305;
    public static final int NUM_STATENODE_KVSTAKINGINFO = 306;
    public static final int NUM_STATENODE_KVSCHEDULESBYID = 307;
    public static final int NUM_STATENODE_KVSCHEDULESBYEXPIRYSEC = 308;
    public static final int NUM_STATENODE_KVSCHEDULESBYEQUALITY = 309;
    public static final int NUM_STATENODE_KVBLOBS = 310;
    public static final int NUM_STATENODE_KVEVMSTORAGE = 311;
    public static final int NUM_STATENODE_KVEVMBYTECODE = 312;
    public static final int NUM_STATENODE_KVTOPICS = 313;

    public static final FieldDefinition FIELD_STATENODE_SINGLETON = new FieldDefinition(
            "singleton", FieldType.MESSAGE, false, true, true, NUM_STATENODE_SINGLETON);

    public static final FieldDefinition FIELD_STATENODE_QUEUE = new FieldDefinition(
            "queue", FieldType.MESSAGE, false, true, true, NUM_STATENODE_QUEUE);

    // https://github.com/hashgraph/hedera-services/issues/13781
    // public static final FieldDefinition FIELD_STATENODE_KEYVALUE = new FieldDefinition(
    //         "keyValue", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KEYVALUE);

    public static final FieldDefinition FIELD_STATENODE_KVTOKENS = new FieldDefinition(
            "tokens", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVTOKENS);

    public static final FieldDefinition FIELD_STATENODE_KVACCOUNTS = new FieldDefinition(
            "accounts", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVACCOUNTS);

    public static final FieldDefinition FIELD_STATENODE_KVALIASES = new FieldDefinition(
            "aliases", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVALIASES);

    public static final FieldDefinition FIELD_STATENODE_KVNFTS = new FieldDefinition(
            "nfts", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVNFTS);

    public static final FieldDefinition FIELD_STATENODE_KVTOKENRELS = new FieldDefinition(
            "tokenRels", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVTOKENRELS);

    public static final FieldDefinition FIELD_STATENODE_KVSTAKINGINFO = new FieldDefinition(
            "stakingInfo", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVSTAKINGINFO);

    public static final FieldDefinition FIELD_STATENODE_KVSCHEDULESBYID = new FieldDefinition(
            "schedulesById", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVSCHEDULESBYID);

    public static final FieldDefinition FIELD_STATENODE_KVSCHEDULESBYEXPIRYSEC = new FieldDefinition(
            "schedulesByExpirySec", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVSCHEDULESBYEXPIRYSEC);

    public static final FieldDefinition FIELD_STATENODE_KVSCHEDULESBYEQUALITY = new FieldDefinition(
            "schedulesByEquality", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVSCHEDULESBYEQUALITY);

    public static final FieldDefinition FIELD_STATENODE_KVBLOBS = new FieldDefinition(
            "blobs", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVBLOBS);

    public static final FieldDefinition FIELD_STATENODE_KVEVMSTORAGE = new FieldDefinition(
            "evmStorage", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVEVMSTORAGE);

    public static final FieldDefinition FIELD_STATENODE_KVEVMBYTECODE = new FieldDefinition(
            "evmBytecode", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVEVMBYTECODE);

    public static final FieldDefinition FIELD_STATENODE_KVTOPICS = new FieldDefinition(
            "topics", FieldType.MESSAGE, false, true, true, NUM_STATENODE_KVTOPICS);

    // Hedera state nodes

    // message StringLeaf {
    //     Hash hash = 1;
    //     bytes value = 2;
    // }

    public static final int NUM_STRINGLEAF_VALUE = 2;

    public static final FieldDefinition FIELD_STRINGLEAF_VALUE = new FieldDefinition(
            "value", FieldType.BYTES, false, false, false, NUM_STRINGLEAF_VALUE);

    // message SingletonStateNode {
    //     Hash hash = 1;
    //     StringLeaf label = 2;
    //     SingletonValueLeaf value = 3;
    // }

    public static final int NUM_SINGLETONSTATE_LABEL = 2;
    public static final int NUM_SINGLETONSTATE_VALUE = 3;

    public static final FieldDefinition FIELD_SINGLETONSTATE_LABEL = new FieldDefinition(
            "label", FieldType.MESSAGE, false, false, false, NUM_SINGLETONSTATE_LABEL);

    public static final FieldDefinition FIELD_SINGLETONSTATE_VALUE = new FieldDefinition(
            "value", FieldType.MESSAGE, false, false, false, NUM_SINGLETONSTATE_VALUE);

    // message QueueStateNode {
    //     Hash hash = 1;
    //     StringLeaf label = 2;
    //     repeated QueueValueLeaf value = 3;
    // }

    public static final int NUM_QUEUESTATE_LABEL = 2;
    public static final int NUM_QUEUESTATE_VALUE = 3;

    public static final FieldDefinition FIELD_QUEUESTATE_LABEL = new FieldDefinition(
            "label", FieldType.MESSAGE, false, false, false, NUM_QUEUESTATE_LABEL);

    public static final FieldDefinition FIELD_QUEUESTATE_VALUE = new FieldDefinition(
            "value", FieldType.MESSAGE, true, false, false, NUM_QUEUESTATE_VALUE);

    // https://github.com/hashgraph/hedera-services/issues/13781
    /*
    // message KeyValueStateNode {
    //     Hash hash = 1;
    //     StringLeaf label = 2;
    //     KeyValueValueLeaf value = 3;
    // }

    public static final int NUM_KEYVALUESTATE_LABEL = 2;
    public static final int NUM_KEYVALUESTATE_VALUE = 3;

    public static final FieldDefinition FIELD_KEYVALUESTATE_LABEL = new FieldDefinition(
            "label", FieldType.MESSAGE, false, false, false, NUM_KEYVALUESTATE_LABEL);

    public static final FieldDefinition FIELD_KEYVALUESTATE_VALUE = new FieldDefinition(
            "value", FieldType.MESSAGE, false, false, false, NUM_KEYVALUESTATE_VALUE);
    */

    // FUTURE WORK: SingletonValueLeaf or SingletonValue?

    // message SingletonValueLeaf {
    //     Hash hash = 1;
    //     oneof value {
    //         EntityId entityId = 101;
    //         BlockInfo blockInfo = 102;
    //         MidnightRates midnightRates = 103;
    //         RunningHashes runningHashes = 104;
    //         ThrottleUsageSnapshots throttleUsageSnapshots = 105;
    //         CongestionLevelStarts congestionLevelStarts = 106;
    //         StakingNetworkRewards stakingNetworkRewards = 107;
    //         UpgradeFileHash upgradeFileHash = 108;
    //         FreezeTime freezeTime = 108;
    //     }
    // }

    public static final int NUM_SINGLETONVALUELEAF_ENTITYID = 101;
    public static final int NUM_SINGLETONVALUELEAF_BLOCKINFO = 102;
    public static final int NUM_SINGLETONVALUELEAF_MIDNIGHTRATES = 103;
    public static final int NUM_SINGLETONVALUELEAF_RUNNINGHASHES = 104;
    public static final int NUM_SINGLETONVALUELEAF_THROTTLEUSAGESNAPSHOTS = 105;
    public static final int NUM_SINGLETONVALUELEAF_CONGESTIONLEVELSTARTS = 106;
    public static final int NUM_SINGLETONVALUELEAF_STAKINGNETWORKREWARDS = 107;
    public static final int NUM_SINGLETONVALUELEAF_UPGRADEFILEHASH = 108;
    public static final int NUM_SINGLETONVALUELEAF_FREEZETIME = 109;

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_ENTITYID = new FieldDefinition(
            "entityId", FieldType.MESSAGE, false, false, true, NUM_SINGLETONVALUELEAF_ENTITYID);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_BLOCKINFO = new FieldDefinition(
            "blockInfo", FieldType.MESSAGE, false, false, true, NUM_SINGLETONVALUELEAF_BLOCKINFO);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_MIDNIGHTRATES = new FieldDefinition(
            "midnightRates", FieldType.MESSAGE, false, false, true, NUM_SINGLETONVALUELEAF_MIDNIGHTRATES);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_RUNNINGHASHES = new FieldDefinition(
            "runningHashes", FieldType.MESSAGE, false, false, true, NUM_SINGLETONVALUELEAF_RUNNINGHASHES);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_THROTTLEUSAGESNAPSHOTS = new FieldDefinition(
            "throttleUsageSnapshots", FieldType.MESSAGE, false, false, true,
            NUM_SINGLETONVALUELEAF_THROTTLEUSAGESNAPSHOTS);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_CONGESTIONLEVELSTARTS = new FieldDefinition(
            "congestionLevelStarts", FieldType.MESSAGE, false, false, true,
            NUM_SINGLETONVALUELEAF_CONGESTIONLEVELSTARTS);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_STAKINGNETWORKREWARDS = new FieldDefinition(
            "stakingNetworkRewards", FieldType.MESSAGE, false, false, true,
            NUM_SINGLETONVALUELEAF_STAKINGNETWORKREWARDS);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_UPGRADEFILEHASH = new FieldDefinition(
            "upgradeFileHash", FieldType.MESSAGE, false, false, true, NUM_SINGLETONVALUELEAF_UPGRADEFILEHASH);

    public static final FieldDefinition FIELD_SINGLETONVALUELEAF_FREEZETIME = new FieldDefinition(
            "freezeTime", FieldType.MESSAGE, false, false, true, NUM_SINGLETONVALUELEAF_FREEZETIME);

    // FUTURE WORK: QueueValueLeaf or QueueValue?

    // message QueueValueLeaf {
    //     Hash hash = 1;
    //     oneof value {
    //         Queue txtRecord = 201;
    //         Queue upgradeData = 202;
    //     }
    // }

    public static final int NUM_QUEUEVALUELEAF_TXNRECORD = 201;
    public static final int NUM_QUEUEVALUELEAF_UPGRADEDATA = 202;

    public static final FieldDefinition FIELD_QUEUEVALUELEAF_TXNRECORD = new FieldDefinition(
            "txnRecord", FieldType.MESSAGE, false, false, true, NUM_QUEUEVALUELEAF_TXNRECORD);

    public static final FieldDefinition FIELD_QUEUEVALUELEAF_UPGRADEDATA = new FieldDefinition(
            "upgradeData", FieldType.MESSAGE, false, false, true, NUM_QUEUEVALUELEAF_UPGRADEDATA);

    // https://github.com/hashgraph/hedera-services/issues/13781
    /*
    // FUTURE WORK: KeyValueValueLeaf or KeyValueValue?

    // message KeyValueValueLeaf {
    //     Hash hash = 1;
    //     oneof {
    //         VirtualMap tokens = 301;
    //         VirtualMap accounts = 302;
    //         VirtualMap aliases = 303;
    //         VirtualMap nfts = 304;
    //         VirtualMap tokenRels = 305;
    //         VirtualMap stakingInfo = 306;
    //         VirtualMap schedulesById = 307;
    //         VirtualMap schedulesByExpirySec = 308;
    //         VirtualMap schedulesByEquality = 309;
    //         VirtualMap blobs = 310;
    //         VirtualMap evmStorage = 311;
    //         VirtualMap evmBytecode = 312;
    //         VirtualMap topics = 313;
    //     }
    // }

    public static final int NUM_KEYVALUEVALUELEAF_TOKENS = 301;
    public static final int NUM_KEYVALUEVALUELEAF_ACCOUNTS = 302;
    public static final int NUM_KEYVALUEVALUELEAF_ALIASES = 303;
    public static final int NUM_KEYVALUEVALUELEAF_NFTS = 304;
    public static final int NUM_KEYVALUEVALUELEAF_TOKENRELS = 305;
    public static final int NUM_KEYVALUEVALUELEAF_STAKINGINFO = 306;
    public static final int NUM_KEYVALUEVALUELEAF_SCHEDULESBYID = 307;
    public static final int NUM_KEYVALUEVALUELEAF_SCHEDULESBYEXPIRYSEC = 308;
    public static final int NUM_KEYVALUEVALUELEAF_SCHEDULESBYEQUALITY = 309;
    public static final int NUM_KEYVALUEVALUELEAF_BLOBS = 310;
    public static final int NUM_KEYVALUEVALUELEAF_EVMSTORAGE = 311;
    public static final int NUM_KEYVALUEVALUELEAF_EVNBYTECODE = 312;
    public static final int NUM_KEYVALUEVALUELEAF_TOPICS = 313;

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_TOKENS = new FieldDefinition(
            "tokens", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_TOKENS);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_ACCOUNTS = new FieldDefinition(
            "accounts", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_ACCOUNTS);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_ALIASES = new FieldDefinition(
            "aliases", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_ALIASES);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_NFTS = new FieldDefinition(
            "nfts", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_NFTS);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_TOKENRELS = new FieldDefinition(
            "tokenRels", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_TOKENRELS);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_STAKINGINFO = new FieldDefinition(
            "stakingInfo", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_STAKINGINFO);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_SCHEDULESBYID = new FieldDefinition(
            "schedulesById", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_SCHEDULESBYID);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_SCHEDULESBYEXPIRYSEC = new FieldDefinition(
            "schedulesByExpirySec", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_SCHEDULESBYEXPIRYSEC);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_SCHEDULESBYEQUALITY = new FieldDefinition(
            "schedulesByEquality", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_SCHEDULESBYEQUALITY);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_BLOBS = new FieldDefinition(
            "blobs", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_BLOBS);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_EVMSTORAGE = new FieldDefinition(
            "evmStorage", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_EVMSTORAGE);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_EVMBYTECODE = new FieldDefinition(
            "evmBytecode", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_EVNBYTECODE);

    public static final FieldDefinition FIELD_KEYVALUEVALUELEAF_TOPICS = new FieldDefinition(
            "topics", FieldType.MESSAGE, false, false, true, NUM_KEYVALUEVALUELEAF_TOPICS);
    */







    private MerkleNodeProtoFields() {}

}
