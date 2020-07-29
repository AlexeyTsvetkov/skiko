package org.jetbrains.skiko

import org.jetbrains.skiko.internal.Library

class SkiaWindow {
  companion object {
    init {
      Library.load("/", "skiko")
    }
  }

  external fun nativeMethod(param: Long): Long
}

