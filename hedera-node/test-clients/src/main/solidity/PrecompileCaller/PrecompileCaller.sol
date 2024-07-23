// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
import "../../../../build/hedera-smart-contracts/contracts/system-contracts/hedera-token-service/HederaTokenService.sol";
import "../../../../build/hedera-smart-contracts/contracts/system-contracts/HederaResponseCodes.sol";

contract PrecompileCaller is HederaTokenService {

    function callSha256AndIsToken(bytes memory toHash, address token) external returns(bool,bytes32) {
        bytes32 hashed = sha256(toHash);

        (int statusCode, bool isToken) = HederaTokenService.isToken(token);

        if (statusCode != HederaResponseCodes.SUCCESS) {
            revert ("Token check failed!");
        }
        return (isToken, hashed);
    }
}