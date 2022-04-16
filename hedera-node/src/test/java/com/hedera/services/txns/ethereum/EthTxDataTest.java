package com.hedera.services.txns.ethereum;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


class EthTxDataTest {

	static final String SIGNATURE_ADDRESS = "a94f5374fce5edbc8e2a8697c15331677e6ebf0b";
	static final String SIGNATURE_PUBKEY = "033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d";
	static final String RAW_TX_TYPE_0 =
			"f864012f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc18180827653820277a0f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2fa00c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792";
	static final String RAW_TX_TYPE_2 =
			"02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";
	static final String RAW_TX_TYPE_2_ACCESS_LIST =
			"02f8a10101648227108402625a0094000000000000000000000000000000000000c0de80a4693c61390000000000000000000000000000000000000000000000000000000000000000d7d694000000000000000000000000000000000000ba5ec080a0d5a3052a8cc387f35ffdebe832afdfdd45980cdcbb9b926dee7fb9b1d9a4ee08a06210d404eb6aecd05bc214b120959a71ede9fadcae4b769c96c58bffa216f205";

	static final String EIP_155_DEMO_ADDRESS = "9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f";
	static final String EIP_155_DEMO_PUBKEY = "024bc2a31265153f07e70e0bab08724e6b85e217f8cd628ceb62974247bb493382";
	static final String EIP155_DEMO =
			"f86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83";

	@Test
	void extractFrontierSignature() {
		try {
			var frontierTx = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_0));
			assertNotNull(frontierTx);
			assertEquals(RAW_TX_TYPE_0, Hex.toHexString(frontierTx.rawTx()));
			assertEquals(EthTxData.EthTransactionType.LEGACY_ETHEREUM, frontierTx.type());
			assertEquals("012a", Hex.toHexString(frontierTx.chainId()));
			assertEquals(1, frontierTx.nonce());
			assertEquals("2f", Hex.toHexString(frontierTx.gasPrice()));
			assertNull(frontierTx.maxPriorityGas());
			assertNull(frontierTx.maxGas());
			assertEquals(98_304L, frontierTx.gasLimit());
			assertEquals("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181", Hex.toHexString(frontierTx.to()));
			assertEquals(BigInteger.ZERO, frontierTx.value());
			assertEquals("7653", Hex.toHexString(frontierTx.callData()));
			assertNull(frontierTx.accessList());
			assertEquals(0, frontierTx.recId());
			assertEquals("0277", Hex.toHexString(frontierTx.v()));
			assertEquals("f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2f",
					Hex.toHexString(frontierTx.r()));
			assertEquals("0c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792",
					Hex.toHexString(frontierTx.s()));

			var frontierSigs = EthTxSigs.extractSignatures(frontierTx);
			assertNotNull(frontierSigs);

			assertEquals(SIGNATURE_ADDRESS, Hex.toHexString(frontierSigs.address()));
			assertEquals(SIGNATURE_PUBKEY, Hex.toHexString(frontierSigs.publicKey()));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	void extractEIP155Signature() {
		var eip155Tx = EthTxData.populateEthTxData(Hex.decode(EIP155_DEMO));
		assertNotNull(eip155Tx);
		assertEquals(EIP155_DEMO, Hex.toHexString(eip155Tx.rawTx()));
		assertEquals(EthTxData.EthTransactionType.LEGACY_ETHEREUM, eip155Tx.type());
		assertEquals("01", Hex.toHexString(eip155Tx.chainId()));
		assertEquals(9, eip155Tx.nonce());
		assertEquals("04a817c800", Hex.toHexString(eip155Tx.gasPrice()));
		assertNull(eip155Tx.maxPriorityGas());
		assertNull(eip155Tx.maxGas());
		assertEquals(21_000L, eip155Tx.gasLimit());
		assertEquals("3535353535353535353535353535353535353535", Hex.toHexString(eip155Tx.to()));
		assertEquals(new BigInteger("0de0b6b3a7640000", 16), eip155Tx.value());
		assertEquals(0, eip155Tx.callData().length);
		assertNull(eip155Tx.accessList());
		assertEquals(0, eip155Tx.recId());
		assertEquals("25", Hex.toHexString(eip155Tx.v()));
		assertEquals("28ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276",
				Hex.toHexString(eip155Tx.r()));
		assertEquals("67cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83",
				Hex.toHexString(eip155Tx.s()));

		var eip155Sigs = EthTxSigs.extractSignatures(eip155Tx);
		assertNotNull(eip155Sigs);
		assertEquals(EIP_155_DEMO_ADDRESS, Hex.toHexString(eip155Sigs.address()));
		assertEquals(EIP_155_DEMO_PUBKEY, Hex.toHexString(eip155Sigs.publicKey()));
	}

	@Test
	void extractLondonSignature() {
		var londonTx = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_2));
		assertNotNull(londonTx);
		assertEquals(RAW_TX_TYPE_2, Hex.toHexString(londonTx.rawTx()));
		assertEquals(EthTxData.EthTransactionType.EIP1559, londonTx.type());
		assertEquals("012a", Hex.toHexString(londonTx.chainId()));
		assertEquals(2, londonTx.nonce());
		assertNull(londonTx.gasPrice());
		assertEquals("2f", Hex.toHexString(londonTx.maxPriorityGas()));
		assertEquals("2f", Hex.toHexString(londonTx.maxGas()));
		assertEquals(98_304L, londonTx.gasLimit());
		assertEquals("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181", Hex.toHexString(londonTx.to()));
		assertEquals(new BigInteger("0de0b6b3a7640000", 16), londonTx.value());
		assertEquals("123456", Hex.toHexString(londonTx.callData()));
		assertEquals("", Hex.toHexString(londonTx.accessList()));
		assertEquals(1, londonTx.recId());
		assertNull(londonTx.v());
		assertEquals("df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479",
				Hex.toHexString(londonTx.r()));
		assertEquals("1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66",
				Hex.toHexString(londonTx.s()));

		var londonSigs = EthTxSigs.extractSignatures(londonTx);
		assertNotNull(londonSigs);
		assertEquals(SIGNATURE_ADDRESS, Hex.toHexString(londonSigs.address()));
		assertEquals(SIGNATURE_PUBKEY, Hex.toHexString(londonSigs.publicKey()));
	}

	@Test
	void roundTripFrontier() {
		var expected = Hex.decode(RAW_TX_TYPE_0);
		var frontierTx = EthTxData.populateEthTxData(expected);

		assertNotNull(frontierTx);
		assertArrayEquals(expected, frontierTx.encodeTx());
	}
	
	@Test
	void roundTrip155() {
		var expected = Hex.decode(EIP155_DEMO);
		var tx155 = EthTxData.populateEthTxData(expected);

		assertNotNull(tx155);
		assertArrayEquals(expected, tx155.encodeTx());
	}

	@Test
	void roundTrip1559() {
		var expected = Hex.decode(RAW_TX_TYPE_2);
		var tx1559 = EthTxData.populateEthTxData(expected);

		assertNotNull(tx1559);
		assertArrayEquals(expected, tx1559.encodeTx());
	}

	@Test
	void whiteBoxDecodingErrors() {
		var oneByte = new byte[] { 1 };
		var size_13 = List.of(
				oneByte, oneByte, oneByte, oneByte,
				oneByte, oneByte, oneByte, oneByte,
				oneByte, oneByte, oneByte, oneByte,
				oneByte);

		// legacy TX with too many RLP entries
		assertNull(EthTxData.populateEthTxData(RLPEncoder.encodeAsList(size_13)));
		// type 2 TX with too many RLP entries
		assertNull(EthTxData.populateEthTxData(RLPEncoder.encodeSequentially(new byte[] { 2 }, size_13)));
		// Unsupported Transaciton Type
		assertNull(EthTxData.populateEthTxData(RLPEncoder.encodeSequentially(new byte[] { 127 }, size_13)));

		// poorly wrapped typed transaction
		assertNull(EthTxData.populateEthTxData(RLPEncoder.encodeSequentially(new byte[] { 2 }, oneByte, oneByte)));
	}

	@Test
	void whiteBoxEncodingErrors() {
		var oneByte = new byte[] { 1 };

		EthTxData ethTxDataWithAccessList =
				new EthTxData(oneByte, EthTxData.EthTransactionType.EIP1559, oneByte, 1,
						oneByte, oneByte, oneByte, 1,
						oneByte, BigInteger.ONE, oneByte, oneByte, 1,
						oneByte, oneByte, oneByte);
		assertThrows(IllegalStateException.class, ethTxDataWithAccessList::encodeTx);

		//Type 1
		EthTxData ethTsDataEIP2930 =
				new EthTxData(oneByte, EthTxData.EthTransactionType.EIP2930, oneByte, 1,
						oneByte, oneByte, oneByte, 1,
						oneByte, BigInteger.ONE, oneByte, null, 1,
						oneByte, oneByte, oneByte);
		assertThrows(IllegalStateException.class, ethTsDataEIP2930::encodeTx);
	}

	@Test
	void roundTripTests() {
		EthTxData parsed = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_0));
		assertArrayEquals(Hex.decode(RAW_TX_TYPE_0), parsed.encodeTx());

		parsed = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_2));
		assertArrayEquals(Hex.decode(RAW_TX_TYPE_2), parsed.encodeTx());
	}

	@Test
	void replaceCallData() {
		var parsed = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_2));
		var noCallData = parsed.replaceCallData(new byte[0]);
		System.out.println(Hex.toHexString(noCallData.encodeTx()));
		System.out.println(RAW_TX_TYPE_2.replace("83123456", "80"));
		assertArrayEquals(Hex.decode(RAW_TX_TYPE_2
						.replace("f870", "f86d") // tx is shorter
						.replace("83123456", "80")), // calldata changed
				noCallData.encodeTx());
	}

	@Test
	void toStringHashAndEquals() {
		var parsed = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_2));
		var parsedAgain = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_2));
		var parsed0 = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_0));
		assertDoesNotThrow(() -> parsed.toString());
		assertDoesNotThrow(() -> parsed0.toString());
		assertDoesNotThrow(() -> parsed.hashCode());
		assertDoesNotThrow(() -> parsed0.hashCode());
		
		assertEquals(parsed, parsedAgain);
		assertNotEquals(parsed, parsed0);
	}
}
