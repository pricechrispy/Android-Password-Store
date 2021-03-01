package com.zeapo.pwdstore.crypto

import kotlin.math.*
import java.math.BigInteger

// ADAPTED FROM https://github.com/davidbau/seedrandom/blob/released/seedrandom.js

/*
    shl - signed shift left (equivalent of << operator)
    shr - signed shift right (equivalent of >> operator)
    ushr - unsigned shift right (equivalent of >>> operator)
    and - bitwise and (equivalent of & operator)
    or - bitwise or (equivalent of | operator)
    xor - bitwise xor (equivalent of ^ operator)
    inv - bitwise complement (equivalent of ~ operator)
*/

fun getMutListEl(ml:MutableList<Int>, i:Int): Int {
    try {
        return ml[i]
    } catch (e:IndexOutOfBoundsException) {
        //println("getMutListEl(): " + e)
        return 0
    }
}

fun setMutListEl(ml:MutableList<Int>, i:Int, v:Int) {
    try {
        ml[i] = v
    } catch (e:IndexOutOfBoundsException) {
        //println("setMutListEl(): " + e)
        ml.add(i, v)
    }
}

var pool:MutableList<Int> = mutableListOf<Int>()

var width:Int = 256        // each RC4 output is 0 <= x < 256
var chunks:Int = 6         // at least six RC4 outputs for each double
var digits:Int = 52        // there are 52 significant digits in a double
var rngname:String = "random" // rngname: name for Math.random and Math.seedrandom
var startdenom:Long = (width*1.0).pow(chunks).toLong()
var significance:Long = (2.0).pow(digits).toLong()
var overflow:Long = significance * 2
var mask:Int = width - 1

//
// seedrandom()
// This is the seedrandom function described above.
//
fun seedrandom(seed:String): ARC4 {
    var key:MutableList<Int> = mutableListOf<Int>()

    var shortseed:String = mixkey(seed, key)

    //println(key)
    //println(shortseed)

    // Use the seed to initialize an ARC4 generator.
    var arc4:ARC4 = ARC4(key)

    //println("back in main()")
    //println(arc4.S) //102,179,90,72,191,58,6,1,228,245,...
    //println(arc4.i) //0
    //println(arc4.j) //163

    // Mix the randomness into accumulated entropy.
    mixkey(tostring(arc4.S), pool)

    //return prng(arc4)
    return arc4
}

// This function returns a random double in [0, 1) that contains
// randomness in every bit of the mantissa of the IEEE 754 value.
fun prng(arc4:ARC4): Double {
    var n:Long = arc4.g(chunks).toLong()
    var d:BigInteger = startdenom.toBigInteger()
    var x:Long = 0
    //println("prng()")
    while (n < significance) {
        //println("n:" + n + ",d:" + d + ",x:" + x)
        n = (n + x) * width
        d = d * width.toBigInteger()
        x = arc4.g(1).toLong()
    }
    //println("finish (n < significance)")
    //println("n:" + n + ",d:" + d + ",x:" + x)
    while (n >= overflow) {
        //println("n:" + n + ",d:" + d + ",x:" + x)
        n = n / 2
        d = d / 2.toBigInteger()
        x = x ushr 1
    }
    //println("finish (n >= overflow)")
    //println("n:" + n + ",d:" + d + ",x:" + x)

    var _temp_top:BigInteger = (n + x).toBigInteger()

    return (_temp_top.toDouble() / d.toDouble())
    //return (n + x) / (d * 1.0)
}

fun prng_int32(arc4:ARC4): Long {
    return arc4.g(4) or 0
}

fun prng_quick(arc4:ARC4): Long {
    return arc4.g(4) / 0x100000000
}

fun prng_double(arc4:ARC4): Double {
    return prng(arc4)
}

//
// ARC4
//
// An ARC4 implementation.  The constructor takes a key in the form of
// an array of at most (width) integers that should be 0 <= x < (width).
//
// The g(count) method returns a pseudorandom integer that concatenates
// the next (count) outputs from ARC4.  Its return value is a number x
// that is in the range 0 <= x < (width ^ count).
//
class ARC4 {
    var i:Int
    var j:Int
    var S:MutableList<Int>

    init {
        //println("init ARC4")

        i = 0
        j = 0
        S = mutableListOf<Int>()
    }

    constructor(arg_key:MutableList<Int>) {
        var key:MutableList<Int> = arg_key

        //println("ARC4:construct")

        var t:Int = 0
        var keylen:Int = key.size
        var i:Int = 0
        var j:Int = 0
        var s:MutableList<Int> = this.S

        // The empty key [] is treated as [0].
        if (keylen == 0) {
            key = mutableListOf<Int>()

            //key[0] = 0
            setMutListEl(key, 0, 0)

            keylen += 1
        }

        // Set up S using the standard key scheduling algorithm.
        while (i < width) {
            //s[i] = i++
            setMutListEl(s, i, i++)
        }

        for (iterator in 0..(width-1)) {
            //t = s[i]
            t = getMutListEl(s, iterator)

            //j = mask and (j + key[i % keylen] + t)
            j = mask and (j + getMutListEl(key, iterator % keylen) + t)

            //s[i] = s[j]
            setMutListEl(s, iterator, getMutListEl(s, j))

            //s[j] = t
            setMutListEl(s, j, t)
        }

        //println(key) //[114, 64, 110, 100, 48, 109, 107, 51, 121]
        //println(t) //28
        //println(keylen) //9
        //println(i) //256
        //println(j) //29
        //println(s) //[ 46, 188, 45, 73, 190, 52, 161, 29, 92, 17, …
        //println(this.S) //S:[ 46, 188, 45, 73, 190, 52, 161, 29, 92, 17, …
        //println(this.i) //i:0
        //println(this.j) //j:0

        g(width)
    }

    // The "g" method returns the next (count) outputs as one number.
    fun g(arg_count:Int): Long {
        var count:Int = arg_count

        // Using instance members instead of closure state nearly doubles speed.
        var t:Int = 0
        var r:Long = 0
        var i:Int = this.i
        var j:Int = this.j
        var s:MutableList<Int> = this.S

        while (count != 0) {
            count -= 1

            i = mask and (i + 1)

            //t = s[i]
            t = getMutListEl(s, i)

            j = mask and (j + t)

            //s[i] = s[j]
            setMutListEl(s, i, getMutListEl(s, j))

            //s[j] = t
            setMutListEl(s, j, t)

            //r = r * width + s[mask and (s[i] + s[j])]
            r = r * width + getMutListEl(s, mask and (getMutListEl(s, i) + getMutListEl(s, j)))
        }

        this.i = i
        this.j = j

        return r
        // For robust unpredictability, the function call below automatically
        // discards an initial batch of values.  This is called RC4-drop[256].
        // See http://google.com/search?q=rsa+fluhrer+response&btnI
    }
}

//
// mixkey()
// Mixes a string seed into a key that is an array of integers, and
// returns a shortened string seed that is equivalent to the result key.
//
fun mixkey(seed:String, key:MutableList<Int>): String {
    var stringseed:String = seed + ""
    var smear:Int = 0
    var j:Int = 0

    while (j < stringseed.length) {
        //smear = smear xor key[mask and j] * 19
        smear = smear xor getMutListEl(key, mask and j) * 19

        //key[mask and j] = mask and (smear + stringseed.get(j).toInt())
        setMutListEl(key, mask and j, mask and (smear + stringseed.get(j).toInt()))

        j += 1
    }

    //return String(key.toIntArray(), 0, key.size)
    return tostring(key)
}

//
// tostring()
// Converts an array of charcodes to a string
//
fun tostring(a:MutableList<Int>): String {
    return String(a.toIntArray(), 0, a.size)
}

