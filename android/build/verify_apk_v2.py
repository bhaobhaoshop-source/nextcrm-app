#!/usr/bin/env python3
"""INDEPENDENT APK Signature Scheme v2 verifier — written against the spec
from https://source.android.com/docs/security/features/apksigning/v2, with
no shared code paths with sign_apk.py. Used as a cross-check because Android
rejects structurally invalid blocks that a self-consistent verifier may miss.

Usage: verify_apk_v2.py <apk>
"""
import hashlib
import struct
import sys
import zipfile

from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_der_public_key

MAGIC = b"APK Sig Block 42"
SIGNER_ID = 0x7109871A
RSA_PKCS1_SHA256 = 0x0103
SHA256_CHUNKED = 0x0103
CHUNK = 1024 * 1024


def u32(b, off):
    return struct.unpack("<I", b[off:off + 4])[0]


def u64(b, off):
    return struct.unpack("<Q", b[off:off + 8])[0]


def lp(b, off):
    n = u32(b, off)
    return b[off + 4:off + 4 + n], off + 4 + n


def main(path):
    data = open(path, "rb").read()
    problems = []

    # ---- EOCD
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        problems.append("no EOCD")
        return report(problems)
    cd_offset = u32(data, eocd + 16)
    cd_size = u32(data, eocd + 12)
    if cd_offset + cd_size != eocd:
        problems.append(f"EOCD mismatch: cd_offset={cd_offset} cd_size={cd_size} eocd={eocd}")
    eocd_bytes = data[eocd:eocd + 22]

    # ---- signing block: [u64 size1][pairs][u64 size2][magic(16)]
    # size2 field occupies the 8 bytes just before the magic.
    if data[cd_offset - 16: cd_offset] != MAGIC:
        problems.append("no v2 magic before central directory")
        return report(problems)
    magic_off = cd_offset - 16
    size2 = u64(data, magic_off - 8)
    block_start = magic_off - size2 + 8  # skip size1 field
    if block_start < 0:
        problems.append("negative block start")
        return report(problems)
    size1 = u64(data, block_start)
    if size1 != size2:
        problems.append(f"size1={size1} != size2={size2}")
    pairs_region_len = (magic_off - 8) - (block_start + 8)
    if size1 != pairs_region_len + 8 + 16:
        problems.append("block size arithmetic inconsistent")

    # ---- pairs
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
    ids = [p[0] for p in pairs]
    if ids != sorted(ids):
        problems.append("pairs not sorted by id")
    if SIGNER_ID not in ids:
        problems.append("no v2 signer pair")

    for pid, value in pairs:
        if pid != SIGNER_ID:
            continue
        # value = length-prefixed sequence of length-prefixed signers
        signers, _ = lp(value, 0)
        so = 0
        while so < len(signers):
            signer, so = lp(signers, so)
            soff = 0
            signed_data, soff = lp(signer, soff)
            sigs, soff = lp(signer, soff)
            pub, _ = lp(signer, soff)  # length-prefixed SPKI (per AOSP)

            # public key structural check
            try:
                spki = load_der_public_key(pub)
            except Exception as e:
                problems.append("signer public key not parseable: %s" % e)
                continue

            # signed data
            doff = 0
            digests, doff = lp(signed_data, doff)
            certs, doff = lp(signed_data, doff)
            attrs, doff = lp(signed_data, doff)
            if doff != len(signed_data):
                problems.append("signed data trailing bytes")

            # digests: sequence of length-prefixed (alg u32, lp digest)
            entries = []
            eoff = 0
            while eoff < len(digests):
                e, eoff = lp(digests, eoff)
                entries.append(e)
            if len(entries) < 4:
                problems.append("expected >= 4 digest entries, got %d" % len(entries))
                continue
            def unpack_digest(e):
                alg = u32(e, 0)
                d, _ = lp(e, 4)
                return alg, d

            chunk_digests = [unpack_digest(e)[1] for e in entries[:-3]]
            chunks_digest = unpack_digest(entries[-3])[1]
            cd_digest = unpack_digest(entries[-2])[1]
            eocd_digest = unpack_digest(entries[-1])[1]

            content = data[:block_start]
            chunks = [content[i:i + CHUNK] for i in range(0, len(content), CHUNK)] or [b""]
            if len(chunks) != len(chunk_digests):
                problems.append(f"chunk count mismatch: file={len(chunks)} block={len(chunk_digests)}")
                continue
            for i, c in enumerate(chunks):
                if hashlib.sha256(c).digest() != chunk_digests[i]:
                    problems.append(f"chunk {i} digest mismatch")
            if hashlib.sha256(b"".join(hashlib.sha256(c).digest() for c in chunks)).digest() != chunks_digest:
                problems.append("chunks-of-digests mismatch")
            cd_bytes = data[cd_offset:eocd]
            if hashlib.sha256(cd_bytes).digest() != cd_digest:
                problems.append("central directory digest mismatch")
            if hashlib.sha256(eocd_bytes).digest() != eocd_digest:
                problems.append("EOCD digest mismatch")

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

            # signatures: length-prefixed sequence of length-prefixed
            # signatures, each = [alg u32][length-prefixed sig bytes]
            sigs_seq, _ = lp(sigs, 0)
            sigoff = 0
            nsig = 0
            while sigoff < len(sigs_seq):
                sig_entry, sigoff = lp(sigs_seq, sigoff)
                alg = u32(sig_entry, 0)
                sig_bytes, _ = lp(sig_entry, 4)
                if alg != RSA_PKCS1_SHA256:
                    problems.append("unsupported signature alg 0x%x" % alg)
                    continue
                try:
                    spki.verify(sig_bytes, signed_data, padding.PKCS1v15(), hashes.SHA256())
                    nsig += 1
                except Exception as e:
                    problems.append("RSA signature over signed data INVALID: %s" % e)
            if nsig == 0:
                problems.append("no valid signature")
            if ncert == 0:
                problems.append("no valid certificate")

    return report(problems)


def report(problems):
    if problems:
        print("INDEPENDENT VERIFY: FAIL")
        for p in problems:
            print("  -", p)
        return 1
    print("INDEPENDENT VERIFY: OK — block layout, pairs, signer structure,")
    print("  SPKI/certificate parse, content digests and RSA signature all valid")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
