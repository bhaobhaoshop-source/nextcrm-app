#!/usr/bin/env python3
"""Zip a directory of .class files into a jar."""
import sys, zipfile, os

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(src):
        for f in files:
            if f.endswith(".class"):
                full = os.path.join(root, f)
                rel = os.path.relpath(full, src)
                z.write(full, rel)
print("jar written:", dst)
