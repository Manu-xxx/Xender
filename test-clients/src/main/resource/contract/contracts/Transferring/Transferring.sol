pragma solidity ^0.8.7;

contract TransferringContract {

    constructor() public payable {}

    fallback() external payable {}

    function transferToAddress(address payable _address, uint256 _amount) public payable {
        _address.transfer(_amount);
    }

    function transferToCaller(uint256 _amount) public payable {
        address payable recipient = payable(msg.sender);
        recipient.transfer(_amount);
    }

    function transferToAddressNegativeAmount(address payable _address, uint256 _amount) public payable {
        _address.transfer(_amount & (~_amount + 1));
    }

    function transferToAddressMultipleTimes(address payable _address, uint256 _amount) public payable {
        _address.transfer(_amount);
        _address.transfer(_amount/2);
        _address.transfer(_amount/4);
        _address.transfer(_amount/8);
        _address.transfer(_amount/16);
        _address.transfer(_amount/32);
        _address.transfer(_amount/64);
    }

    function transferToDifferentAddresses(address payable receiver1, address payable receiver2,
        address payable receiver3, uint256 _amount) public payable {
        receiver1.transfer(_amount);
        receiver2.transfer(_amount/2);
        receiver3.transfer(_amount/4);
    }
}