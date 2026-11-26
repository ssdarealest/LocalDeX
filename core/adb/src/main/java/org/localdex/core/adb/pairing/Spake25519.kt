package org.localdex.core.adb.pairing

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure-Kotlin implementation of BoringSSL's SPAKE2 over Edwards25519 (`spake25519.cc`),
 * matching Android 11+ `adbd` wireless pairing bit-for-bit.
 */
class Spake25519(
    private val role: Role,
    private val myName: ByteArray,
    private val theirName: ByteArray,
    private val random: SecureRandom = SecureRandom()
) {
    enum class Role {
        ALICE,
        BOB
    }

    private enum class State {
        INIT,
        MSG_GENERATED,
        KEY_GENERATED
    }

    private var state = State.INIT
    private var privateKey: BigInteger = BigInteger.ZERO
    private var passwordScalar: BigInteger = BigInteger.ZERO
    private var passwordHash: ByteArray = ByteArray(0)
    private var myMsg: ByteArray = ByteArray(0)

    /**
     * Generates this peer's 32-byte blinded SPAKE2 public message from [password].
     */
    fun generateMessage(password: ByteArray, privateSeed: ByteArray? = null): ByteArray {
        check(state == State.INIT) { "SPAKE2 message already generated" }

        // 1. Ephemeral private key: random 64 bytes -> sc_reduce -> * 8 (clears cofactor)
        val privBytes = privateSeed ?: ByteArray(64).also { random.nextBytes(it) }
        require(privBytes.size == 64) { "Private seed must be 64 bytes" }
        privateKey = scReduce(privBytes).shiftLeft(3)

        // P = privateKey * G
        val pPoint = scalarMult(BASE_G, privateKey)

        // 2. Password scalar: SHA-512(password) -> sc_reduce -> BoringSSL cofactor-clearing adjustment
        val sha512 = MessageDigest.getInstance("SHA-512")
        passwordHash = sha512.digest(password)
        var w = scReduce(passwordHash)
        if (w.testBit(0)) w = w.add(L)
        if (w.testBit(1)) w = w.add(L.shiftLeft(1))
        if (w.testBit(2)) w = w.add(L.shiftLeft(2))
        check(w.and(BigInteger.valueOf(7)) == BigInteger.ZERO)
        passwordScalar = w

        // mask = passwordScalar * (M for Alice, N for Bob)
        val maskPoint = scalarMult(if (role == Role.ALICE) POINT_M else POINT_N, passwordScalar)

        // P* = P + mask
        val pStar = pPoint.add(maskPoint)
        myMsg = pStar.encode()
        state = State.MSG_GENERATED
        return myMsg.copyOf()
    }

    /**
     * Processes the peer's 32-byte SPAKE2 message and returns the 64-byte shared key material,
     * or null if the peer message is not a valid point on Edwards25519.
     */
    fun processMessage(theirMsg: ByteArray): ByteArray? {
        check(state == State.MSG_GENERATED) { "Must call generateMessage before processMessage" }
        if (theirMsg.size != 32) return null

        val qStar = Point.decode(theirMsg) ?: return null

        // Unmask peer's message: Q = Q* - passwordScalar * (N for Alice, M for Bob)
        val peersMask = scalarMult(if (role == Role.ALICE) POINT_N else POINT_M, passwordScalar)
        val qPoint = qStar.add(peersMask.negate())

        // dh_shared = privateKey * Q
        val dhShared = scalarMult(qPoint, privateKey).encode()

        // Transcript hash via SHA-512 with 8-byte little-endian length prefixes
        val sha512 = MessageDigest.getInstance("SHA-512")
        if (role == Role.ALICE) {
            updateWithLengthPrefix(sha512, myName)
            updateWithLengthPrefix(sha512, theirName)
            updateWithLengthPrefix(sha512, myMsg)
            updateWithLengthPrefix(sha512, theirMsg)
        } else {
            updateWithLengthPrefix(sha512, theirName)
            updateWithLengthPrefix(sha512, myName)
            updateWithLengthPrefix(sha512, theirMsg)
            updateWithLengthPrefix(sha512, myMsg)
        }
        updateWithLengthPrefix(sha512, dhShared)
        updateWithLengthPrefix(sha512, passwordHash)

        state = State.KEY_GENERATED
        return sha512.digest()
    }

    private fun updateWithLengthPrefix(digest: MessageDigest, data: ByteArray) {
        val lenLe = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(data.size.toLong())
            .array()
        digest.update(lenLe)
        digest.update(data)
    }

    internal data class Point(
        val x: BigInteger,
        val y: BigInteger,
        val z: BigInteger,
        val t: BigInteger
    ) {
        fun negate(): Point = Point(
            x = if (x.signum() == 0) BigInteger.ZERO else P.subtract(x),
            y = y,
            z = z,
            t = if (t.signum() == 0) BigInteger.ZERO else P.subtract(t)
        )

        fun add(other: Point): Point {
            val a = y.subtract(x).multiply(other.y.subtract(other.x)).mod(P)
            val b = y.add(x).multiply(other.y.add(other.x)).mod(P)
            val c = t.multiply(TWO_D).multiply(other.t).mod(P)
            val d = z.multiply(TWO).multiply(other.z).mod(P)
            val e = b.subtract(a).mod(P)
            val f = d.subtract(c).mod(P)
            val g = d.add(c).mod(P)
            val h = b.add(a).mod(P)
            return Point(
                x = e.multiply(f).mod(P),
                y = g.multiply(h).mod(P),
                z = f.multiply(g).mod(P),
                t = e.multiply(h).mod(P)
            )
        }

        fun encode(): ByteArray {
            val zInv = z.modInverse(P)
            val affineX = x.multiply(zInv).mod(P)
            val affineY = y.multiply(zInv).mod(P)
            val out = toLittleEndian32(affineY)
            if (affineX.testBit(0)) {
                out[31] = (out[31].toInt() or 0x80).toByte()
            }
            return out
        }

        companion object {
            val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

            fun fromAffine(x: BigInteger, y: BigInteger): Point {
                return Point(x.mod(P), y.mod(P), BigInteger.ONE, x.multiply(y).mod(P))
            }

            fun decode(bytes: ByteArray): Point? {
                if (bytes.size != 32) return null
                val xSign = (bytes[31].toInt() ushr 7) and 1
                val yBytes = bytes.copyOf()
                yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
                val y = fromLittleEndian(yBytes)
                if (y >= P) return null

                val y2 = y.multiply(y).mod(P)
                val u = y2.subtract(BigInteger.ONE).mod(P)
                val v = D.multiply(y2).add(BigInteger.ONE).mod(P)
                val v3 = v.pow(3).mod(P)
                val v7 = v3.multiply(v3).multiply(v).mod(P)

                // Candidate x = (u * v^3) * (u * v^7)^((p - 5) / 8) mod p
                var x = u.multiply(v3).mod(P)
                    .multiply(u.multiply(v7).mod(P).modPow(P_MINUS_5_DIV_8, P))
                    .mod(P)

                val vx2 = v.multiply(x).multiply(x).mod(P)
                if (vx2 != u) {
                    if (vx2 == P.subtract(u).mod(P)) {
                        x = x.multiply(SQRT_MINUS_1).mod(P)
                    } else {
                        return null
                    }
                }

                if (x.signum() == 0 && xSign == 1) return null
                val xParity = if (x.testBit(0)) 1 else 0
                if (xParity != xSign && x.signum() != 0) {
                    x = P.subtract(x)
                }

                return fromAffine(x, y)
            }
        }
    }

    companion object {
        // Edwards25519 field prime p = 2^255 - 19
        private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))

        // Prime-order subgroup order L = 2^252 + 27742317777372353535851937790883648493
        internal val L = BigInteger.ONE.shiftLeft(252)
            .add(BigInteger("27742317777372353535851937790883648493"))

        // Curve parameter d = -121665 / 121666 mod p
        private val TWO = BigInteger.valueOf(2L)
        private val D = BigInteger.valueOf(-121665L)
            .multiply(BigInteger.valueOf(121666L).modInverse(P))
            .mod(P)

        private val TWO_D = D.multiply(TWO).mod(P)
        private val P_MINUS_5_DIV_8 = P.subtract(BigInteger.valueOf(5)).shiftRight(3)
        private val SQRT_MINUS_1 = TWO.modPow(P.subtract(BigInteger.ONE).shiftRight(2), P)

        // Edwards25519 base point G
        internal val BASE_G = Point.fromAffine(
            x = BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202"),
            y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
        )

        // BoringSSL spake25519.cc mask point M (Alice / Client)
        internal val POINT_M = Point.fromAffine(
            x = BigInteger("31406539342727633121250288103050113562375374900226415211311216773867585644232"),
            y = BigInteger("21177308356423958466833845032658859666296341766942662650232962324899758529114")
        )

        // BoringSSL spake25519.cc mask point N (Bob / Server)
        internal val POINT_N = Point.fromAffine(
            x = BigInteger("49918732221787544735331783592030787422991506689877079631459872391322455579424"),
            y = BigInteger("54629554431565467720832445949441049581317094546788069926228343916274969994000")
        )

        internal fun scReduce(littleEndianBytes: ByteArray): BigInteger {
            return fromLittleEndian(littleEndianBytes).mod(L)
        }

        internal fun scalarMult(point: Point, scalar: BigInteger): Point {
            var acc = Point.IDENTITY
            var addend = point
            val bitLen = scalar.bitLength()
            for (bit in 0 until bitLen) {
                if (scalar.testBit(bit)) {
                    acc = acc.add(addend)
                }
                addend = addend.add(addend)
            }
            return acc
        }

        private fun fromLittleEndian(le: ByteArray): BigInteger {
            val be = le.reversedArray()
            return BigInteger(1, be)
        }

        private fun toLittleEndian32(value: BigInteger): ByteArray {
            val be = value.toByteArray()
            val out = ByteArray(32)
            // Copy from LSB (end of big-endian array) into little-endian out[0..31]
            var srcIdx = be.size - 1
            var dstIdx = 0
            while (srcIdx >= 0 && dstIdx < 32) {
                out[dstIdx++] = be[srcIdx--]
            }
            return out
        }
    }
}
