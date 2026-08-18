#!/usr/bin/env python3
"""Insert classes.dex (and classes2.dex...) into the aapt2-linked APK."""
import sys, zipfile, os

unsigned, dexdir, out = sys.argv[1], sys.argv[2], sys.argv[3]
dex_files = sorted(f for f in os.listdir(dexdir) if f.endswith(".dex"))
if not dex_files:
    sys.exit("no dex files found in " + dexdir)
with zipfile.ZipFile(unsigned, "r") as zin:
    names = zin.namelist()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for n in names:
            if n.startswith("META-INF/") or n.endswith(".dex"):
                continue
            zout.writestr(n, zin.read(n))
        for f in dex_files:
            zout.write(os.path.join(dexdir, f), f)
print("packaged:", out, "with", ", ".join(dex_files))
