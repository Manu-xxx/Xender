// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "../../../../build/hedera-smart-contracts/contracts/system-contracts/hedera-token-service/HederaTokenService.sol";
import "../../../../build/hedera-smart-contracts/contracts/system-contracts/HederaResponseCodes.sol";

contract HtsTransferFrom is HederaTokenService {

    function htsTransferFrom(address token, address from, address to, uint256 amount) public  {
        (int statusCode) = HederaTokenService.transferFrom(token, from, to, amount);
        if (statusCode != HederaResponseCodes.SUCCESS) {
            revert ("Transfer from failed!");
        }
    }

    function htsTransferFromNFT(address token, address from, address to, uint256 serialNumber) public {
        (int statusCode) = HederaTokenService.transferFromNFT(token, from, to, serialNumber);
        if (statusCode != HederaResponseCodes.SUCCESS) {
            revert ("Transfer from failed!");
        }
    }
}
