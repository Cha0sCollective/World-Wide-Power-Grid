"""Check save-completion logs; callers must also require a successful process exit."""
import re


def completed_world_save(text):
    messages = [line.rsplit(']: ', 1)[-1].strip() for line in text.splitlines()]
    stops = [index for index, message in enumerate(messages) if message == 'Stopping server']
    if not stops:
        raise ValueError('No actual server shutdown was recorded')
    shutdown = messages[stops[-1]:]
    if any(re.search(r'\b(failed to save|failed to write chunk|error saving|exception stopping the server)\b',
                     message, re.IGNORECASE) for message in shutdown):
        raise ValueError('World shutdown reported a save error')
    cursor = 0
    for marker in ('Saving players', 'Saving worlds'):
        try:
            cursor = shutdown.index(marker, cursor) + 1
        except ValueError:
            raise ValueError('Incomplete world shutdown: missing ' + marker) from None
    try:
        end = shutdown.index('ThreadedAnvilChunkStorage: All dimensions are saved', cursor)
    except ValueError:
        raise ValueError('World shutdown did not finish saving all dimensions') from None
    storages = []
    for message in shutdown[cursor:end]:
        match = re.fullmatch(r'ThreadedAnvilChunkStorage \((.+)\): All chunks are saved', message)
        if match:
            storages.append(match[1])
    if not storages:
        raise ValueError('World shutdown has no completed chunk-storage save')
    return dict(all_dimensions_saved=True, saved_chunk_storages=sorted(set(storages)))
