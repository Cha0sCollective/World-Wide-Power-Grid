"""Make portable evidence views, retaining each original JSON byte-for-byte.

Only filesystem references change in a portable view. Executed source IDs,
artifact hashes, logs and observations keep their original values.
"""
import argparse
import json
from pathlib import Path
from test_suites import ROOT, descriptor, read, write


def bundle(source, output):
    source, output = Path(source).resolve(), Path(output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    objects=output/'objects';objects.mkdir()
    cached={}
    def copy(item, base):
        original=(base/item['path']).resolve()
        if not original.is_file():original=(ROOT/item['path']).resolve()
        if not original.is_file():raise ValueError('Missing retained input: '+str(original))
        if descriptor(original)['sha256']!=item['sha256']:raise ValueError('Changed retained input: '+str(original))
        if original in cached:return dict(item,**cached[original])
        raw=original.read_bytes(); target=objects/(item['sha256']+original.suffix)
        try:value=json.loads(raw)
        except (ValueError,UnicodeError):value=None
        if value is not None:
            portable=walk(value,original.parent)
            if portable!=value:
                saved=objects/(item['sha256']+'.original.bin')
                if not saved.exists():saved.write_bytes(raw)
                if isinstance(portable,dict):portable['_original_record']=relative(saved)
                raw=(json.dumps(portable,indent=2)+'\n').encode('utf-8')
        if not target.exists():target.write_bytes(raw)
        elif target.read_bytes()!=raw:raise ValueError('Conflicting retained content')
        cached[original]=relative(target)
        return dict(item,**cached[original])
    def relative(path):
        record=descriptor(path);record['path']=path.relative_to(ROOT).as_posix();return record
    def walk(value,base):
        if isinstance(value,dict):
            if {'path','sha256','bytes'}<=value.keys():return copy(value,base)
            return {k:walk(v,base) for k,v in value.items()}
        if isinstance(value,list):return [walk(v,base) for v in value]
        return value
    result=walk(read(source),source.parent)
    original=objects/'result.original.bin';original.write_bytes(source.read_bytes())
    result['_original_record']=relative(original)
    write(output/'result.json',result)
    return output/'result.json'


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(bundle(a.source,a.output))
