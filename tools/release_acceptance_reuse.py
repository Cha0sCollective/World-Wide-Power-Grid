"""Validate carried-forward acceptance without rewriting its source or hashes."""
import json
import re
import struct
import zipfile


def runtime_equivalent(previous, current, old_version, new_version):
    """Only the single declared mod-version value may differ."""
    with zipfile.ZipFile(previous) as old, zipfile.ZipFile(current) as new:
        if len(old.namelist()) != len(set(old.namelist())) or set(old.namelist()) != set(new.namelist()):
            raise ValueError('Runtime entries differ')
        for name in old.namelist():
            expected = old.read(name)
            if name == 'META-INF/neoforge.mods.toml':
                before = ('version="' + old_version + '"').encode()
                after = ('version="' + new_version + '"').encode()
                if expected.count(before) != 1:
                    raise ValueError('Ambiguous previous mod version')
                expected = expected.replace(before, after, 1)
            if expected != new.read(name):
                raise ValueError('Runtime changed beyond its version: ' + name)


def without_line_numbers(data):
    """Mask only source-line values; preserve code, constants and all other attributes."""
    out = bytearray(data); pos = 8
    def u2(offset): return struct.unpack_from('>H', data, offset)[0]
    def u4(offset): return struct.unpack_from('>I', data, offset)[0]
    count = u2(pos); pos += 2; names = {}; index = 1
    lengths = {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            length = u2(pos); pos += 2; names[index] = data[pos:pos+length].decode('utf-8'); pos += length
        else:
            pos += lengths[tag]
            if tag in (5,6): index += 1
        index += 1
    def attributes(cursor, number):
        for _ in range(number):
            name = names[u2(cursor)]; length = u4(cursor+2); start = cursor+6
            if name == 'Code':
                nested = start+8+u4(start+4)
                nested += 2+8*u2(nested)
                attributes(nested+2,u2(nested))
            elif name == 'LineNumberTable':
                for item in range(u2(start)):
                    line = start+2+item*4+2; out[line:line+2] = b'\0\0'
            cursor = start+length
        return cursor
    pos += 6; pos += 2+2*u2(pos)
    for _ in range(2):
        number = u2(pos); pos += 2
        for _ in range(number):
            pos = attributes(pos+8,u2(pos+6))
    attributes(pos+2,u2(pos))
    return bytes(out)


def api_equivalent(previous, current):
    with zipfile.ZipFile(previous) as old, zipfile.ZipFile(current) as new:
        if set(old.namelist()) != set(new.namelist()): raise ValueError('API entries differ')
        for name in old.namelist():
            before, after = old.read(name), new.read(name)
            if name.endswith('.class'):
                before, after = without_line_numbers(before), without_line_numbers(after)
            if before != after: raise ValueError('API changed beyond source line numbers: '+name)


def validate(manifest, root, gate):
    reuse = manifest['acceptance_reuse']
    gate.require(reuse.get('schema') == 1 and reuse.get('scope') == 'runtime_version_only', 'Unsupported acceptance reuse')
    source = reuse['source_sha']; gate.require(re.fullmatch(r'[0-9a-f]{40}', source), 'Missing original accepted source')
    runtime = gate.locked_file(reuse['runtime'],root)
    current = gate.locked_file(manifest['artifacts']['runtime'],root)
    runtime_equivalent(runtime,current,reuse['runtime_version'],manifest['wwpg_version'])
    ci_runtime = gate.locked_file(reuse['ci_runtime'],root)
    runtime_equivalent(ci_runtime,current,reuse['ci_runtime_version'],manifest['wwpg_version'])
    hashes = {role: reuse[role]['sha256'] for role in ('runtime','api','cee_api','example','upstream_example')}
    for role in ('api','cee_api'):
        api_equivalent(gate.locked_file(reuse[role],root),gate.locked_file(manifest['artifacts'][role],root))
    for role in ('example','upstream_example'):
        gate.require(gate.sha(gate.locked_file(reuse[role],root)) == manifest['artifacts'][role]['sha256'], 'Example changed')
    expected = {(b,p,m) for b in gate.BACKENDS for p in gate.PROFILES for m in ('graphical','multiplayer')}
    seen = set()
    for item in reuse['clients']:
        proof = gate.accepted_record(item['proof'],root,hashes,source)
        key = (proof['backend'],proof['profile'],proof['mode'])
        gate.require(key in expected and key not in seen and proof.get('real_graphical_client') is True, 'Invalid original client coverage')
        gate.require(proof['world_sha256'] == item['world']['sha256'], 'Original client world differs')
        gate.locked_file(item['world'],root)
        if proof['mode'] == 'multiplayer':
            gate.require(proof.get('real_tcp_clients') is True and len(set(proof.get('real_clients',[])))>=2, 'Original TCP coverage missing')
        seen.add(key)
    gate.require(seen == expected, 'Original client matrix incomplete')
    for key in ('base_world','pinout_world','base_workshop_world','pinout_workshop_world'):
        previous = gate.locked_file(reuse['worlds'][key],root); current_world = gate.locked_file(manifest[key],root)
        with zipfile.ZipFile(previous) as old, zipfile.ZipFile(current_world) as new:
            old_files = {n.split('/',1)[1]:old.read(n) for n in old.namelist() if not n.endswith('/')}
            new_files = {n.split('/',1)[1]:new.read(n) for n in new.namelist() if not n.endswith('/')}
            gate.require(set(old_files)==set(new_files), 'Reused world entries differ')
            for name in old_files:
                if name not in {'WWPG_MANIFEST.json','WWPG_README.txt'}:
                    gate.require(old_files[name]==new_files[name], 'Native save changed: '+name)
            metadata=json.loads(new_files['WWPG_MANIFEST.json'])
            gate.require(metadata['target']==manifest['wwpg_version'] and metadata['jar']['sha256']==manifest['artifacts']['runtime']['sha256'], 'Derived world metadata differs')
            gate.require(metadata['files']==json.loads(old_files['WWPG_MANIFEST.json'])['files'], 'Native save inventory changed')
    ci_hashes = {role:manifest['artifacts'][role]['sha256'] for role in hashes}
    ci_hashes['runtime'] = reuse['ci_runtime']['sha256']
    return source, ci_hashes, reuse['ci_runtime_version']
