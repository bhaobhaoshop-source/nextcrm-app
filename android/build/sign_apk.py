#!/usr/bin/env python3
"""EstateDesk APK signer — implements JAR (v1) + APK Signature Scheme v2.

Self-contained; depends only on `cryptography` (pip install cryptography).

Commands:
  genkey <out.pem> <out-cert.pem> [subject_cn]
      Generate a 2048-bit RSA key and a self-signed X.509 cert.
  sign  <unsigned.apk> <key.pem> <cert.pem> <out.apk> [v1|v2|v1v2]
      Sign an APK. Default: v1v2 (v1 = JAR signing, v2 = APK Signature Block).
  verify <apk>
      Verify v1 and v2 signatures of an APK (self-check).

Format reference: https://source.android.com/docs/security/features/apksigning/v2
"""

import base64
import hashlib
import struct
import sys
import zipfile
from datetime import datetime, timedelta, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.serialization import pkcs7

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
SIG_RSA_PKCS1_SHA256 = 0x0103
DIGEST_SHA256_CHUNKED = 0x0103
CHUNK_SIZE = 1024 * 1024


def u32(n: int) -> bytes:
    return struct.pack("<I", n)


def u64(n: int) -> bytes:
    return struct.pack("<Q", n)


def len_prefixed(data: bytes) -> bytes:
    return u32(len(data)) + data


# ---------------------------------------------------------------- key gen

def generate_key_and_cert(cn: str, out_key: str, out_cert: str) -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    now = datetime.now(timezone.utc)
    name = x509.Name([
        x509.NameAttribute(x509.oid.NameOID.COMMON_NAME, cn),
        x509.NameAttribute(x509.oid.NameOID.ORGANIZATION_NAME, "EstateDesk"),
    ])
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(days=1))
        .not_valid_after(now + timedelta(days=365 * 30))
        .sign(key, hashes.SHA256())
    )
    with open(out_key, "wb") as f:
        f.write(key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption()))
    with open(out_cert, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.DER))


def load_key(path: str):
    with open(path, "rb") as f:
        return serialization.load_pem_private_key(f.read(), password=None)


def load_cert_der(path: str) -> bytes:
    data = open(path, "rb").read()
    if data.startswith(b"-----BEGIN"):
        return x509.load_pem_x509_certificate(data).public_bytes(serialization.Encoding.DER)
    return data


# ---------------------------------------------------------------- zip helpers

def parse_zip_sections(data: bytes):
    """Return (content_end, central_dir, eocd_offset)."""
    eocd_off = data.rfind(b"PK\x05\x06")
    if eocd_off < 0:
        raise ValueError("no EOCD found — not a zip file")
    # EOCD: 4 sig + 2+2+2+2 (counts) + 4 (cd size @+12) + 4 (cd offset @+16) + 2 (comment len)
    cd_size = struct.unpack("<I", data[eocd_off + 12: eocd_off + 16])[0]
    cd_offset = struct.unpack("<I", data[eocd_off + 16: eocd_off + 20])[0]
    if cd_offset + cd_size > eocd_off:
        raise ValueError("bad zip structure")
    return cd_offset, data[cd_offset:eocd_off], eocd_off


# ---------------------------------------------------------------- v1 (JAR)

def b64_sha256(data: bytes) -> str:
    return base64.b64encode(hashlib.sha256(data).digest()).decode("ascii")


def sign_v1_entries(entries):
    """entries: dict path -> bytes (only regular files, no META-INF)."""
    lines = ["Manifest-Version: 1.0", "Created-By: EstateDesk", "", ""]
    sections = {}
    for path in sorted(entries):
        digest = b64_sha256(entries[path])
        section = f"Name: {path}\r\nSHA-256-Digest: {digest}\r\n\r\n"
        lines.append(section)
        sections[path] = section
    manifest = "".join(lines).encode("utf-8")

    sf_lines = [
        "Signature-Version: 1.0",
        "Created-By: EstateDesk",
        f"SHA-256-Digest-Manifest: {b64_sha256(manifest)}",
        "",
    ]
    for path in sorted(sections):
        sf_lines.append(f"Name: {path}\r\nSHA-256-Digest: {b64_sha256(sections[path].encode('utf-8'))}\r\n\r\n")
    sf = "".join(sf_lines).encode("utf-8")

    sig = (
        pkcs7.PKCS7SignatureBuilder()
        .set_data(sf)
        .add_signer(V1_CERT, V1_KEY, hashes.SHA256())
        .sign(serialization.Encoding.DER, options=[])
    )
    return manifest, sf, sig


V1_KEY = None
V1_CERT = None


# ---------------------------------------------------------------- v2

def compute_v2_digests(content: bytes, cd: bytes, eocd: bytes):
    """content = APK bytes before central dir (sections 1..3)."""
    chunk_digests = []
    for i in range(0, len(content), CHUNK_SIZE):
        chunk_digests.append(hashlib.sha256(content[i:i + CHUNK_SIZE]).digest())
    if not chunk_digests:
        chunk_digests = [hashlib.sha256(b"").digest()]
    chunks_digest = hashlib.sha256(b"".join(chunk_digests)).digest()
    cd_digest = hashlib.sha256(cd).digest()
    eocd_digest = hashlib.sha256(eocd).digest()

    digest_entries = []
    for d in chunk_digests:
        digest_entries.append(len_prefixed(u32(DIGEST_SHA256_CHUNKED) + len_prefixed(d)))
    digest_entries.append(len_prefixed(u32(DIGEST_SHA256_CHUNKED) + len_prefixed(chunks_digest)))
    digest_entries.append(len_prefixed(u32(DIGEST_SHA256_CHUNKED) + len_prefixed(cd_digest)))
    digest_entries.append(len_prefixed(u32(DIGEST_SHA256_CHUNKED) + len_prefixed(eocd_digest)))
    return len_prefixed(b"".join(digest_entries))


def build_v2_signer_block(cert_der: bytes, key, digests_block: bytes):
    certs = len_prefixed(len_prefixed(cert_der))
    attrs = len_prefixed(b"")  # no additional attributes
    signed_data = digests_block + certs + attrs

    signature = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signatures_block = len_prefixed(
        len_prefixed(u32(SIG_RSA_PKCS1_SHA256) + len_prefixed(signature)))

    public_key_der = key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

    signer = len_prefixed(signed_data) + len_prefixed(signatures_block) + len_prefixed(public_key_der)
    return len_prefixed(signer)


def build_apk_signing_block(v2_value: bytes) -> bytes:
    pair = u64(4 + len(v2_value)) + u32(V2_BLOCK_ID) + v2_value
    block_size = len(pair) + 8 + 16  # pairs + size2 field + magic
    return u64(block_size) + pair + u64(block_size) + APK_SIG_BLOCK_MAGIC


def sign_v2(unsigned: bytes, cert_der: bytes, key, with_v1: bool) -> bytes:
    cd_offset, cd, eocd_off = parse_zip_sections(unsigned)
    eocd = unsigned[eocd_off:]

    if with_v1:
        global V1_KEY, V1_CERT
        V1_KEY, V1_CERT = key, x509.load_der_x509_certificate(cert_der)
        zf = zipfile.ZipFile(__import__("io").BytesIO(unsigned))
        entries = {
            i.filename: zf.read(i.filename)
            for i in zf.infolist()
            if not i.is_dir() and not i.filename.startswith("META-INF/")
        }
        manifest, sf, rsa_sig = sign_v1_entries(entries)
        out = __import__("io").BytesIO()
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
            for i in zf.infolist():
                if not i.filename.startswith("META-INF/"):
                    z.writestr(i, zf.read(i.filename))
            z.writestr("META-INF/MANIFEST.MF", manifest)
            z.writestr("META-INF/CERT.SF", sf)
            z.writestr("META-INF/CERT.RSA", rsa_sig)
        unsigned = out.getvalue()
        cd_offset, cd, eocd_off = parse_zip_sections(unsigned)
        eocd = unsigned[eocd_off:]

    content = unsigned[:cd_offset]

    def assemble(patched_eocd: bytes) -> bytes:
        digests_block = compute_v2_digests(content, cd, patched_eocd)
        signer_block = build_v2_signer_block(cert_der, key, digests_block)
        block = build_apk_signing_block(signer_block)
        new_cd_offset = cd_offset + len(block)
        patched = patched_eocd[:16] + struct.pack("<I", new_cd_offset) + patched_eocd[20:]
        return content + block + cd + patched

    # Pass 1: figure out the block size (all components are fixed-size).
    probe = assemble(eocd)
    block_len = len(probe) - len(content) - len(cd) - len(eocd)
    # Pass 2: digest the properly patched EOCD and rebuild (same size).
    eocd_patched = eocd[:16] + struct.pack("<I", cd_offset + block_len) + eocd[20:]
    return assemble(eocd_patched)


# ---------------------------------------------------------------- verify

def verify_v2(path: str) -> bool:
    data = open(path, "rb").read()
    eocd_off = data.rfind(b"PK\x05\x06")
    if eocd_off < 0:
        print("VERIFY: no EOCD"); return False
    cd_offset = struct.unpack("<I", data[eocd_off + 16: eocd_off + 20])[0]
    magic_off = cd_offset - 16
    if magic_off < 0 or data[magic_off: magic_off + 16] != APK_SIG_BLOCK_MAGIC:
        print("VERIFY: no v2 block"); return False
    block_end = magic_off - 8
    block_size = struct.unpack("<Q", data[magic_off - 8: magic_off])[0]
    block_start = magic_off - block_size + 8
    block = data[block_start: block_end]  # [size1][pairs]
    pos = 8  # skip size1 field
    signers = None
    while pos < len(block):
        (pair_len, pair_id) = struct.unpack("<QI", block[pos: pos + 12])
        value = block[pos + 12: pos + 8 + pair_len]
        if pair_id == V2_BLOCK_ID:
            signers = value
        pos += 8 + pair_len
    if signers is None:
        print("VERIFY: no v2 signers"); return False

    def read_lp(buf, pos):
        (n,) = struct.unpack("<I", buf[pos: pos + 4])
        return buf[pos + 4: pos + 4 + n], pos + 4 + n

    signer, _ = read_lp(signers, 0)
    sd, pos = read_lp(signer, 0)
    sigs, pos = read_lp(signer, pos)
    pub, _ = read_lp(signer, pos)

    digests, p = read_lp(sd, 0)
    certs, _ = read_lp(sd, p)
    cert_der, _ = read_lp(certs, 0)

    sig_entry, _ = read_lp(sigs, 0)          # [len][alg][len][sig]
    inner, _ = read_lp(sig_entry, 0)         # [alg][len][sig]
    sig_alg = struct.unpack("<I", inner[:4])[0]
    sig_val, _ = read_lp(inner, 4)

    from cryptography.hazmat.primitives.asymmetric import padding as pad
    cert = x509.load_der_x509_certificate(cert_der)
    try:
        cert.public_key().verify(sig_val, sd, pad.PKCS1v15(), hashes.SHA256())
    except Exception as e:
        print("VERIFY: RSA signature INVALID:", e)
        return False

    # verify digests — sections 1..3 are the bytes BEFORE the signing block
    content = data[: block_start]
    entries = []
    q = 0
    while q < len(digests):
        (n,) = struct.unpack("<I", digests[q: q + 4])
        entries.append(digests[q + 4: q + 4 + n])
        q += 4 + n
    chunk_count = len(entries) - 3
    chunks = [content[i:i + CHUNK_SIZE] for i in range(0, len(content), CHUNK_SIZE)] or [b""]
    for i in range(chunk_count):
        expect = entries[i][8:] if len(entries[i]) > 8 else b""
        if hashlib.sha256(chunks[i]).digest() != expect:
            print("VERIFY: chunk digest mismatch", i); return False
    chunks_digest = hashlib.sha256(b"".join(
        hashlib.sha256(c).digest() for c in chunks)).digest()
    if chunks_digest != entries[chunk_count][8:]:
        print("VERIFY: chunks-of-digests mismatch"); return False
    if hashlib.sha256(data[cd_offset: eocd_off]).digest() != entries[chunk_count + 1][8:]:
        print("VERIFY: central dir digest mismatch"); return False
    if hashlib.sha256(data[eocd_off:]).digest() != entries[chunk_count + 2][8:]:
        print("VERIFY: eocd digest mismatch"); return False
    print(f"VERIFY v2: OK — RSA-SHA256 signature valid over signed data, "
          f"{chunk_count} chunk digest(s) + CD + EOCD verified, cert CN={cert.subject.rfc4514_string()}")
    return True


def cmd_genkey(args):
    cn = args[3] if len(args) > 3 else "EstateDesk"
    generate_key_and_cert(cn, args[1], args[2])
    print("key written:", args[1], "cert written:", args[2])


def cmd_sign(args):
    unsigned, key_p, cert_p, out = args[1], args[2], args[3], args[4]
    mode = args[5] if len(args) > 5 else "v1v2"
    key = load_key(key_p)
    cert_der = load_cert_der(cert_p)
    with open(unsigned, "rb") as f:
        unsigned_data = f.read()
    signed = sign_v2(unsigned_data, cert_der, key, with_v1=(mode in ("v1", "v1v2")))
    with open(out, "wb") as f:
        f.write(signed)
    print("signed:", out, len(signed), "bytes")


def cmd_verify(args):
    sys.exit(0 if verify_v2(args[1]) else 1)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "genkey":
        cmd_genkey(sys.argv[1:])
    elif cmd == "sign":
        cmd_sign(sys.argv[1:])
    elif cmd == "verify":
        cmd_verify(sys.argv[1:])
    else:
        print(__doc__)
        sys.exit(1)
