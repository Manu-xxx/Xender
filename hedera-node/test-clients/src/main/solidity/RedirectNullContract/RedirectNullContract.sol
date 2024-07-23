// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

import "../../../../build/hedera-smart-contracts/contracts/system-contracts/hedera-token-service/HederaTokenService.sol";

contract RedirectNullContract is HederaTokenService {

    function sendNullSelector(address token) public returns (bytes memory result) {
        (bool success, bytes memory data) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.redirectForToken.selector, token, "")
        );
        require(success, "call failed");
        result = data;
    }
}