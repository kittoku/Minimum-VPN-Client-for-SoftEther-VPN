package kittoku.mvc.extension


internal fun Int.addOnesComplement(addend: Int): Int {
    var sum = this + addend

    if (sum and 0x00010000 != 0) {
        sum += 1
    }

    return sum and 0x0000FFFF
}
