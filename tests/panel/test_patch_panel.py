# Tests for tools/patch-panel.py against our own fixture (tests/panel/fixture/SplitScreenHome.smali).
# usage: python3 -m unittest tests/panel/test_patch_panel.py
# The assemble test needs SMALI_CP (eval "$(tools/toolchain.sh env)"); without it it prints SKIP.
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PATCHER = os.path.join(ROOT, 'tools', 'patch-panel.py')
FIXTURE = os.path.join(ROOT, 'tests', 'panel', 'fixture', 'SplitScreenHome.smali')
HOOKS = os.path.join(ROOT, 'panel', 'hooks')
REL = os.path.join('com', 'mobis', 'splitscreen', 'right', 'SplitScreenHome.smali')


def hook(name):
    with open(os.path.join(HOOKS, name), encoding='utf-8') as f:
        return f.read().splitlines()


class PatchPanelTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.path = os.path.join(self.tmp, REL)
        os.makedirs(os.path.dirname(self.path))
        with open(FIXTURE, encoding='utf-8') as f:
            self.write(f.read())

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def write(self, text):
        with open(self.path, 'w', encoding='utf-8') as f:
            f.write(text)

    def read(self):
        with open(self.path, encoding='utf-8') as f:
            return f.read()

    def patch(self):
        return subprocess.run([sys.executable, PATCHER, self.tmp], stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, universal_newlines=True)

    def edit(self, old, new, count=1):
        text = self.read()
        self.assertEqual(text.count(old), count, 'fixture edit anchor: %r' % old)
        self.write(text.replace(old, new))

    def assert_refused(self, anchor):
        before = self.read()
        r = self.patch()
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        self.assertIn(anchor, r.stderr)
        self.assertEqual(self.read(), before, 'file must be left unchanged')

    def test_patches_field_register_and_show(self):
        r = self.patch()
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        lines = self.read().splitlines()
        stripped = [l.strip() for l in lines]

        field = '.field volatile hkgClimatePage:Lcom/mobis/splitscreen/right/k;'
        self.assertEqual(stripped.count(field), 1)
        # after the last stock instance field, before the methods
        at = stripped.index(field)
        self.assertGreater(at, stripped.index('.field private volatile pv:Lcom/mobis/splitscreen/right/n/l;'))
        self.assertLess(at, stripped.index('# direct methods'))

        reg = hook('register.smali')
        a55 = stripped.index(':cond_a55')
        self.assertEqual(lines[a55 + 1:a55 + 1 + len(reg)], reg)

        show = hook('show.smali')
        label = lines.index('    :pswitch_1b8')  # the label, not the switch-table entry
        self.assertEqual(lines[label + 1:label + 1 + len(show)], show)
        self.assertEqual(stripped.count(':pswitch_1b8'), 2)  # label + table entry, nothing added

    def test_second_run_is_refused(self):
        self.assertEqual(self.patch().returncode, 0)
        self.assert_refused('already patched')

    def test_missing_cond_a55(self):
        self.edit('    :cond_a55\n', '')
        self.edit('if-eqz v0, :cond_a55', 'if-eqz v0, :try_end_0')
        self.assert_refused(':cond_a55')

    def test_cond_a55_twice(self):
        self.edit('    :cond_a55\n', '    :cond_a55\n    :cond_a55\n')
        self.assert_refused(':cond_a55')

    def test_cm_registers_11(self):
        self.edit('.method private cM()V\n    .registers 10', '.method private cM()V\n    .registers 11')
        self.assert_refused('.registers 10')

    def test_missing_pswitch_1b8_label(self):
        self.edit('    :pswitch_1b8\n    move-object', '    :pswitch_1b9\n    move-object')
        self.assert_refused(':pswitch_1b8')

    def test_goto_2d_twice(self):
        self.edit('    :goto_2d\n', '    :goto_2d\n    :goto_2d\n')
        self.assert_refused(':goto_2d')

    def test_missing_method(self):
        self.edit('.method private aS(I)', '.method private aT(I)')
        self.assert_refused('aS(I)')

    def test_hooks_use_only_free_registers(self):
        # at :cond_a55, v1 holds the monitor and v4-v8 are live; aS returns v0 at :goto_2d
        for name, allowed in (('register.smali', {'v0', 'v2', 'v3', 'p0'}), ('show.smali', {'v0', 'p0'})):
            code = [l for l in hook(name) if not l.strip().startswith('#')]
            used = set(re.findall(r'\b[vp]\d+\b', '\n'.join(code)))
            self.assertTrue(used, name)
            self.assertLessEqual(used, allowed, name)

    def test_patched_fixture_assembles(self):
        cp = os.environ.get('SMALI_CP')
        if not cp:
            print('SKIP: patched fixture assembles (no SMALI_CP; eval "$(tools/toolchain.sh env)")')
            self.skipTest('no SMALI_CP')
        self.assertEqual(self.patch().returncode, 0)
        out = os.path.join(self.tmp, 'out.dex')
        r = subprocess.run(['java', '-cp', cp, 'org.jf.smali.Main', 'a', '--api', '18', '-o', out,
                            os.path.join(self.tmp, 'com')], stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, universal_newlines=True)
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertGreater(os.path.getsize(out), 0)


if __name__ == '__main__':
    unittest.main()
