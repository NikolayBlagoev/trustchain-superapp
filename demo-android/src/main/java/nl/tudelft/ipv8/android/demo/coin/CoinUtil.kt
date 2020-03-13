package nl.tudelft.ipv8.android.demo.coin

import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList


object CoinUtil {
    /**
     * Generate a random 128 bit string
     * From: https://sakthipriyan.com/2017/04/02/creating-base64-uuid-in-java.html
     */
    @JvmStatic
    fun randomUUID(): String {
        val uuid: UUID = UUID.randomUUID()
        val src: ByteArray = ByteBuffer.wrap(ByteArray(16))
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        return Base64.getUrlEncoder().encodeToString(src).substring(0, 22)
    }

    @JvmStatic
    fun parseTransaction(transaction: TrustChainTransaction): JSONObject {
        return JSONObject(transaction["message"].toString())
    }

    @JvmStatic
    fun parseJSONArray(jsonArray: JSONArray): ArrayList<String> {
        return Array(jsonArray.length()) {
            jsonArray.getString(it)
        }.toCollection(ArrayList())
    }
}
