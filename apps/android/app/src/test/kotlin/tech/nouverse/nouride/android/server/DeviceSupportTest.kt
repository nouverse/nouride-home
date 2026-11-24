package tech.nouverse.nouride.android.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSupportTest {
  @Test
  fun `a device that started proot runs Nouride`() {
    assertTrue(DeviceSupport.runsFrom(null))
  }

  @Test
  fun `a kernel that refuses the executable's format cannot run Nouride`() {
    // ProcessBuilder's words on Android for ENOEXEC: what a 32-bit kernel answers to an aarch64 executable.
    assertFalse(DeviceSupport.runsFrom("Cannot run program \"/data/app/x/lib/arm/libproot.so\": error=8, Exec format error"))
    assertFalse(DeviceSupport.runsFrom("error=8, Exec format error"))
  }

  @Test
  fun `any other failure to start is not this one, and is left to the start that follows`() {
    assertTrue(DeviceSupport.runsFrom("Cannot run program \"/data/app/x/lib/arm/libproot.so\": error=2, No such file or directory"))
    assertTrue(DeviceSupport.runsFrom("Cannot run program \"/data/app/x/lib/arm/libproot.so\": error=13, Permission denied"))
    // error=8 is not error=80-something.
    assertTrue(DeviceSupport.runsFrom("error=88, Socket operation on non-socket"))
  }
}
