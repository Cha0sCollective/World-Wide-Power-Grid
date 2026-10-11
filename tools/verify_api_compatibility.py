"""Snapshot the published Java API or compare it with an actual previous artifact.

No dependencies or installed JDK are required. This inspects classfile contracts,
not method bodies. Semantic/electrical compatibility still requires the packaged
previously compiled example and gameplay acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL = 0x1, 0x2, 0x4, 0x8, 0x10
INTERFACE, ABSTRACT, ANNOTATION, ENUM = 0x200, 0x400, 0x2000, 0x4000
EXPORT_MASK = PUBLIC | PROTECTED
CLASS_MASK = INTERFACE | ABSTRACT | ANNOTATION | ENUM | FINAL | STATIC
MEMBER_MASK = STATIC | FINAL | ABSTRACT
DEFAULT_PREFIX = 'org/cha0scollective/wwpg/api/'


class ContractError(ValueError):
    pass


class Reader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, count):
        end = self.offset + count
        if end > len(self.data):
            raise ContractError('Truncated classfile')
        result = self.data[self.offset:end]
        self.offset = end
        return result

    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack('>H', self.take(2))[0]
    def u4(self): return struct.unpack('>I', self.take(4))[0]


def parse_class(data):
    reader = Reader(data)
    if reader.u4() != 0xcafebabe:
        raise ContractError('Invalid classfile magic')
    reader.u2()
    major = reader.u2()
    pool = [None] * reader.u2()
    i = 1
    while i < len(pool):
        tag = reader.u1()
        if tag == 1:
            raw = reader.take(reader.u2()).replace(b'\xc0\x80', b'\0')
            pool[i] = (tag, raw.decode('utf-8', errors='surrogatepass'))
        elif tag in (3, 4): pool[i] = (tag, reader.take(4).hex())
        elif tag in (5, 6):
            pool[i] = (tag, reader.take(8).hex())
            i += 1
        elif tag in (7, 8, 16, 19, 20): pool[i] = (tag, reader.u2())
        elif tag in (9, 10, 11, 12, 17, 18): pool[i] = (tag, reader.u2(), reader.u2())
        elif tag == 15: pool[i] = (tag, reader.u1(), reader.u2())
        else: raise ContractError(f'Unsupported constant pool tag {tag}')
        i += 1

    def utf(index):
        if not index or pool[index][0] != 1: raise ContractError('Expected UTF8 constant')
        return pool[index][1]

    def class_name(index):
        return utf(pool[index][1]) if index else None

    def attributes(source):
        result = {}
        for _ in range(source.u2()):
            name = utf(source.u2())
            contents = source.take(source.u4())
            if name in result: raise ContractError(f'Duplicate {name} attribute')
            result[name] = contents
        return result

    def members():
        result = {}
        for _ in range(reader.u2()):
            flags, name, descriptor = reader.u2(), utf(reader.u2()), utf(reader.u2())
            attrs = attributes(reader)
            if not flags & EXPORT_MASK: continue
            entry = dict(name=name, descriptor=descriptor, access=flags & EXPORT_MASK,
                         flags=flags & MEMBER_MASK)
            if 'Signature' in attrs: entry['signature'] = utf(Reader(attrs['Signature']).u2())
            if 'ConstantValue' in attrs:
                constant = pool[Reader(attrs['ConstantValue']).u2()]
                entry['constant'] = [constant[0], utf(constant[1]) if constant[0] == 8 else constant[1]]
            if 'Exceptions' in attrs:
                exceptions = Reader(attrs['Exceptions'])
                entry['exceptions'] = sorted(class_name(exceptions.u2()) for _ in range(exceptions.u2()))
            result[name + descriptor] = entry
        return result

    flags, this_index, super_index = reader.u2(), reader.u2(), reader.u2()
    name = class_name(this_index)
    interfaces = sorted(class_name(reader.u2()) for _ in range(reader.u2()))
    fields, methods = members(), members()
    attrs = attributes(reader)
    # A protected nested class has no protected class access bit. Its actual
    # Java visibility is recorded by InnerClasses, including enclosing types.
    enclosing = None
    if 'InnerClasses' in attrs:
        inner = Reader(attrs['InnerClasses'])
        for _ in range(inner.u2()):
            index, outer, _simple, access = inner.u2(), inner.u2(), inner.u2(), inner.u2()
            if index == this_index:
                flags = (flags & ~(EXPORT_MASK | PRIVATE | STATIC)) | (access & (EXPORT_MASK | PRIVATE | STATIC))
                enclosing = class_name(outer)
    result = dict(name=name, access=flags & EXPORT_MASK, flags=flags & CLASS_MASK,
                  super=class_name(super_index), interfaces=interfaces, fields=fields,
                  methods=methods, major_version=major)
    if enclosing: result['enclosing'] = enclosing
    if 'Signature' in attrs: result['signature'] = utf(Reader(attrs['Signature']).u2())
    if 'PermittedSubclasses' in attrs:
        permits = Reader(attrs['PermittedSubclasses'])
        result['permitted_subclasses'] = sorted(class_name(permits.u2()) for _ in range(permits.u2()))
    if reader.offset != len(data): raise ContractError('Trailing classfile data')
    return result


def snapshot(jar, prefixes=(DEFAULT_PREFIX,)):
    jar = Path(jar)
    types = {}
    with zipfile.ZipFile(jar) as archive:
        if archive.testzip() is not None: raise ContractError(f'Corrupt artifact: {jar}')
        names = archive.namelist()
        if len(names) != len(set(names)): raise ContractError('Duplicate JAR entries')
        for name in names:
            if name.startswith('META-INF/versions/') and name.endswith('.class'):
                raise ContractError('Multi-release contracts require an explicit target; not supported by API 1.0')
            if not name.endswith('.class') or not any(name.startswith(p) for p in prefixes): continue
            parsed = parse_class(archive.read(name))
            if parsed['name'] + '.class' != name: raise ContractError('Class name does not match JAR entry')
            types[parsed['name']] = parsed
    # Members of a private/package-private enclosing class are not exported.
    def exported(name, visiting=frozenset()):
        if name in visiting: raise ContractError('Cyclic enclosing class declaration')
        entry = types.get(name)
        if not entry: return True
        return bool(entry['access'] & EXPORT_MASK) and (not entry.get('enclosing') or
            exported(entry['enclosing'], visiting | {name}))
    public = {name: value for name, value in sorted(types.items()) if exported(name)}
    if not public: raise ContractError('Artifact contains no exported API classes')
    return dict(schema=1, artifact=dict(file=jar.name, sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                                      bytes=jar.stat().st_size), prefixes=list(prefixes), types=public)


def compare(previous, current):
    changes = []
    def add(kind, location, detail): changes.append(dict(kind=kind, location=location, detail=detail))
    for name, old in previous['types'].items():
        new = current['types'].get(name)
        if new is None:
            add('removed_type', name, 'Previously exported class/interface is absent or inaccessible')
            continue
        if old['access'] & PUBLIC and not new['access'] & PUBLIC:
            add('visibility', name, 'Public type became protected')
        if (old['flags'] ^ new['flags']) & (INTERFACE | ANNOTATION | ENUM):
            add('type_kind', name, 'Class/interface/annotation/enum kind changed')
        if (old['flags'] ^ new['flags']) & STATIC:
            add('nested_static', name, 'Static/instance nested-type ownership changed')
        if new['flags'] & (FINAL | ABSTRACT) & ~old['flags']:
            add('type_constraint', name, 'Type became final or abstract')
        for key in ('super', 'signature', 'permitted_subclasses'):
            if old.get(key) != new.get(key): add(key, name, 'Declared type contract changed')
        if not set(old['interfaces']) <= set(new['interfaces']):
            add('interfaces', name, 'Previously implemented/extended interface removed')
        if new['major_version'] > old['major_version']:
            add('java_target', name, 'Required Java classfile version increased')
        for category in ('fields', 'methods'):
            for key, member in old[category].items():
                location = name + '#' + key
                replacement = new[category].get(key)
                if replacement is None:
                    add('removed_member', location, 'Previously exported descriptor is absent or inaccessible')
                    continue
                if member['access'] & PUBLIC and not replacement['access'] & PUBLIC:
                    add('visibility', location, 'Public member became protected')
                if (member['flags'] ^ replacement['flags']) & STATIC:
                    add('static', location, 'Static/instance dispatch changed')
                if replacement['flags'] & (FINAL | ABSTRACT) & ~member['flags']:
                    add('member_constraint', location, 'Member became final or abstract')
                for attribute in ('constant', 'signature', 'exceptions'):
                    if member.get(attribute) != replacement.get(attribute):
                        add(attribute, location, 'Published constant/source contract changed')
            if category == 'methods':
                for key, member in new[category].items():
                    if key not in old[category] and member['flags'] & ABSTRACT and not member['flags'] & STATIC:
                        add('new_abstract_method', name + '#' + key,
                            'Existing third-party implementors cannot implement this added obligation')
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('current', type=Path)
    parser.add_argument('--previous', type=Path, help='Actual earlier published API JAR; omit for first API release')
    parser.add_argument('--api-version', required=True)
    parser.add_argument('--prefix', action='append', help='Slash-delimited package prefix; default WWPG public API')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prefixes = tuple(p.replace('.', '/').rstrip('/') + '/' for p in args.prefix) if args.prefix else (DEFAULT_PREFIX,)
    try:
        current = snapshot(args.current, prefixes)
        previous = snapshot(args.previous, prefixes) if args.previous else None
        changes = compare(previous, current) if previous else []
        report = dict(schema=1, api_version=args.api_version,
                      mode='comparison' if previous else 'first_release_snapshot',
                      compatible=not changes if previous else None,
                      previous=previous['artifact'] if previous else None,
                      changes=changes, current=current,
                      limits='Classfile contracts only; does not certify electrical semantics, default-method conflicts, or gameplay compatibility.')
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, ensure_ascii=True) + '\n', encoding='utf-8', newline='\n')
        print(json.dumps(dict(mode=report['mode'], compatible=report['compatible'], exported_types=len(current['types']),
                              changes=len(changes), report=str(args.output))))
        if changes: raise SystemExit(1)
    except (ContractError, OSError, zipfile.BadZipFile, UnicodeError, IndexError) as error:
        parser.exit(2, f'API artifact verification failed: {error}\n')


if __name__ == '__main__': main()
