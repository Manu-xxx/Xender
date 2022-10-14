package com.hedera.services.state.virtual.entities;

import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.utils.SeededPropertySource;
import org.junit.jupiter.api.Test;

import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.*;

class OnDiskAccountCompatibilityTest {
    private final OnDiskAccount subject = new OnDiskAccount();

    @Test
    void tokenTreasuryIsKnowable() {
        assertFalse(subject.isTokenTreasury());
        subject.setNumTreasuryTitles(1);
        assertTrue(subject.isTokenTreasury());
    }

    @Test
    void notContractByDefault() {
        assertFalse(subject.isSmartContract());
        subject.setIsContract(true);
        assertTrue(subject.isSmartContract());
    }

    @Test
    void canReportHeadNftInfoFromHederaInterface() {
        final var nftId = 1234L;
        final var serialNo = 5678L;
        subject.setHeadNftId(nftId);
        subject.setHeadNftSerialNum(serialNo);
        final var expected = EntityNumPair.fromLongs(nftId, serialNo);
        assertEquals(expected, subject.getHeadNftKey());
    }

    @Test
    void canReportLatestAssociationFromHederaInterface() {
        final var num = 666L;
        final var tokenId = 1234L;
        subject.setHeadTokenId(tokenId);
        subject.setAccountNumber(num);
        final var expected = EntityNumPair.fromLongs(num, tokenId);
        assertEquals(expected, subject.getLatestAssociation());
    }

    @Test
    void cannotSetNegativeBalance() {
        assertThrows(NegativeAccountBalanceException.class, () -> subject.setBalance(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.setBalanceUnchecked(-1L));
    }

    @Test
    void canSetPositiveBalance() throws NegativeAccountBalanceException {
        subject.setBalance(+1L);
        assertEquals(1L, subject.getBalance());
        subject.setBalanceUnchecked(+2L);
        assertEquals(2L, subject.getBalance());
    }

    @Test
    void understandsEntityNumCode() {
        final var num = Integer.MAX_VALUE + 1L;
        subject.setAccountNumber(num);
        final var expected = BitPackUtils.codeFromNum(num);
        assertEquals(expected, subject.number());
    }

    @Test
    void proxyAlwaysMissing() {
        assertEquals(EntityId.MISSING_ENTITY_ID, subject.getProxy());
        subject.setProxy(new EntityId(0L, 0L, 666L));
        assertEquals(EntityId.MISSING_ENTITY_ID, subject.getProxy());
    }

    @Test
    void canGetMaxAutoAssociationsViaHederaInterface() {
        subject.setMaxAutoAssociations(123);
        assertEquals(123, subject.getMaxAutomaticAssociations());
    }

    @Test
    void canSetUsedAutoAssociationsViaHederaInterface() {
        subject.setUsedAutomaticAssociations(123);
        assertEquals(123, subject.getMaxAutomaticAssociations());
    }

    @Test
    void firstStorageKeyIsNullTilSet() {
        final var pretend = new int[] { 1, 2, 3, 4, 5, 6, 7, 8};
        final var alsoPretend = new int[] { 6, 6, 6, 6, 6, 6, 6, 6};
        assertNull(subject.getFirstContractStorageKey());
        subject.setFirstStorageKey(pretend);
        assertArrayEquals(pretend, subject.getFirstContractStorageKey().getKey());
        assertArrayEquals(pretend, subject.getFirstUint256Key());
        subject.setFirstUint256StorageKey(alsoPretend);
        assertArrayEquals(alsoPretend, subject.getFirstUint256Key());
    }

    @Test
    void approvalsManageableViaHederaInterface() {
        final var firstSubject = SeededPropertySource
                .forSerdeTest(1, 1)
                .nextOnDiskAccount();
        final var secondSubject = SeededPropertySource
                .forSerdeTest(1, 3)
                .nextOnDiskAccount();

        assertSame(firstSubject.getHbarAllowances(), firstSubject.getCryptoAllowances());
        assertSame(firstSubject.getHbarAllowances(), firstSubject.getCryptoAllowancesUnsafe());
        assertSame(firstSubject.getFungibleAllowances(), firstSubject.getFungibleTokenAllowances());
        assertSame(firstSubject.getFungibleAllowances(), firstSubject.getFungibleTokenAllowancesUnsafe());
        assertSame(firstSubject.getNftOperatorApprovals(), firstSubject.getApproveForAllNfts());
        assertSame(firstSubject.getNftOperatorApprovals(), firstSubject.getApproveForAllNftsUnsafe());

        firstSubject.setCryptoAllowances((SortedMap<EntityNum, Long>) secondSubject.getHbarAllowances());
        firstSubject.setCryptoAllowancesUnsafe(secondSubject.getHbarAllowances());
        assertSame(secondSubject.getHbarAllowances(), firstSubject.getCryptoAllowances());

        firstSubject.setFungibleTokenAllowances((SortedMap<FcTokenAllowanceId, Long>) secondSubject.getFungibleAllowances());
        firstSubject.setFungibleTokenAllowancesUnsafe(secondSubject.getFungibleAllowances());
        assertSame(secondSubject.getFungibleAllowances(), firstSubject.getFungibleAllowances());

        firstSubject.setApproveForAllNfts(secondSubject.getApproveForAllNfts());
        assertSame(secondSubject.getNftOperatorApprovals(), firstSubject.getNftOperatorApprovals());
    }

    @Test
    void stakingMetaAvailableFromHederaInterface() {

    }
}