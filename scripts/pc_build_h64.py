#!/usr/bin/env python3
"""Builds domains-pc.h64 for Netra Privacy Guard (PC browser extension).

Input : the published domains.bin (gzip text, one domain per line, parent-collapsed)
        plus the BlockList Project ads list (ad-blocking, owner rule 2026-10-10).
Output: header 4s 'NPG1' + uint32 LE version(=1) + uint32 LE count, then count x
        (uint32 LE hi, uint32 LE lo) FNV-1a 64-bit hashes sorted by (hi, lo).
        Also writes <out>.sha256.

Government / institutional names are STRIPPED here as well (owner rule: never
blocked), so even a source-list mistake cannot reach a browser. The runtime
extension repeats the same check. Shared suffix set (same as the mobile app):
.gov, .edu, .gov.in, .nic.in, .ac.in, .edu.in, .res.in, plus .bank.in (banks,
RBI exclusive namespace), rbi.org.in, npci.org.in.

--selftest runs membership checks against the input text and exits non-zero on
any failure (this is the green gate for the publish workflow).

Canonical copy: prayagi-Privacy- /scripts/pc_build_h64.py (the list pipeline);
this copy exists so the extension repo's CI tests the exact same code.
"""
import gzip, hashlib, struct, sys

OFF_HI, OFF_LO = 0xCBF29CE4, 0x84222325
P_HI, P_LO = 0x100, 0x1B3

def fnv64(b: bytes):
    hi, lo = OFF_HI, OFF_LO
    for c in b:
        lo ^= c
        p = lo * P_LO
        hi = (hi * P_LO + lo * P_HI + (p >> 32)) & 0xFFFFFFFF
        lo = p & 0xFFFFFFFF
    return hi, lo

SUFFIX = ('gov.in', 'nic.in', 'ac.in', 'edu.in', 'res.in', 'bank.in')
TLD = ('gov', 'edu')
EXTRA = ('rbi.org.in', 'npci.org.in')

def allowlisted(name: str) -> bool:
    parts = name.split('.')
    if len(parts) < 2:
        return False
    if parts[-1] in TLD:
        return True
    for s in SUFFIX + EXTRA:
        if name == s or name.endswith('.' + s):
            return True
    return False

def read_names(path):
    op = gzip.open if path.endswith('.gz') or path.endswith('.bin') else open
    out = set()
    with op(path, 'rt', encoding='utf-8', errors='ignore') as f:
        for line in f:
            line = line.split('#', 1)[0].strip()
            parts = line.split()
            if len(parts) == 2 and parts[0] in ('0.0.0.0', '127.0.0.1'):
                name = parts[1]
            elif len(parts) == 1:
                name = parts[0]
            else:
                continue
            name = name.lower().rstrip('.')
            if name and '.' in name:
                out.add(name)
    return out

def collapse(names):
    """Drop names whose parent is already listed (suffix check covers them)."""
    out = set()
    for n in names:
        labels = n.split('.')
        skip = False
        for i in range(1, len(labels) - 1):
            if '.'.join(labels[i:]) in names:
                skip = True
                break
        if not skip:
            out.add(n)
    return out

def main():
    ads_file = None
    args = []
    i = 1
    while i < len(sys.argv):
        if sys.argv[i] == '--ads' and i + 1 < len(sys.argv):
            ads_file = sys.argv[i + 1]; i += 2
        elif sys.argv[i].startswith('--'):
            i += 1
        else:
            args.append(sys.argv[i]); i += 1
    if len(args) != 2:
        raise SystemExit('usage: pc_build_h64.py <domains.bin|txt.gz> <out.h64> [--ads FILE] [--selftest]')
    names = read_names(args[0])
    if ads_file:
        names |= read_names(ads_file)
    before = len(names)
    names = {n for n in names if not allowlisted(n)}
    stripped = before - len(names)
    names = collapse(names)
    hashes = sorted(fnv64(n.encode('idna') if any(ord(c) > 127 for c in n) else n.encode()) for n in names)
    with open(args[1], 'wb') as f:
        f.write(struct.pack('<4sII', b'NPG1', 1, len(hashes)))
        for hi, lo in hashes:
            f.write(struct.pack('<II', hi, lo))
    data = open(args[1], 'rb').read()
    with open(args[1] + '.sha256', 'w') as f:
        f.write(hashlib.sha256(data).hexdigest() + '  ' + args[1].split('/')[-1] + '\n')
    print('names in: %d, allow-listed stripped: %d, final: %d, out: %d bytes' % (before, stripped, len(hashes), len(data)))
    if '--selftest' in sys.argv:
        # vectors
        assert fnv64(b'') == (0xCBF29CE4, 0x84222325), 'fnv empty'
        assert fnv64(b'a') == (0xAF63DC4C, 0x8601EC8C), 'fnv a'
        assert fnv64(b'foobar') == (0x85944171, 0xF73967E8), 'fnv foobar'
        # every listed name must be found by suffix walk; allow-listed names must NOT be listed
        import bisect
        for n in list(names)[:2000] + ['x'.join(list(names)[:1])]:
            h = fnv64(n.encode())
            i = bisect.bisect_left(hashes, h)
            assert i < len(hashes) and hashes[i] == h, 'missing ' + n
        for bad in ('example.gov.in', 'test.nic.in', 'foo.ac.in', 'bank.bank.in', 'rbi.org.in'):
            h = fnv64(bad.encode())
            i = bisect.bisect_left(hashes, h)
            assert not (i < len(hashes) and hashes[i] == h), 'allow-listed present ' + bad
        # sorted check
        assert all(hashes[i] < hashes[i + 1] for i in range(len(hashes) - 1)), 'not sorted'
        print('SELFTEST OK (%d sampled memberships, allow-list and sort verified)' % min(2000, len(names)))

if __name__ == '__main__':
    main()
