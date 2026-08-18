#!/usr/bin/env python3
"""INDEPENDENT APK Signature Scheme v2 verifier, written against AOSP's
apksig (V2SchemeSigner/V2SchemeVerifier) semantics:

- v2 digest: ONE content digest per signature algorithm, computed as
    H(0x5a || uint32le(chunkCount) || concat(H(0xa5 || uint32le(len) || chunk)))
  over the segments [content, central directory, EOCD with its CD offset
  field patched to the start of the signing block].
- signed data = lp(digests) + lp(certificates) + lp(additional attributes);
  trailing bytes tolerated (older apksigner releases emit a few zero bytes).
- signatures = sequence of lp(alg + lp(signature)); RSA PKCS1 v1.5.

The authoritative verifier remains Google's apksigner itself (run in
build.sh); this is an independent cross-check.

Usage: verify_apk_v2.py <apk>
"""
import hashlib
import struct
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_der_public_key

MAGIC = b"APK Sig Block 42"
SIGNER_ID = 0x7109871A
ALG_SHA256 = 0x0103
ALG_SHA512 = 0x0104
CHUNK = 1024 * 1024


def u32(b, off):
    return struct.unpack("<I", b[off:off + 4])[0]


def u64(b, off):
    return struct.unpack("<Q", b[off:off + 8])[0]


def lp(b, off):
    n = u32(b, off)
    return b[off + 4:off + 4 + n], off + 4 + n


def compute_content_digest(segments, h):
    """AOSP V2SchemeSigner.computeContentDigests for a single algorithm."""
    chunk_digests = []
    for seg in segments:
        for i in range(0, len(seg), CHUNK):
            chunk = seg[i:i + CHUNK]
            chunk_digests.append(h(b"\xa5" + struct.pack("<I", len(chunk)) + chunk).digest())
    return h(b"\x5a" + struct.pack("<I", len(chunk_digests)) + b"".join(chunk_digests)).digest()


def main(path):
    data = open(path, "rb").read()
    problems = []

    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        problems.append("no EOCD")
        return report(problems)
    cd_offset = u32(data, eocd + 16)
    cd_size = u32(data, eocd + 12)
    if cd_offset + cd_size != eocd:
        problems.append("EOCD mismatch")

    if data[cd_offset - 16: cd_offset] != MAGIC:
        problems.append("no v2 magic before central directory")
        return report(problems)
    magic_off = cd_offset - 16
    size2 = u64(data, magic_off - 8)
    block_start = magic_off - size2 + 8
    if block_start < 0:
        problems.append("negative block start")
        return report(problems)
    if u64(data, block_start) != size2:
        problems.append("size1 != size2")

    off = block_start + 8
    pair_end = magic_off - 8
    pairs = []
    while off < pair_end:
        plen = u64(data, off)
        pid = u32(data, off + 8)
        pairs.append((pid, data[off + 12: off + 8 + plen]))
        off += 8 + plen
    if off != pair_end:
        problems.append("pair list overruns block")
    if SIGNER_ID not in [p[0] for p in pairs]:
        problems.append("no v2 signer pair")

    # patched EOCD: CD offset -> start of signing block (per AOSP)
    eocd_bytes = bytearray(data[eocd:eocd + 22])
    eocd_bytes[16:20] = struct.pack("<I", block_start)

    signers_ok = 0
    for pid, value in pairs:
        if pid != SIGNER_ID:
            continue
        signers, _ = lp(value, 0)
        so = 0
        while so < len(signers):
            signer, so = lp(signers, so)
            soff = 0
            signed_data, soff = lp(signer, soff)
            sigs, soff = lp(signer, soff)
            pub, soff = lp(signer, soff)
            if soff > len(signer):
                problems.append("signer overruns block")
                continue
            try:
                spki = load_der_public_key(pub)
            except Exception as e:
                problems.append("public key not parseable: %s" % e)
                continue

            doff = 0
            digests, doff = lp(signed_data, doff)
            certs, doff = lp(signed_data, doff)
            attrs, doff = lp(signed_data, doff)
            # (AOSP reads exactly three fields; no exhaustiveness requirement)

            # digest entries: each lp(entry), entry = u32 alg + lp(digest)
            content = data[:block_start]
            cd_bytes = data[cd_offset:eocd]
            segments = [content, cd_bytes, bytes(eocd_bytes)]

            verified_digest = False
            eoff = 0
            while eoff < len(digests):
                entry, eoff = lp(digests, eoff)
                alg = u32(entry, 0)
                d, _ = lp(entry, 4)
                if alg == ALG_SHA256:
                    if compute_content_digest(segments, hashlib.sha256) == d:
                        verified_digest = True
                    else:
                        problems.append("SHA-256 content digest mismatch")
                elif alg == ALG_SHA512:
                    if compute_content_digest(segments, hashlib.sha512) == d:
                        verified_digest = True
                    else:
                        problems.append("SHA-512 content digest mismatch")
            if not verified_digest:
                problems.append("no valid content digest")

            # certs
            coff = 0
            ncert = 0
            while coff < len(certs):
                der, coff = lp(certs, coff)
                try:
                    x509.load_der_x509_certificate(der)
                    ncert += 1
                except Exception as e:
                    problems.append("certificate not parseable: %s" % e)
            if ncert == 0:
                problems.append("no valid certificate")

            # signatures: sequence of lp(sig_entry); entry = u32 alg + lp(sig)
            sigoff = 0
            nsig = 0
            while sigoff < len(sigs):
                sig_entry, sigoff = lp(sigs, sigoff)
                alg = u32(sig_entry, 0)
                sig_bytes, _ = lp(sig_entry, 4)
                if alg not in (ALG_SHA256, ALG_SHA512):
                    continue
                h = hashes.SHA512() if alg == ALG_SHA512 else hashes.SHA256()
                try:
                    spki.verify(sig_bytes, signed_data, padding.PKCS1v15(), h)
                    nsig += 1
                except Exception as e:
                    problems.append("signature invalid (alg 0x%x): %s" % (alg, e))
            if nsig == 0:
                problems.append("no valid signature")
            else:
                signers_ok += 1

    if signers_ok == 0:
        problems.append("no fully valid signer")

    return report(problems)


def report(problems):
    if problems:
        print("INDEPENDENT VERIFY: FAIL")
        for p in problems:
            print("  -", p)
        return 1
    print("INDEPENDENT VERIFY: OK — block layout, pairs, signer structure,")
    print("  SPKI/certificate parse, AOSP content digest and RSA signature valid")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
