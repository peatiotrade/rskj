package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.BridgeMethods;
import co.rsk.peg.BridgeStorageProvider;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Ignore
public class hasBtcBlockCoinbaseTransactionInformation extends BridgePerformanceTestCase {

    private BtcTransaction coinbaseTx;
    private co.rsk.bitcoinj.core.BtcBlock registerHeader;
    private PartialMerkleTree pmtWithoutWitness;
    private Sha256Hash witnessRoot;
    private Sha256Hash witnessReservedValue;

    @BeforeClass
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    public void hasBtcBlockCoinbaseTransactionInformation() {
        ExecutionStats stats = new ExecutionStats("hasBtcBlockCoinbaseTransactionInformation");
        hasBtcBlockCoinbaseTransactionInformation_success(10, stats);  //Set Times PAMELA
        BridgePerformanceTest.addStats(stats);
    }

    private void hasBtcBlockCoinbaseTransactionInformation_success(int times, ExecutionStats stats) {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
                1000,
                2000
        );

        executeAndAverage("hasBtcBlockCoinbaseTransactionInformation-sucess", times, getABIEncoder(), storageInitializer, Helper.getZeroValueValueTxBuilderFromFedMember(), Helper.getRandomHeightProvider(10), stats);
    }

    private ABIEncoder getABIEncoder() {

        return (int executionIndex) ->
                BridgeMethods.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.getFunction().encode(new Object[]{
                   //     coinbaseTx.bitcoinSerialize(),
                        registerHeader.getHash().toBigInteger(),
                   /*     pmtWithoutWitness.bitcoinSerialize(),
                        witnessRoot.toBigInteger(),
                        witnessReservedValue.toBigInteger()*/
                });
    }

    private BridgeStorageProviderInitializer generateInitializerForTest(int minBtcBlocks, int maxBtcBlocks) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, blockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            BtcBlock lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);

            coinbaseTx = new BtcTransaction(bridgeConstants.getBtcParams());

/*
            1-byte - OP_RETURN (0x6a)
            1-byte - Push the following 36 bytes (0x24)
            4-byte - Commitment header (0xaa21a9ed)
            32-byte - Commitment hash: Double-SHA256(witness root hash|witness reserved value
            https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki#Commitment_structure
*/

            witnessRoot = Sha256Hash.ZERO_HASH;
            witnessReservedValue = Sha256Hash.ZERO_HASH;
            byte[] witnessCommitment = Sha256Hash.twiceOf(witnessRoot.getBytes(), witnessReservedValue.getBytes()).getBytes();
            byte[] opcode = Hex.decode("6a24aa21a9ed");
            byte[] scriptData = new byte[opcode.length + witnessCommitment.length];
            System.arraycopy(opcode, 0, scriptData, 0, opcode.length);
            System.arraycopy(witnessCommitment, 0, scriptData, opcode.length, witnessCommitment.length);

            coinbaseTx.addInput(new TransactionInput(networkParameters, null, new byte[2]));
            coinbaseTx.addOutput(Coin.COIN.multiply(10), Address.fromBase58(networkParameters, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
            coinbaseTx.addOutput(Coin.ZERO, new Script(scriptData));

            byte[] bits = new byte[1];
            bits[0] = 0x3f;

            List<Sha256Hash> hashes = new ArrayList<>();
            hashes.add(coinbaseTx.getHash());
            pmtWithoutWitness = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
            List<Sha256Hash> hashlist = new ArrayList<>();
            Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

            registerHeader = Helper.generateBtcBlock(lastBlock, Collections.singletonList(coinbaseTx), blockMerkleRoot);
            btcBlockChain.add(registerHeader);
        };
    }


}