package nl.tudelft.trustchain.detoks

import android.content.Context
import android.util.Log
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload
import java.util.*

const val LIKE_BLOCK: String = "like_block"

class DeToksCommunity(
    private val context: Context, settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {

    private val walletManager = WalletManager(context)
    private val visitedPeers  = mutableListOf<Peer>()

    init {
        messageHandlers[MESSAGE_TORRENT_ID] = ::onGossip
        messageHandlers[MESSAGE_TRANSACTION_ID] = ::onTransactionMessage
        registerBlockSigner(LIKE_BLOCK, object : BlockSigner {
            override fun onSignatureRequest(block: TrustChainBlock) {
                createAgreementBlock(block, block.transaction)
            }
        })
        addListener(LIKE_BLOCK, object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                Log.d("Detoks", "onBlockReceived: ${block.blockId} ${block.transaction}")
                val video = block.transaction.get("video")
                val torrent = block.transaction.get("torrent")
                Log.d("Detoks", "Received like for $video, $torrent")
            }
        })
    }

    companion object {
        const val MESSAGE_TORRENT_ID = 1
        const val MESSAGE_TRANSACTION_ID = 2

    }

    override val serviceId = "c86a7db45eb3563ae047639817baec4db2bc7c25"
    val discoveredAddressesContacted: MutableMap<IPv4Address, Date> = mutableMapOf()
    val lastTrackerResponses = mutableMapOf<IPv4Address, Date>()

    fun sendTokens(amount: Int, recipientMid: String) {
        val senderWallet = walletManager.getOrCreateWallet(myPeer.mid)

        Log.d("DetoksCommunity", "my wallet ${senderWallet.balance}")

        if (senderWallet.balance >= amount) {
            Log.d("DetoksCommunity", "Sending $amount money to $recipientMid")
            senderWallet.balance -= amount
            walletManager.setWalletBalance(myPeer.mid, senderWallet.balance)

            val recipientWallet = walletManager.getOrCreateWallet(recipientMid)
            recipientWallet.balance += amount
            walletManager.setWalletBalance(recipientMid, recipientWallet.balance)

            for (peer in getPeers()) {
                val packet = serializePacket(
                    MESSAGE_TRANSACTION_ID,
                    TransactionMessage(amount, myPeer.mid, recipientMid)
                )
                send(peer.address, packet)
            }
        } else {
            Log.d("DeToksCommunity", "Insufficient funds!")
        }

    }

    fun gossipWith(peer: Peer) {
        Log.d("DeToksCommunity", "Gossiping with ${peer.mid}, address: ${peer.address}")
        Log.d("DetoksCommunity", "My wallet size: ${walletManager.getOrCreateWallet(myPeer.mid)}")
        Log.d("DetoksCommunity", "My peer wallet size: ${walletManager.getOrCreateWallet(peer.mid)}")
        val listOfTorrents = TorrentManager.getInstance(context).getListOfTorrents()
        if(listOfTorrents.isEmpty()) return
        val magnet = listOfTorrents.random().makeMagnetUri()

        val packet = serializePacket(MESSAGE_TORRENT_ID, TorrentMessage(magnet))

        // Send a token only to a new peer
        if (!visitedPeers.contains(peer)) {
            visitedPeers.add(peer)
            sendTokens(1, peer.mid)
        }

        send(peer.address, packet)
    }

    private fun onGossip(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(TorrentMessage.Deserializer)
        val torrentManager = TorrentManager.getInstance(context)
        Log.d("DeToksCommunity", "received torrent from ${peer.mid}, address: ${peer.address}, magnet: ${payload.magnet}")
        torrentManager.addTorrent(payload.magnet)
    }
    private fun onTransactionMessage(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(TransactionMessage.Deserializer)

        val senderWallet = walletManager.getOrCreateWallet(payload.senderMID)

        if (senderWallet.balance >= payload.amount) {
            senderWallet.balance -= payload.amount
            walletManager.setWalletBalance(payload.senderMID, senderWallet.balance)

            val recipientWallet = walletManager.getOrCreateWallet(payload.recipientMID)
            recipientWallet.balance += payload.amount
            walletManager.setWalletBalance(payload.recipientMID, recipientWallet.balance)

            Log.d("DeToksCommunity", "Received ${payload.amount} tokens from ${payload.senderMID}")
        } else {
            Log.d("DeToksCommunity", "Insufficient funds from ${payload.senderMID}!")
        }
    }

    override fun onIntroductionResponse(peer: Peer, payload: IntroductionResponsePayload) {
        super.onIntroductionResponse(peer, payload)

        if (peer.address in DEFAULT_ADDRESSES) {
            lastTrackerResponses[peer.address] = Date()
        }
    }

    fun broadcastLike(vid: String, torrent: String, creator: String) {
        Log.d("DeToks", "Liking: $vid")
        val like = Like(myPeer.publicKey.toString(), vid, torrent, creator)
        createProposalBlock(LIKE_BLOCK, like.toMap(), myPeer.publicKey.keyToBin())
        Log.d("DeToks", "$like")
    }

    fun getLikes(vid: String, torrent: String): List<TrustChainBlock> {
        return database.getBlocksWithType(LIKE_BLOCK).filter {
            it.transaction["video"] == vid && it.transaction["torrent"] == torrent
        }
    }

    fun getBlocksByAuthor(author: String): List<TrustChainBlock> {
        return database.getBlocksWithType(LIKE_BLOCK).filter {
            it.transaction["author"] == author
        }
    }

    fun userLikedVideo(vid: String, torrent: String, liker: String): Boolean {
        return getLikes(vid, torrent).any { it.transaction["liker"] == liker }
    }

    fun getPostedVideos(author: String): List<Pair<String, Int>> {
        // Create Key data class so we can group by two fields (torrent and video)
        data class Key(val video: String, val torrent: String)
        fun TrustChainBlock.toKey() = Key(transaction["video"] as String, transaction["torrent"] as String)
        val likes = getBlocksByAuthor(author).groupBy { it.toKey() }
        // TODO: sort based on posted timestamp
        return likes.entries.map {
            Pair(it.key.video, it.value.size)
        }
    }

    fun listOfLikedVideosAndTorrents(person: String): List<Pair<String,String>> {
        var iterator = database.getBlocksWithType(LIKE_BLOCK).filter {
            it.transaction["liker"] == person
        }.listIterator()
        var likedVideos = ArrayList<Pair<String,String>>()
        while(iterator.hasNext()) {
            val block = iterator.next()
            likedVideos.add(Pair(block.transaction.get("video") as String, block.transaction.get("torrent") as String))
        }
        return likedVideos;
    }

    class Factory(
        private val context: Context,
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<DeToksCommunity>(DeToksCommunity::class.java) {
        override fun create(): DeToksCommunity {
            return DeToksCommunity(context, settings, database, crawler)
        }
    }
}