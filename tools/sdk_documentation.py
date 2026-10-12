"""Resolve future bundled Markdown against its actual contents and frozen source."""
import posixpath,re
from urllib.parse import urlsplit

REPOSITORY='https://github.com/Cha0sCollective/World-Wide-Power-Grid'

def bundle_markdown(text,source,included,version,source_sha):
    if not re.fullmatch(r'[a-f0-9]{40}',source_sha): raise ValueError('Missing frozen documentation source')
    def replace(match):
        target=match.group(1)
        if target.startswith('#') or target.startswith(('mailto:','https://wwpg.ch4os.live/')): return match.group(0)
        path,separator,fragment=target.partition('#')
        mutable=REPOSITORY+'/blob/main/'
        if path.startswith(mutable): absolute=path[len(mutable):]
        elif urlsplit(path).scheme: return match.group(0)
        else: absolute=posixpath.normpath(posixpath.join(posixpath.dirname(source),path))
        if absolute.startswith('../') or absolute.startswith('/'): raise ValueError('Documentation link escapes repository: '+target)
        suffix=('#'+fragment) if separator else ''
        if absolute in included:
            destination=posixpath.relpath(absolute,posixpath.dirname(source) or '.')+suffix
        elif absolute.startswith('release/'+version+'/'):
            destination=REPOSITORY+'/releases/download/'+version+'/wwpg-'+version+'-verification.zip'
        else:
            kind='tree' if not posixpath.splitext(absolute)[1] else 'blob'
            destination=REPOSITORY+'/'+kind+'/'+source_sha+'/'+absolute+suffix
        return ']('+destination+')'
    return re.sub(r'\]\(([^\s)]+)\)',replace,text)
