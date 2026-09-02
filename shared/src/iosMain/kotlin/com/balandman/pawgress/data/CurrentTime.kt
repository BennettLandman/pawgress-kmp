package com.balandman.pawgress.data

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

// `timeIntervalSince1970` compiles as a Kotlin/Native *extension* property on
// NSDate (Foundation category member, not a plain @interface member), so --
// unlike NSDate itself -- it needs its own explicit import; importing just
// the class isn't enough. First real compile error this file hit (all 9
// prior errors elsewhere in the project are gone as of this same build log).
actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
