// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
import "../../../../build/hedera-smart-contracts/contracts/system-contracts/hedera-token-service/HederaTokenService.sol";
import "../../../../build/hedera-smart-contracts/contracts/system-contracts/HederaResponseCodes.sol";

contract PauseUnpauseTokenAccount is HederaTokenService {

    function pauseTokenAccount(address token) public {
        int response = HederaTokenService.pauseToken(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token pause failed!");
        }
    }

    function unpauseTokenAccount(address token) public {
        int response = HederaTokenService.unpauseToken(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token unpause failed!");
        }
    }
}