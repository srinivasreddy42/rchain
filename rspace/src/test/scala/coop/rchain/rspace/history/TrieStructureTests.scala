package coop.rchain.rspace.history

import java.nio.ByteBuffer

import org.lmdbjava.Txn
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

class TrieStructureTests
    extends HistoryTestsBase[Txn[ByteBuffer], TestKey, ByteVector]
    with WithLMDBStore {

  implicit val codecByteVector: Codec[ByteVector] = variableSizeBytesLong(int64, bytes)

  import TestData._

  def withTrie[R](f: Trie[TestKey, ByteVector] => R): R =
    withTestTrieStore { store =>
      store.withTxn(store.createTxnRead()) { txn =>
        val trieOpt = store.get(txn, store.workingRootHash.get)
        trieOpt should not be empty
        f(trieOpt.get)
      }
    }

  def withTrieTxnAndStore[R](
      f: (ITrieStore[Txn[ByteBuffer], TestKey, ByteVector],
          Txn[ByteBuffer],
          Trie[TestKey, ByteVector]) => R): R =
    withTestTrieStore { store =>
      store.withTxn(store.createTxnRead()) { txn =>
        val trieOpt = store.get(txn, store.workingRootHash.get)
        trieOpt should not be empty
        f(store, txn, trieOpt.get)
      }
    }

  "insert's effect" should "be visible in the outer read transaction" ignore {
    withTrieTxnAndStore { (store, txn, trie) =>
      insert(store, key1, val1)
      // Insert was made in a nested transaction, so it's effect should be visible
      store.get(txn, store.workingRootHash.get) should not be None
    }
  }

  behavior of "A trie"
  it should "be created as an empty pointer block" in
    withTrie {
      case Node(PointerBlock(vector)) =>
        vector should have size 256
        vector should contain only None
      case _ => fail("expected a node")
    }

  it should "have two levels after inserting one element" in {
    withTestTrieStore { implicit store =>
      insert(store, key1, val1)

      store.withTxn(store.createTxnRead()) { implicit txn =>
        val rootHex = "0x538b82b41d4360492c17a112864ffb989571504b73b1281677a692e9fd2ee4cc"
        val leafHex = "0x8d329ed700f130f40b15b73b1bd4f7b70d982acb9dce55e58f58425038f5db1c"

        expectNode(rootHex, Seq((1, leafHex)))

        val expectedLeafHash = Blake2b256Hash
          .fromHex(leafHex)
          .get

        store.get(txn, expectedLeafHash) shouldBe Some(Leaf(key1, val1))
      }
    }
  }

  it should "have four levels after inserting second element with same hash prefix" in {
    withTestTrieStore { implicit store =>
      insert(store, key1, val1)
      insert(store, key2, val2)

      store.withTxn(store.createTxnRead()) { implicit txn =>
        val rootHex   = "0xae608338e532497ea0844d9efd6008251451538497d30d3d4dbfb9a032fc8feb"
        val level1Hex = "0xc67f3d92de6f9e75b9561274fbcc4c8efe8d1161554e3b0f66307a111ddd11ce"
        val level2Hex = "0xdb61c95e4ea234de4fe154861f86d090af2029da3cfca83cf210c71c539f1942"
        val level3Hex = "0x681aff745729ccec1d5e3f23f5b56796416a6604a8a07b646fc16f1b0e8c70c8"
        val leaf1Hex  = "0x8d329ed700f130f40b15b73b1bd4f7b70d982acb9dce55e58f58425038f5db1c"
        val leaf2Hex  = "0xf22c71982cf8663fb1ea77a444233c99d8c00cd187b0253cfc4213228fea6625"

        expectNode(rootHex, Seq((1, level1Hex)))
        expectNode(level1Hex, Seq((0, level2Hex)))
        expectNode(level2Hex, Seq((0, level3Hex)))
        expectNode(level3Hex, Seq((0, leaf1Hex), (1, leaf2Hex)))

        val expectedLeaf1Hash = Blake2b256Hash
          .fromHex(leaf1Hex)
          .get
        val expectedLeaf2Hash = Blake2b256Hash
          .fromHex(leaf2Hex)
          .get

        store.get(txn, expectedLeaf1Hash) shouldBe Some(Leaf(key1, val1))
        store.get(txn, expectedLeaf2Hash) shouldBe Some(Leaf(key2, val2))
      }

    }
  }

  it should "retain previous structure after delete" in {
    withTestTrieStore { implicit store =>
      insert(store, key1, val1)
      insert(store, key2, val2)

      delete(store, key2, val2)

      store.withTxn(store.createTxnRead()) { implicit txn =>
        val rootHex = "0x538b82b41d4360492c17a112864ffb989571504b73b1281677a692e9fd2ee4cc"
        val leafHex = "0x8d329ed700f130f40b15b73b1bd4f7b70d982acb9dce55e58f58425038f5db1c"

        expectNode(rootHex, Seq((1, leafHex)))

        val expectedLeafHash = Blake2b256Hash
          .fromHex(leafHex)
          .get

        store.get(txn, expectedLeafHash) shouldBe Some(Leaf(key1, val1))
      }

      store.withTxn(store.createTxnRead()) { implicit txn =>
        val rootHex   = "0xae608338e532497ea0844d9efd6008251451538497d30d3d4dbfb9a032fc8feb"
        val level1Hex = "0xc67f3d92de6f9e75b9561274fbcc4c8efe8d1161554e3b0f66307a111ddd11ce"
        val level2Hex = "0xdb61c95e4ea234de4fe154861f86d090af2029da3cfca83cf210c71c539f1942"
        val level3Hex = "0x681aff745729ccec1d5e3f23f5b56796416a6604a8a07b646fc16f1b0e8c70c8"
        val leaf1Hex  = "0x8d329ed700f130f40b15b73b1bd4f7b70d982acb9dce55e58f58425038f5db1c"
        val leaf2Hex  = "0xf22c71982cf8663fb1ea77a444233c99d8c00cd187b0253cfc4213228fea6625"

        expectNode(rootHex, Seq((1, level1Hex)))
        expectNode(level1Hex, Seq((0, level2Hex)))
        expectNode(level2Hex, Seq((0, level3Hex)))
        expectNode(level3Hex, Seq((0, leaf1Hex), (1, leaf2Hex)))

        val expectedLeaf1Hash = Blake2b256Hash
          .fromHex(leaf1Hex)
          .get
        val expectedLeaf2Hash = Blake2b256Hash
          .fromHex(leaf2Hex)
          .get

        store.get(txn, expectedLeaf1Hash) shouldBe Some(Leaf(key1, val1))
        store.get(txn, expectedLeaf2Hash) shouldBe Some(Leaf(key2, val2))
      }

    }
  }

  private[this] def expectNode(currentHex: String, childHexes: Seq[(Int, String)])(
      implicit txn: Txn[ByteBuffer],
      store: ITrieStore[Txn[ByteBuffer], TestKey, ByteVector]) =
    store.get(txn,
              Blake2b256Hash
                .fromHex(currentHex)
                .get) match {
      case Some(Node(PointerBlock(vector))) =>
        vector should have size 256
        val expectedHashes = childHexes.map {
          case (expectedHexPosition, expectedHexString) =>
            val expectedHash = Blake2b256Hash
              .fromHex(expectedHexString)
              .get
            val maybeExpectedHash = Some(expectedHash)
            vector(expectedHexPosition) shouldBe maybeExpectedHash
            maybeExpectedHash
        }

        vector.filterNot(expectedHashes.contains) should contain only None

      case _ => fail("expected a node")
    }

}
