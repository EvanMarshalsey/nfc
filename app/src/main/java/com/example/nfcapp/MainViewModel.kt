package com.example.nfcapp

import android.R.attr
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcV
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.experimental.and


public class MainViewModel : AndroidViewModel {

    companion object {
        private val TAG = MainViewModel::class.java.getSimpleName()
        private const val prefix = "android.nfc.tech."
    }

    private val liveNFC : MutableStateFlow<NFCStatus?>
    private val liveToast : MutableSharedFlow<String?>
    private val liveTag : MutableStateFlow<String?>

    private var kegStatus: String = "empty" // Default status

    fun setKegStatus(status: String) {
        kegStatus = status
    }

    fun getKegStatus(): String {
        return kegStatus
    }



    constructor(application : Application) : super(application) { Log.d(TAG, "constructor")
        liveNFC = MutableStateFlow(null)
        liveToast = MutableSharedFlow()
        liveTag = MutableStateFlow(null)
    }
    //region Toast Methods
    private fun updateToast(message : String) { Coroutines.io(this@MainViewModel, {
        liveToast.emit(message)
    } ) }

    private suspend fun postToast(message : String) { Log.d(TAG, "postToast(${message})")
        liveToast.emit(message)
    }

    public fun observeToast() : SharedFlow<String?> {
        return liveToast.asSharedFlow()
    }
    //endregion
    public fun getNFCFlags() : Int {
        return NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V or
        NfcAdapter.FLAG_READER_NFC_BARCODE //or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }

    public fun getExtras() : Bundle {
        val options : Bundle = Bundle();
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 30000);
        return options
    }
    //region NFC Methods
    public fun onCheckNFC(isChecked : Boolean) { Coroutines.io(this@MainViewModel, { Log.d(TAG, "onCheckNFC(${isChecked})")
        if (isChecked) {
            postNFCStatus(NFCStatus.Tap)
        } else {
            postNFCStatus(NFCStatus.NoOperation)
            postToast("NFC is Disabled, Please Toggle On!")
        }
    } ) }

    public fun readTag(tag : Tag?) { Coroutines.default(this@MainViewModel, { Log.d(TAG, "readTag(${tag} ${tag?.getTechList()})")
        postNFCStatus(NFCStatus.Process)
        val stringBuilder : StringBuilder = StringBuilder()
        val id : ByteArray? = tag?.getId()
        stringBuilder.append("Tag ID (hex): ${getHex(id!!)} \n")
        stringBuilder.append("Tag ID (dec): ${getDec(id)} \n")
        stringBuilder.append("Tag ID (reversed): ${getReversed(id)} \n")
        stringBuilder.append("Technologies: ")
        tag.getTechList().forEach { tech ->
            stringBuilder.append(tech.substring(prefix.length))
            stringBuilder.append(", ")
        }
        stringBuilder.delete(stringBuilder.length - 2, stringBuilder.length)
        tag.getTechList().forEach { tech ->
            if (tech.equals(MifareClassic::class.java.getName())) {
                stringBuilder.append('\n')
                val mifareTag : MifareClassic = MifareClassic.get(tag)
                val type : String
                if (mifareTag.getType() == MifareClassic.TYPE_CLASSIC) type = "Classic"
                else if (mifareTag.getType() == MifareClassic.TYPE_PLUS) type = "Plus"
                else if (mifareTag.getType() == MifareClassic.TYPE_PRO) type = "Pro"
                else type = "Unknown"
                stringBuilder.append("Mifare Classic type: $type \n")
                stringBuilder.append("Mifare size: ${mifareTag.getSize()} bytes \n")
                stringBuilder.append("Mifare sectors: ${mifareTag.getSectorCount()} \n")
                stringBuilder.append("Mifare blocks: ${mifareTag.getBlockCount()} \n")
                stringBuilder.append(readMifareClassicData(mifareTag))
            }
            if (tech.equals(MifareUltralight::class.java.getName())) {
                stringBuilder.append('\n');
                val mifareUlTag : MifareUltralight = MifareUltralight.get(tag);
                val type : String
                if (mifareUlTag.getType() == MifareUltralight.TYPE_ULTRALIGHT) type = "Ultralight"
                else if (mifareUlTag.getType() == MifareUltralight.TYPE_ULTRALIGHT_C) type = "Ultralight C"
                else type = "Unkown"
                stringBuilder.append("Mifare Ultralight type: ");
                stringBuilder.append(type)
            }
        }
        Log.d(TAG, "Datum: $stringBuilder")
        Log.d(ContentValues.TAG, "dumpTagData Return \n $stringBuilder")
        postNFCStatus(NFCStatus.Read)
        liveTag.emit("${getDateTimeNow()} \n ${stringBuilder}")
    } ) }

    public fun readNfcTagOld(context: Context, tag: Tag?) {
        val nfcV = NfcV.get(tag)

        if (nfcV != null) {
            var retryCount = 0
            var response: ByteArray? = null

            while (retryCount < 3 && response == null) {
                try {
                    // Reconnect if not already connected
                    if (!nfcV.isConnected) {
                        nfcV.connect()
                    }

                    val uid = tag?.id
                    if (uid == null) {
                        Toast.makeText(context, "Error: Tag UID is null", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val blockNumber = 0x00 // Replace with the desired block number

                    // Command: Read Single Block (Flags, Command, UID, Block Number)
                    val command = byteArrayOf(
                        0x02.toByte(), // Flags
                        0x20.toByte(), // Command: Read Single Block
                        *uid,          // UID
                        blockNumber.toByte()
                    )

                    response = try {
                        nfcV.transceive(command) // Perform NFC transceive
                    } catch (e: SecurityException) {
                        // Handle tag loss during transceive
                        Toast.makeText(context, "Tag is no longer valid. Please rescan the tag.", Toast.LENGTH_SHORT).show()
                        return
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    retryCount++
                    if (retryCount >= 3) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Handle the response if successful
            if (response != null) {
                val data = response.copyOfRange(1, response.size) // Skip status byte
                Toast.makeText(context, "Data: ${data.joinToString(", ")}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Error: NFC Tag is null", Toast.LENGTH_SHORT).show()
        }
    }

    public fun readNfcTag(context: Context, tag: Tag?) {
        val nfcV = NfcV.get(tag)

        if (nfcV != null) {
            var retryCount = 0
            var response: ByteArray? = null

            while (retryCount < 3 && response == null) {
                try {
                    // Reconnect if not already connected
                    if (!nfcV.isConnected) {
                        nfcV.connect()
                    }

                    val uid = tag?.id
                    if (uid == null) {
                        Toast.makeText(context, "Error: Tag UID is null", Toast.LENGTH_SHORT).show()
                        return
                    }


                    // set up read command buffer
                    val blockNo: Byte = 0 // block address
//                    val id: ByteArray = attr.tag.getId()
                    val readCmd = ByteArray(3 + uid.size)
                    readCmd[0] = 0x20 // set "address" flag (only send command to this tag)
                    readCmd[1] = 0x20 // ISO 15693 Single Block Read command byte
                    System.arraycopy(uid, 0, readCmd, 2, uid.size) // copy ID
                    readCmd[2 + uid.size] = blockNo // 1 byte payload: block address

//                    val blockNumber = 0x00 // Replace with the desired block number
//
//                    // Command: Read Single Block (Flags, Command, UID, Block Number)
//                    val command = byteArrayOf(
//                        0x02.toByte(), // Flags
//                        0x20.toByte(), // Command: Read Single Block
//                        *uid,          // UID
//                        blockNumber.toByte()
//                    )

                    response = try {
                        nfcV.transceive(readCmd) // Perform NFC transceive
                    } catch (e: SecurityException) {
                        // Handle tag loss during transceive
                        Toast.makeText(context, "Tag is no longer valid. Please rescan the tag.", Toast.LENGTH_SHORT).show()
                        return
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    retryCount++
                    if (retryCount >= 3) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Handle the response if successful
            if (response != null) {
                val data = response.copyOfRange(1, response.size) // Skip status byte
                Toast.makeText(context, "Data: ${data.joinToString(", ")}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Error: NFC Tag is null", Toast.LENGTH_SHORT).show()
        }
    }


    public fun updateNFCStatus(status : NFCStatus) { Coroutines.io(this@MainViewModel, {
        postNFCStatus(status)
    } ) }

    private suspend fun postNFCStatus(status : NFCStatus) { Log.d(TAG, "postNFCStatus(${status})")
        if (NFCManager.isSupportedAndEnabled(getApplication())) {
            liveNFC.emit(status)
        }
        else if (NFCManager.isNotEnabled(getApplication())) {
            liveNFC.emit(NFCStatus.NotEnabled)
            postToast("Please Enable your NFC!")
            liveTag.emit("Please Enable your NFC!")
        } else if (NFCManager.isNotSupported(getApplication())) {
            liveNFC.emit(NFCStatus.NotSupported)
            postToast("NFC Not Supported!")
            liveTag.emit("NFC Not Supported!")
        }
        if (NFCManager.isSupportedAndEnabled(getApplication()) && status == NFCStatus.Tap) {
            liveTag.emit("Please Tap Now!")
        } else {
            liveTag.emit(null)
        }
    }

    public fun observeNFCStatus() : StateFlow<NFCStatus?> {
        return liveNFC.asStateFlow()
    }
    //endregion
    //region Tags Information Methods
    private fun getDateTimeNow() : String { Log.d(TAG, "getDateTimeNow()")
        val TIME_FORMAT : DateFormat = SimpleDateFormat.getDateTimeInstance()
        val now : Date = Date()
        Log.d(ContentValues.TAG,"getDateTimeNow() Return ${TIME_FORMAT.format(now)}")
        return TIME_FORMAT.format(now)
    }

    private fun getHex(bytes : ByteArray) : String {
        val sb = StringBuilder()
        for (i in bytes.indices.reversed()) {
            val b : Int = bytes[i].and(0xff.toByte()).toInt()
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
            if (i > 0)
                sb.append(" ")
        }
        return sb.toString()
    }

    private fun getDec(bytes : ByteArray) : Long {
        Log.d(TAG, "getDec()")
        var result : Long = 0
        var factor : Long = 1
        for (i in bytes.indices) {
            val value : Long = bytes[i].and(0xffL.toByte()).toLong()
            result += value * factor
            factor *= 256L
        }
        return result
    }

    private fun getReversed(bytes : ByteArray) : Long {
        Log.d(TAG, "getReversed()")
        var result : Long = 0
        var factor : Long = 1
        for (i in bytes.indices.reversed()) {
            val value = bytes[i].and(0xffL.toByte()).toLong()
            result += value * factor
            factor *= 256L
        }
        return result
    }

    public fun observeTag() : StateFlow<String?> {
        return liveTag.asStateFlow()
    }

    private fun readMifareClassicData(tag: MifareClassic): String {
        val mifareClassic = tag
        val stringBuilder = StringBuilder()

        try {
            mifareClassic.connect()

            // Loop through sectors
            for (sector in 0 until mifareClassic.sectorCount) {
                val auth = mifareClassic.authenticateSectorWithKeyA(sector, MifareClassic.KEY_DEFAULT)
                if (auth) {
                    // Loop through each block in the sector in the correct order
                    for (block in 0 until mifareClassic.getBlockCountInSector(sector)) {
                        val blockIndex = mifareClassic.sectorToBlock(sector) + block
                        val data = mifareClassic.readBlock(blockIndex)
                        stringBuilder.append("Block $blockIndex: ${data.toHexString()}\n")
                    }
                } else {
                    stringBuilder.append("Sector $sector authentication failed\n")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error reading MIFARE Classic data", e)
        } finally {
            try {
                mifareClassic.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing MIFARE Classic connection", e)
            }
        }

        return stringBuilder.toString()
    }

    // Helper extension function to convert byte array to hexadecimal string
    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }

    internal fun writeKeyStatusToTag(tag: Tag, kegStatus: String) {
        val mifareClassic = MifareClassic.get(tag)
        val blockIndex = 4 // Change this to the block you want to write to

        try {
            mifareClassic.connect()

            // Authenticate with the default key
            val isAuthenticated = mifareClassic.authenticateSectorWithKeyA(blockIndex / mifareClassic.getBlockCountInSector(blockIndex), MifareClassic.KEY_DEFAULT)

            if (isAuthenticated) {
                val dataToWrite = kegStatus.toByteArray() // Convert the keg status to bytes
                val paddedData = dataToWrite + ByteArray(MifareClassic.BLOCK_SIZE - dataToWrite.size) // Pad the data to the block size

                // Write the data to the specified block
                mifareClassic.writeBlock(blockIndex, paddedData)
                Log.d(TAG, "Successfully wrote to block $blockIndex: $kegStatus")
            } else {
                Log.e(TAG, "Authentication failed for block $blockIndex")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to tag", e)
        } finally {
            try {
                mifareClassic.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing MIFARE Classic connection", e)
            }
        }
    }

    //endregion
}