// SPDX-License-Identifier: Apache-2.0

import "./SelfFunding.sol";

contract ExchangeRatePrecompile is SelfFunding {
    // The USD in cents that must be sent as msg.value
    uint256 toll;

    constructor(uint256 _toll) {
        toll = _toll;
    }

    function gatedAccess() external payable costsCents(toll) {
        // Hope it was worth it!
    }

    function approxUsdValue() external payable returns (uint256 tinycents) {
        tinycents = toTinycents(msg.value);
    }
}
