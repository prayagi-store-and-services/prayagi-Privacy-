#!/usr/bin/env python3
"""Builds assets/blocklist/domains.txt.gz for SensorGuard site blocking (build step, needs internet).

Sources (see docs/THIRD_PARTY_LISTS.md for licences and credits):
  - BlockList Project porn.txt and gambling.txt (MIT / Unlicense)       https://github.com/blocklistproject/Lists
  - BlockList Project phishing.txt, scam.txt, ransomware.txt (MIT), and malware.txt only when INCLUDE_BL_MALWARE=1 (2.6 million names, size decision pending)
  - UT1 blacklists gambling (and adult when INCLUDE_UT1_ADULT=1), CC BY-SA 4.0   https://dsi.ut-capitole.fr/blacklists/

Output: one domain per line, lower case, sorted, a domain is dropped when a parent of it is already listed
(the app blocks subdomains of every listed name). Also writes SOURCES.txt next to it with date, counts and sha256.
Usage: build_blocklist.py <output domains.txt.gz>
"""
import gzip, hashlib, io, os, sys, tarfile, urllib.request, datetime

BL = "https://raw.githubusercontent.com/blocklistproject/Lists/master/"
UT = "https://dsi.ut-capitole.fr/blacklists/download/"

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "SensorGuard-list-build"})
    last = None
    for _ in range(3):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except Exception as e:  # retry a few times, then fail the build loudly
            last = e
    raise SystemExit("download failed: %s (%s)" % (url, last))

def hosts_names(raw):
    out = set()
    for line in raw.decode("utf-8", "ignore").splitlines():
        line = line.split("#", 1)[0].strip()
        parts = line.split()
        if len(parts) == 2 and parts[0] in ("0.0.0.0", "127.0.0.1"):
            out.add(parts[1].lower().rstrip("."))
    return out

def ut1_names(raw, member):
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as t:
        data = t.extractfile(member).read().decode("utf-8", "ignore")
    return set(l.strip().lower().rstrip(".") for l in data.splitlines() if l.strip() and not l.startswith("#"))

def valid(n):
    if not n or "." not in n or len(n) > 253 or n.startswith(".") or ".." in n:
        return False
    if all(c.isdigit() or c == "." for c in n):
        return False
    return all(c.isalnum() or c in "-._" or ord(c) > 127 for c in n)

def main():
    out = sys.argv[1]
    sources = []
    names = set()
    files = ["porn.txt", "gambling.txt", "phishing.txt", "scam.txt", "ransomware.txt"]
    if os.environ.get("INCLUDE_BL_MALWARE") == "1":
        files.append("malware.txt")
    for fname in files:
        raw = fetch(BL + fname)
        s = hosts_names(raw)
        sources.append(("BlockList Project " + fname, BL + fname, hashlib.sha256(raw).hexdigest(), len(s)))
        names |= s
    raw = fetch(UT + "gambling.tar.gz")
    s = ut1_names(raw, "gambling/domains")
    sources.append(("UT1 gambling", UT + "gambling.tar.gz", hashlib.sha256(raw).hexdigest(), len(s)))
    names |= s
    if os.environ.get("INCLUDE_UT1_ADULT") == "1":
        raw = fetch(UT + "adult.tar.gz")
        s = ut1_names(raw, "adult/domains")
        sources.append(("UT1 adult", UT + "adult.tar.gz", hashlib.sha256(raw).hexdigest(), len(s)))
        names |= s
    names = {n for n in names if valid(n)}
    keep = []
    for n in names:
        p = n.split(".")
        if any(".".join(p[i:]) in names for i in range(1, len(p) - 1)):
            continue
        keep.append(n)
    keep.sort()
    if len(keep) < 200000:
        raise SystemExit("list looks too small (%d names): refusing to build" % len(keep))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with gzip.GzipFile(out, "wb", compresslevel=9, mtime=0) as g:
        g.write(("\n".join(keep) + "\n").encode("utf-8"))
    with open(os.path.join(os.path.dirname(out), "SOURCES.txt"), "w") as f:
        f.write("Built %s UTC. Names after merge and parent-reduction: %d\n" % (datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M"), len(keep)))
        for n, u, h, c in sources:
            f.write("%s | %s | sha256 %s | %d names\n" % (n, u, h, c))
    print("wrote %s with %d names" % (out, len(keep)))

main()
