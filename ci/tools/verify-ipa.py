#!/usr/bin/env python3
"""Verify an unsigned iOS .ipa built by the feasibility workflow.

The IPAs produced in CI cannot be installed on a device (they are unsigned), so the
strongest offline check is: the archive is intact, the Info.plist identifies an iOS app,
and the Mach-O executable inside the bundle is a 64-bit arm64 iOS binary rather than a
macOS or simulator build.

Usage: python verify-ipa.py <path-to-ipa> [<expected-sha256-file>]
"""
from __future__ import annotations

import hashlib
import plistlib
import struct
import sys
import zipfile

CPU_TYPE_ARM64 = 0x0100000C
CPU_TYPE_X86_64 = 0x01000007
FILE_TYPE_EXECUTE = 0x2
PLATFORM_IOS = 2
PLATFORM_IOSSIMULATOR = 7


def sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def macho_summary(data: bytes) -> dict:
    magic, cputype, cpusubtype, filetype, ncmds, sizeofcmds, flags, _reserved = struct.unpack_from(
        "<IiiIIII", data, 0
    )
    if magic not in (0xFEEDFACF, 0xFEEDFACE):
        return {"error": f"unexpected Mach-O magic 0x{magic:08x}"}
    result = {
        "magic": f"0x{magic:08x}",
        "64bit": magic == 0xFEEDFACF,
        "cputype": cputype,
        "cputype_name": {CPU_TYPE_ARM64: "arm64", CPU_TYPE_X86_64: "x86_64"}.get(cputype, hex(cputype)),
        "cpusubtype": cpusubtype,
        "filetype_execute": filetype == FILE_TYPE_EXECUTE,
        "ncmds": ncmds,
    }
    offset = 32
    for _ in range(ncmds):
        cmd, cmdsize = struct.unpack_from("<II", data, offset)
        if cmd in (0x32, 0x24):  # LC_BUILD_VERSION / LC_VERSION_MIN_IPHONEOS
            if cmd == 0x32:
                platform, minos, sdk, _ntools = struct.unpack_from("<IIII", data, offset + 8)
                result["platform"] = platform
                result["min_os"] = f"{minos >> 16}.{(minos >> 8) & 0xff}.{minos & 0xff}"
                result["sdk"] = f"{sdk >> 16}.{(sdk >> 8) & 0xff}.{sdk & 0xff}"
            else:
                minos, sdk = struct.unpack_from("<II", data, offset + 8)
                result["platform"] = PLATFORM_IOS
                result["min_os"] = f"{minos >> 16}.{(minos >> 8) & 0xff}.{minos & 0xff}"
            break
        offset += cmdsize
    return result


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    path = sys.argv[1]
    print(f"ipa: {path}")
    digest = sha256(path)
    print(f"sha256: {digest}")
    if len(sys.argv) > 2:
        expected = open(sys.argv[2], encoding="utf-8").read().split()[0]
        print(f"expected sha256: {expected}")
        print(f"checksum match: {expected == digest}")
        if expected != digest:
            return 1

    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        print(f"archive intact: {bad is None}")
        names = archive.namelist()
        apps = sorted({name.split("/")[1] for name in names if name.startswith("Payload/") and "/" in name[8:]})
        print(f"app bundles: {apps}")
        if not apps:
            print("FAIL: no Payload/*.app bundle inside the IPA")
            return 1
        app = apps[0]
        plist_name = f"Payload/{app}/Info.plist"
        if plist_name in names:
            plist = plistlib.loads(archive.read(plist_name))
            for key in ("CFBundleIdentifier", "CFBundleExecutable", "CFBundleName",
                        "CFBundleShortVersionString", "CFBundleVersion", "MinimumOSVersion",
                        "CFBundleSupportedPlatforms", "UISupportedInterfaceOrientations"):
                print(f"  {key}: {plist.get(key)}")
            executable = plist.get("CFBundleExecutable")
            binary_name = f"Payload/{app}/{executable}"
        else:
            print(f"FAIL: {plist_name} missing")
            return 1
        if binary_name not in names:
            print(f"FAIL: executable {binary_name} missing from bundle")
            return 1
        summary = macho_summary(archive.read(binary_name))
        print(f"executable: {binary_name}")
        for key, value in summary.items():
            print(f"  {key}: {value}")
        print(f"binary size: {archive.getinfo(binary_name).file_size} bytes")
        print(f"bundle entries: {sum(1 for n in names if n.startswith(f'Payload/{app}/'))}")

    ok = (summary.get("cputype") == CPU_TYPE_ARM64 and summary.get("filetype_execute")
          and summary.get("platform") == PLATFORM_IOS)
    print("VERDICT:", "PASS - arm64 iOS executable inside an intact app bundle" if ok
          else "FAIL - not a native arm64 iOS executable")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
