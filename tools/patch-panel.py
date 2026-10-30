#!/usr/bin/env python3
# hkg-climate-page (GPL-3.0)
"""Add our page hooks (panel/hooks/*.smali) to the deodexed stock panel.

usage: patch-panel.py <smali dir>

Edits <smali dir>/com/mobis/splitscreen/right/SplitScreenHome.smali in place:
  - field.smali after the last instance .field;
  - register.smali on the line after :cond_a55 in cM()V (which must have .registers 10);
  - show.smali on the line after the :pswitch_1b8 label in aS(I) (which must also have :goto_2d).
Every check runs before anything is written. Exit 1 naming the anchor if one fails (the file is
then unchanged); exit 2 on a usage error. Uses only the Python standard library.
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOOKS = os.path.join(ROOT, 'panel', 'hooks')
TARGET = os.path.join('com', 'mobis', 'splitscreen', 'right', 'SplitScreenHome.smali')
FIELD = 'hkgClimatePage'
CM = '.method private cM()V'
AS = '.method private aS(I)Lcom/mobis/splitscreen/right/k;'


class Refused(Exception):
    pass


def hook(name):
    with open(os.path.join(HOOKS, name), encoding='utf-8') as f:
        return f.read().splitlines()


def method(lines, decl):
    """(first, end) line indexes of the method whose declaration line is decl."""
    starts = [i for i, l in enumerate(lines) if l.strip() == decl]
    if len(starts) != 1:
        raise Refused('anchor %s: found %d times (need 1)' % (decl, len(starts)))
    for end in range(starts[0] + 1, len(lines)):
        if lines[end].strip() == '.end method':
            return starts[0], end
    raise Refused('anchor %s: no .end method' % decl)


def label(lines, first, end, name, where):
    """Index of the one definition of label name in lines[first:end]. Entries of switch tables
    (between .packed-switch/.sparse-switch and their .end) name labels too; they don't count."""
    found = []
    in_table = False
    for i in range(first, end):
        s = lines[i].strip()
        if s.startswith(('.packed-switch', '.sparse-switch')):
            in_table = True
        elif s in ('.end packed-switch', '.end sparse-switch'):
            in_table = False
        elif s == name and not in_table:
            found.append(i)
    if len(found) != 1:
        raise Refused('anchor %s: found %d times in %s (need 1)' % (name, len(found), where))
    return found[0]


def registers(lines, first, end, want, where):
    for i in range(first + 1, end):
        s = lines[i].strip()
        if s.startswith(('.registers', '.locals')):
            if s != want:
                raise Refused('anchor %s: %s has %s' % (want, where, s))
            return
    raise Refused('anchor %s: %s has no register count' % (want, where))


def last_instance_field(lines):
    """Index of the line after which a new instance field goes: the last non-static .field line,
    or the .end field of its annotation body if it has one."""
    last = None
    for i, l in enumerate(lines):
        words = l.split()
        if words[:1] == ['.field'] and 'static' not in words[1:-1]:
            last = i
    if last is None:
        raise Refused('anchor instance .field: none found')
    nxt = last + 1
    while nxt < len(lines) and not lines[nxt].strip():
        nxt += 1
    if nxt < len(lines) and lines[nxt].strip().startswith('.annotation'):
        for j in range(nxt, len(lines)):
            if lines[j].strip() == '.end field':
                return j
        raise Refused('anchor instance .field: no .end field')
    return last


def patch(lines):
    if any(FIELD in l for l in lines):
        raise Refused('already patched (%s is present)' % FIELD)
    inserts = [(last_instance_field(lines), hook('field.smali'))]

    first, end = method(lines, CM)
    registers(lines, first, end, '.registers 10', 'cM()V')
    inserts.append((label(lines, first, end, ':cond_a55', 'cM()V'), hook('register.smali')))

    first, end = method(lines, AS)
    label(lines, first, end, ':goto_2d', 'aS(I)')
    inserts.append((label(lines, first, end, ':pswitch_1b8', 'aS(I)'), hook('show.smali')))

    out = list(lines)
    for at, text in sorted(inserts, reverse=True):
        out[at + 1:at + 1] = text
    return out


def main():
    if len(sys.argv) != 2 or sys.argv[1] in ('-h', '--help'):
        print(__doc__, file=sys.stderr)
        return 2
    path = os.path.join(sys.argv[1], TARGET)
    try:
        with open(path, encoding='utf-8') as f:
            lines = f.read().splitlines()
        out = patch(lines)
    except Refused as e:
        print('patch-panel: %s' % e, file=sys.stderr)
        return 1
    except OSError as e:
        print('patch-panel: %s' % e, file=sys.stderr)
        return 1
    tmp = path + '.tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out) + '\n')
    os.replace(tmp, path)
    print('patch-panel: patched %s' % path)
    return 0


if __name__ == '__main__':
    sys.exit(main())
