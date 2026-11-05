#!/usr/bin/env python3
"""Build deterministic offline index from pinned DB-IP Lite data; no runtime download."""
import csv,gzip,hashlib,ipaddress,json,pathlib,struct,sys
SOURCE_SHA='e5c8459d638a272ece7f4d7cfa49dce18d91b4f403f6940ba9249bf0db3781fd'
def convert(source,dest):
 p=pathlib.Path(source)
 if hashlib.sha256(p.read_bytes()).hexdigest()!=SOURCE_SHA:raise ValueError('Dataset checksum mismatch')
 groups={4:bytearray(),6:bytearray()};counts={4:0,6:0};prev={4:-1,6:-1}
 with gzip.open(p,'rt') as f:
  for row in csv.reader(f):
   if len(row)!=3:raise ValueError('Bad CSV row')
   a,b=map(ipaddress.ip_address,row[:2]);cc=row[2]
   if a.version!=b.version or int(a)>int(b) or int(a)<=prev[a.version]:raise ValueError('Invalid range order')
   if len(cc)!=2 or not cc.isascii() or not cc.isalpha() or not cc.isupper():raise ValueError('Invalid country')
   prev[a.version]=int(b);counts[a.version]+=1;groups[a.version]+=a.packed+b.packed+cc.encode('ascii')
 raw=b'NCG1'+struct.pack('>II',counts[4],counts[6])+groups[4]+groups[6]
 dest=pathlib.Path(dest);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(gzip.compress(raw,mtime=0))
 print(json.dumps({'ranges':counts,'raw_bytes':len(raw),'compressed_bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'raw_sha256':hashlib.sha256(raw).hexdigest()}))
if __name__=='__main__':convert(sys.argv[1],sys.argv[2])
