import gzip
import hashlib
import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('build_list', os.path.join(os.path.dirname(__file__), 'build_blocklist.py'))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

class MetadataTest(unittest.TestCase):
    def test_content_addressed_archive_and_manifest(self):
        names = {'d%06d.invalid' % i for i in range(200001)}
        with tempfile.TemporaryDirectory() as tmp:
            output = os.path.join(tmp, 'domains.bin')
            with patch.object(sys, 'argv', ['build_blocklist.py', output]), patch.object(builder, 'fetch', return_value=b'fixture'), patch.object(builder, 'hosts_names', return_value=names), patch.object(builder, 'ut1_names', return_value=set()):
                builder.main()
            with open(output, 'rb') as f: data = f.read()
            with open(os.path.join(tmp, 'blocklist.json')) as f: meta = json.load(f)
            self.assertEqual(meta['schema'], 1)
            self.assertEqual(meta['sha256'], hashlib.sha256(data).hexdigest())
            self.assertEqual(meta['size'], len(data))
            self.assertEqual(meta['names'], 200001)
            self.assertGreater(meta['builtAtEpochSeconds'], 1700000000)
            self.assertEqual(meta['file'], 'domains-' + meta['sha256'] + '.bin')
            with open(os.path.join(tmp, meta['file']), 'rb') as f: self.assertEqual(f.read(), data)
            self.assertEqual(len(gzip.decompress(data).splitlines()), 200001)

    def test_bad_source_keeps_existing_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = os.path.join(tmp, 'domains.bin')
            manifest = os.path.join(tmp, 'blocklist.json')
            with open(manifest, 'w') as f: f.write('previous-good')
            with patch.object(sys, 'argv', ['build_blocklist.py', output]), patch.object(builder, 'fetch', return_value=b'bad'), patch.object(builder, 'hosts_names', return_value=set()), patch.object(builder, 'ut1_names', return_value=set()):
                with self.assertRaises(SystemExit): builder.main()
            with open(manifest) as f: self.assertEqual(f.read(), 'previous-good')
            self.assertFalse(os.path.exists(output))

if __name__ == '__main__': unittest.main()
