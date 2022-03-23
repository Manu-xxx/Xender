// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./IHederaTokenService.sol";
import "./KeyHelper.sol";

contract FeeHelper is KeyHelper {

    function createNAmountFixedFeesForHbars(uint8 numberOfFees, uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        fixedFees = new IHederaTokenService.FixedFee[](numberOfFees);

        for(uint8 i = 0; i < numberOfFees; i++) {
            IHederaTokenService.FixedFee memory fixedFee = createFixedFeeForHbars(amount, feeCollector);
            fixedFees[i] = fixedFee;
        }
    }

    function createFixedFeesForToken(uint32 amount, address tokenId, address feeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        fixedFees = new IHederaTokenService.FixedFee[](1);
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeForToken(amount, tokenId, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesForToken(uint32 amount, address tokenId, address firstFeeCollector, address secondFeeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        fixedFees = new IHederaTokenService.FixedFee[](1);
        IHederaTokenService.FixedFee memory fixedFee1 = createFixedFeeForToken(amount, tokenId, firstFeeCollector);
        IHederaTokenService.FixedFee memory fixedFee2 = createFixedFeeForToken(2*amount, tokenId, secondFeeCollector);
        fixedFees[0] = fixedFee1;
        fixedFees[0] = fixedFee2;
    }

    function createFixedFeesForHbars(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        fixedFees = new IHederaTokenService.FixedFee[](1);
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeForHbars(amount, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesForCurrentToken(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        fixedFees = new IHederaTokenService.FixedFee[](1);
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeForCurrentToken(amount, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesWithInvalidFlags(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        fixedFees = new IHederaTokenService.FixedFee[](1);
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeWithInvalidFlags(amount, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesWithTokenIdAndHbars(uint32 amount, address tokenId, address feeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        fixedFees = new IHederaTokenService.FixedFee[](1);
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeWithTokenIdAndHbars(amount, tokenId, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesWithAllTypes(uint32 amount, address tokenId, address feeCollector) internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        IHederaTokenService.FixedFee memory fixedFeeForToken = createFixedFeeForToken(amount, tokenId, feeCollector);
        IHederaTokenService.FixedFee memory fixedFeeForHbars = createFixedFeeForHbars(amount*2, feeCollector);
        IHederaTokenService.FixedFee memory fixedFeeForCurrentToken = createFixedFeeForCurrentToken(amount*4, feeCollector);
        fixedFees[1] = fixedFeeForToken;
        fixedFees[1] = fixedFeeForHbars;
        fixedFees[2] = fixedFeeForCurrentToken;
    }

    function createFixedFeeForToken(uint32 amount, address tokenId, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.tokenId = tokenId;
        fixedFee.feeCollector = feeCollector;
    }

    function createFixedFeeForHbars(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.useHbarsForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    function createFixedFeeForCurrentToken(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.useCurrentTokenForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    //Used for negative scenarios
    function createFixedFeeWithInvalidFlags(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.useHbarsForPayment = true;
        fixedFee.useCurrentTokenForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    //Used for negative scenarios
    function createFixedFeeWithTokenIdAndHbars(uint32 amount, address tokenId, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.tokenId = tokenId;
        fixedFee.useHbarsForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    function getEmptyFixedFees() internal pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {}

    function createNAmountFractionalFees(uint8 numberOfFees, uint32 numerator, uint32 denominator,
        bool netOfTransfers,  address feeCollector) internal pure returns (IHederaTokenService.FractionalFee[] memory fractionalFees) {
        fractionalFees = new IHederaTokenService.FractionalFee[](numberOfFees);

        for(uint8 i = 0; i < numberOfFees; i++) {
            IHederaTokenService.FractionalFee memory fractionalFee = createFractionalFee(numerator, denominator, netOfTransfers, feeCollector);
            fractionalFees[i] = fractionalFee;
        }
    }

    function createFractionalFees(uint32 numerator, uint32 denominator,
        bool netOfTransfers,  address feeCollector) internal pure returns (IHederaTokenService.FractionalFee[] memory fractionalFees) {
        fractionalFees = new IHederaTokenService.FractionalFee[](1);
        IHederaTokenService.FractionalFee memory fractionalFee = createFractionalFee(numerator, denominator, netOfTransfers, feeCollector);
        fractionalFees[0] = fractionalFee;
    }

    function createFractionalFeesWithLimits(uint32 numerator, uint32 denominator, uint32 minimumAmount, uint32 maximumAmount,
        bool netOfTransfers,  address feeCollector) internal pure returns (IHederaTokenService.FractionalFee[] memory fractionalFees) {
        fractionalFees = new IHederaTokenService.FractionalFee[](1);
        IHederaTokenService.FractionalFee memory fractionalFee = createFractionalFeeWithLimits(numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector);
        fractionalFees[0] = fractionalFee;
    }

    function createFractionalFee(uint32 numerator, uint32 denominator,
        bool netOfTransfers,  address feeCollector) internal pure returns (IHederaTokenService.FractionalFee memory fractionalFee) {
        fractionalFee.numerator = numerator;
        fractionalFee.denominator = denominator;
        fractionalFee.netOfTransfers = netOfTransfers;
        fractionalFee.feeCollector = feeCollector;
    }

    function createFractionalFeeWithLimits(uint32 numerator, uint32 denominator, uint32 minimumAmount, uint32 maximumAmount,
        bool netOfTransfers,  address feeCollector) internal pure returns (IHederaTokenService.FractionalFee memory fractionalFee) {
        fractionalFee.numerator = numerator;
        fractionalFee.denominator = denominator;
        fractionalFee.minimumAmount = minimumAmount;
        fractionalFee.maximumAmount = maximumAmount;
        fractionalFee.netOfTransfers = netOfTransfers;
        fractionalFee.feeCollector = feeCollector;
    }

    function getEmptyFractionalFees() internal pure returns (IHederaTokenService.FractionalFee[] memory fractionalFees) {
        fractionalFees = new IHederaTokenService.FractionalFee[](0);
    }

    function createRoyaltylFee(uint32 numerator, uint32 denominator, IHederaTokenService.FixedFee memory fixedFee,
        address feeCollector) internal pure returns (IHederaTokenService.RoyaltyFee memory royaltyFee) {
        royaltyFee.numerator = numerator;
        royaltyFee.denominator = denominator;
        royaltyFee.fixedFee = fixedFee;
        royaltyFee.feeCollector = feeCollector;
    }
}