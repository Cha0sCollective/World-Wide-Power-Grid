"""Build isolated baseline/profile Windows libraries and focused safety executables."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def host_path(value):
    if re.match(r'^[A-Za-z]:[\\/]', value):
        value = '/mnt/' + value[0].lower() + '/' + value[3:].replace('\\','/')
    return Path(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--toolchain', required=True)
    parser.add_argument('--java-home', required=True)
    parser.add_argument('--jobs', type=int, default=2)
    args = parser.parse_args()
    folder = (ROOT / args.directory).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    prepared = json.loads((folder / 'prepared.json').read_text())
    instrumentation = json.loads((folder / 'instrumentation.json').read_text())
    assert instrumentation['all_original_source_restored'] and instrumentation['auxiliary_buffer_unchanged']
    for item in prepared['baseline_inventory'] + prepared['archives'] + prepared['patches'] + instrumentation['sources']:
        assert sha(host_path(item['path'])) == item['sha256'], item['path']
    toolchain_path = (ROOT / args.toolchain).resolve()
    toolchain = json.loads(toolchain_path.read_text())
    for item in toolchain['packages'] + toolchain['evidence']:
        assert sha(host_path(item['path'])) == item['sha256'], item['path']
    compiler = host_path(toolchain['compiler'])
    bindir = compiler.parent
    project, build = folder / 'cmake', folder / 'build'
    project.mkdir(exist_ok=False)
    assert not build.exists()
    base_cmake = (ROOT / 'tools/native/CMakeLists.txt').read_text()
    extra = '''
set(PGP_C "${PROFILE_PG_SOURCE}/native/src/powergridNative/c")
add_library(powergridNativeProfile SHARED "${PGP_C}/sparse.c" "${PGP_C}/solver.c"
    "${PGP_C}/mna.c" "${PGP_C}/exception_handler.c" "${PGP_C}/verify.c" "${PGP_C}/native_profile.c")
set_target_properties(powergridNativeProfile PROPERTIES OUTPUT_NAME powergridNativeProfile PREFIX "lib")
target_compile_options(powergridNativeProfile PRIVATE -mavx2)
set_source_files_properties("${PGP_C}/exception_handler.c" "${PGP_C}/verify.c" PROPERTIES COMPILE_OPTIONS -mno-avx2)
target_include_directories(powergridNativeProfile PRIVATE
    "${PROFILE_PG_SOURCE}/native/src/powergridNative/include" "${JNI_INCLUDE}" "${JNI_PLATFORM_INCLUDE}")
target_link_libraries(powergridNativeProfile PRIVATE superlu openblas_static)
target_link_options(powergridNativeProfile PRIVATE -static-libgcc -static)

add_executable(profile_sparse_safety_tests "${CMAKE_CURRENT_SOURCE_DIR}/profile_sparse_safety_tests.c"
    "${PGP_C}/sparse.c" "${PGP_C}/native_profile.c")
target_include_directories(profile_sparse_safety_tests PRIVATE
    "${PROFILE_PG_SOURCE}/native/src/powergridNative/include" "${JNI_INCLUDE}" "${JNI_PLATFORM_INCLUDE}")
target_link_libraries(profile_sparse_safety_tests PRIVATE superlu openblas_static)
target_link_options(profile_sparse_safety_tests PRIVATE -static-libgcc -static
    -Wl,--wrap=dgstrf -Wl,--wrap=dgstrs -Wl,--wrap=dsnode_dfs)
target_compile_options(profile_sparse_safety_tests PRIVATE -UNDEBUG)
'''
    (project / 'CMakeLists.txt').write_text(base_cmake + extra)
    shutil.copy2(ROOT / 'tools/native/sparse_safety_tests.c', project / 'sparse_safety_tests.c')
    tests = (project / 'sparse_safety_tests.c').read_text()
    first = '    sparsematrix_t a; sparsematrix_init(&a);'
    added_first = first + '\n    uint64_t profile[WWPG_PROFILE_WORDS] = {0};\n    a.m_profile = profile;'
    last = '    printf("PASS %d native safety checks plus 100 valid refactor/cleanup cycles\\n",checks+1);'
    added_last = '''    assert(profile[4 + 2 * WWPG_TRIANGULAR_SOLVE] == (uint64_t)solve_calls);
    assert(profile[5 + 2 * WWPG_TRIANGULAR_SOLVE] > 0);
    assert(profile[4 + 2 * WWPG_PERMUTATION] > 0);
    assert(a.m_profile == profile);
    puts("PASS 4 active profile counter checks");
''' + last
    assert tests.count(first) == tests.count(last) == 1
    profile_tests = tests.replace(first,added_first).replace(last,added_last)
    assert profile_tests.replace(added_last,last).replace(added_first,first) == tests
    (project / 'profile_sparse_safety_tests.c').write_text(profile_tests)
    sources = folder / 'sources'
    java = Path(args.java_home)
    configure = ['cmake','-S',str(project),'-B',str(build),'-DCMAKE_BUILD_TYPE=Release',
        '-DCMAKE_SYSTEM_NAME=Windows','-DHOSTCC=/usr/bin/gcc',
        '-DCMAKE_C_COMPILER=' + str(compiler),
        '-DCMAKE_AR=' + str(bindir / 'x86_64-w64-mingw32-ar'),
        '-DCMAKE_RANLIB=' + str(bindir / 'x86_64-w64-mingw32-ranlib'),
        '-DPG_SOURCE=' + str(sources / 'PowerGrid'),
        '-DPROFILE_PG_SOURCE=' + str(sources / 'PowerGridProfile'),
        '-DSUPERLU_SOURCE=' + str(sources / 'superlu'),
        '-DOPENBLAS_SOURCE=' + str(sources / 'OpenBLAS'),
        '-DJNI_INCLUDE=' + str(java / 'include'), '-DJNI_PLATFORM_INCLUDE=' + str(java / 'include/win32')]
    compile = ['cmake','--build',str(build),'--parallel',str(args.jobs),'--target',
        'powergridNative','powergridNativeProfile','sparse_safety_tests','profile_sparse_safety_tests']
    files = [Path(__file__), ROOT / 'tools/native/CMakeLists.txt', ROOT / 'tools/native/sparse_safety_tests.c',
        folder / 'prepared.json', folder / 'instrumentation.json', toolchain_path]
    files += sorted(p for p in (sources / 'PowerGridProfile').rglob('*') if p.is_file())
    files += sorted(project.iterdir())
    fingerprints = {str(p):sha(p) for p in files}
    env = dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ['PATH'])
    for name, command in (('configure',configure),('build',compile)):
        print(json.dumps(dict(stage=name, command=command)), flush=True)
        with (folder / (name + '.log')).open('x') as log:
            result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            print((folder / (name + '.log')).read_text(errors='replace')[-10000:])
            raise SystemExit(result.returncode)
    assert all(sha(Path(p)) == expected for p,expected in fingerprints.items())
    artifacts = {name:dict(path=str(build / name), sha256=sha(build / name)) for name in
        ('libpowergridNative7.dll','libpowergridNativeProfile.dll','sparse_safety_tests.exe','profile_sparse_safety_tests.exe')}
    record = dict(schema=1, platform='windows', baseline_guarded_sources_unchanged=True,
        original_profile_source_restored=instrumentation['all_original_source_restored'],
        auxiliary_buffer_unchanged=True, source_lock=prepared['source_lock'], artifacts=artifacts,
        commands=[configure,compile], inputs=[dict(path=p,sha256=h) for p,h in fingerprints.items()],
        logs=[dict(path=str(folder / (name + '.log')),sha256=sha(folder / (name + '.log'))) for name in ('configure','build')],
        native_checks_run=False, gameplay_checks_run=False)
    (folder / 'build.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(dict(built=str(folder / 'build.json'), artifacts=artifacts)), flush=True)


if __name__ == '__main__':
    main()
