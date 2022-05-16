// contracts/TokenB.sol
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./token/ERC20/ERC20.sol";

contract TokenB is ERC20 {
    constructor(uint256 initialSupply) ERC20("TokenB", "BBB") {
        _mint(msg.sender, initialSupply);
    }
}
