#!/usr/bin/env python3
"""Convert aapt2's generated R.java into a Kotlin object (R.kt).

We compile with kotlinc only (no javac available in this environment), so
R.java must be translated to Kotlin constants.
"""
import re
import sys

KOTLIN_KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
    "var", "when", "while", "by", "catch", "constructor", "delegate",
    "dynamic", "field", "file", "finally", "get", "import", "init", "param",
    "property", "receiver", "set", "setparam", "where", "actual", "abstract",
    "annotation", "companion", "const", "crossinline", "data", "enum", "expect",
    "external", "final", "infix", "inline", "inner", "internal", "lateinit",
    "noinline", "open", "operator", "out", "override", "private", "protected",
    "public", "reified", "sealed", "suspend", "tailrec", "vararg",
}


def esc(name: str) -> str:
    return f"`{name}`" if name in KOTLIN_KEYWORDS else name


def main():
    src, dst = sys.argv[1], sys.argv[2]
    text = open(src).read()
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//.*", "", text)

    # R.java structure: public final class R { public static final class NAME { ... } }
    classes = {}
    for m in re.finditer(
            r"public static final class (\w+)\s*\{(.*?)\n\s*\}",
            text, flags=re.S):
        cls, body = m.group(1), m.group(2)
        fields = re.findall(r"public static final int (\w+)\s*=\s*(0x[0-9a-fA-F]+|-\d+|\d+);", body)
        if fields:
            classes[cls] = fields

    out = ["// AUTO-GENERATED from R.java by build/r_java_to_kt.py — do not edit.",
           "package com.estatedesk.crm", "", "object R {"]
    for cls in sorted(classes):
        out.append(f"    object {esc(cls)} {{")
        for name, val in classes[cls]:
            out.append(f"        const val {esc(name)} = {val}")
        out.append("    }")
    out.append("}")
    open(dst, "w").write("\n".join(out) + "\n")
    print(f"R.kt written: {len(classes)} resource classes")


if __name__ == "__main__":
    main()
