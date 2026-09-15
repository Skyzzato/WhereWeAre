package com.whereweare.app.ui

import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.whereweare.app.domain.normalizeInviteCode
import com.whereweare.app.domain.validInviteCode
import java.nio.ByteBuffer

fun qrInvitePayload(type: String,code: String): String {
    require(type in setOf("person","group") && validInviteCode(code))
    return "whereweare://$type/${normalizeInviteCode(code)}"
}
fun parseQrInvite(text: String): Pair<String,String>? =
    if(text.length>128) null else parseTypedInvite(Uri.parse(text),baseUrl="")

fun qrInviteBitmap(type: String,code: String): Bitmap {
    val matrix=QRCodeWriter().encode(qrInvitePayload(type,code),BarcodeFormat.QR_CODE,768,768,mapOf(EncodeHintType.MARGIN to 4))
    val pixels=IntArray(matrix.width*matrix.height) {index -> if(matrix[index%matrix.width,index/matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE}
    return Bitmap.createBitmap(pixels,matrix.width,matrix.height,Bitmap.Config.ARGB_8888)
}

/** Packs a Y plane while respecting padding, pixel stride and the buffer position. */
fun qrLuminancePlane(buffer: ByteBuffer,width: Int,height: Int,rowStride: Int,pixelStride: Int): ByteArray {
    require(width in 1..4096 && height in 1..4096 && pixelStride>0 && rowStride>0)
    val start=buffer.position()
    val end=start.toLong()+(height-1).toLong()*rowStride+(width-1).toLong()*pixelStride
    require(end<buffer.limit() && rowStride.toLong()>=(width-1).toLong()*pixelStride+1)
    return ByteArray(width*height) {index -> buffer.get(start+(index/width)*rowStride+(index%width)*pixelStride)}
}
fun decodeQrLuminance(bytes: ByteArray,width: Int,height: Int): String? {
    if(width<=0 || height<=0 || width.toLong()*height>bytes.size) return null
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(bytes,width,height,0,0,width,height,false)))).text
    } catch(_: ReaderException) {null}
}
