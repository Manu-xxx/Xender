// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract AssociateTryCatch is HederaTokenService {

    event CatchEvent();
    event SuccessEvent();

    address tokenAddress;
    CalledContract public externalContract;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
        externalContract = new CalledContract();
    }

    function associateToken() external {
        int response = HederaTokenService.associateToken(msg.sender, tokenAddress);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token associate failed");
        }

        try externalContract.associate(msg.sender, tokenAddress) {
            emit SuccessEvent();
        } catch Error(string memory /*reason*/) {
            emit CatchEvent();
        }
    }

}

contract CalledContract is HederaTokenService {
    function associate(address account, address tokenAddress) external {
        int response2 = HederaTokenService.associateToken(account, tokenAddress);

        if (response2 != HederaResponseCodes.SUCCESS) {
            revert ("Token associate failed");
        }
    }
}