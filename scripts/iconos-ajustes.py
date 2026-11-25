"""Full-color icons for the settings pages (res/drawable/vd_pref_*.xml), in the style of the Ajustes rows
(vd_setting_*.xml): small objects with soft gradients on a 48 x 48 grid. The user asked for them on 24/11/2026.

Run from the codigo folder: python scripts/iconos-ajustes.py
"""
import math
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'drawable')


def rr(x, y, w, h, r):
    """A rounded rectangle"""
    r = min(r, w / 2, h / 2)
    return (f'M{x + r:g},{y:g} L{x + w - r:g},{y:g} A{r:g},{r:g} 0 0 1 {x + w:g},{y + r:g} L{x + w:g},{y + h - r:g} '
            f'A{r:g},{r:g} 0 0 1 {x + w - r:g},{y + h:g} L{x + r:g},{y + h:g} A{r:g},{r:g} 0 0 1 {x:g},{y + h - r:g} '
            f'L{x:g},{y + r:g} A{r:g},{r:g} 0 0 1 {x + r:g},{y:g} Z')


def circle(cx, cy, r):
    return f'M{cx - r:g},{cy:g} A{r:g},{r:g} 0 1 0 {cx + r:g},{cy:g} A{r:g},{r:g} 0 1 0 {cx - r:g},{cy:g} Z'


def star(cx, cy, r, inner=0.45, points=5):
    pts = []
    for i in range(points * 2):
        a = -math.pi / 2 + i * math.pi / points
        rad = r if i % 2 == 0 else r * inner
        pts.append(f'{cx + rad * math.cos(a):.2f},{cy + rad * math.sin(a):.2f}')
    return 'M' + ' L'.join(pts) + ' Z'


def fill(d, color, even_odd=False):
    """A filled shape: color is '#RRGGBB' or (top, bottom) for a vertical gradient over the shape"""
    rule = '\n        android:fillType="evenOdd"' if even_odd else ''
    if isinstance(color, str):
        return f'    <path\n        android:pathData="{d}"{rule}\n        android:fillColor="{color}" />\n'
    top, bottom = color[0], color[1]
    y0, y1 = (color[2], color[3]) if len(color) > 2 else (4, 44)
    return (f'    <path\n        android:pathData="{d}"{rule}>\n'
            f'        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="24" android:startY="{y0}" android:endX="24" android:endY="{y1}">\n'
            f'                <item android:color="{top}" android:offset="0" />\n'
            f'                <item android:color="{bottom}" android:offset="1" />\n'
            f'            </gradient>\n        </aapt:attr>\n    </path>\n')


def line(d, color, width=3.0):
    return (f'    <path\n        android:pathData="{d}"\n        android:strokeColor="{color}"\n'
            f'        android:strokeWidth="{width:g}"\n        android:strokeLineCap="round"\n        android:strokeLineJoin="round" />\n')


# shared pieces
FOLDER_BACK = 'M6,14 Q6,10 10,10 L19,10 L23,14 L38,14 Q42,14 42,18 L42,36 Q42,40 38,40 L10,40 Q6,40 6,36 Z'
FOLDER_FRONT = 'M6,20 Q6,18 8,18 L40,18 Q42,18 42,20 L42,36 Q42,40 38,40 L10,40 Q6,40 6,36 Z'
DOC = 'M12,4 L30,4 L38,12 L38,42 Q38,44 36,44 L14,44 Q12,44 12,42 Z'
DOC_FOLD = 'M30,4 L30,12 L38,12 Z'


def folder(extra=''):
    return fill(FOLDER_BACK, '#F9A825') + fill(FOLDER_FRONT, ('#FFE082', '#FFB300', 18, 40)) + extra


def doc(extra, lines=True):
    rows = ''.join(fill(rr(17, y, w, 2.6, 1.3), '#B0BEC5') for y, w in ((17, 16), (23, 16), (29, 10))) if lines else ''
    return fill(DOC, ('#FFFFFF', '#DCE6F0')) + fill(DOC_FOLD, '#9FB7CC') + rows + extra


def small_pencil(dx=0, dy=0):
    def p(d):
        out = []
        for tok in d.split(' '):
            if ',' in tok and tok[0].isalpha():
                c, rest = tok[0], tok[1:]
                x, y = rest.split(',')
                out.append(f'{c}{float(x) + dx:g},{float(y) + dy:g}')
            elif ',' in tok:
                x, y = tok.split(',')
                out.append(f'{float(x) + dx:g},{float(y) + dy:g}')
            else:
                out.append(tok)
        return ' '.join(out)
    return (fill(p('M28,38 L40,26 L44,30 L32,42 Z'), ('#FFD54F', '#FFA000', 26, 42)) +
            fill(p('M28,38 L32,42 L26,44 Z'), '#FFE0B2') + fill(p('M40,26 L42,24 L46,28 L44,30 Z'), '#F48FB1'))


def note(x, y, color=('#F06292', '#C2185B')):
    """A single eighth note whose head is at x, y"""
    return (fill(circle(x, y, 4.2), (color[0], color[1], y - 4, y + 4)) + fill(rr(x + 2.2, y - 16, 2.6, 16, 1.3), color[1]) +
            fill(f'M{x + 4.8:g},{y - 16:g} Q{x + 11:g},{y - 13:g} {x + 9:g},{y - 7:g} Q{x + 8:g},{y - 11:g} {x + 4.8:g},{y - 11:g} Z', color[1]))


ICONS = {
    'language': fill(circle(24, 24, 18), ('#4FC3F7', '#1565C0')) +
        fill('M14,13 Q19,11 21,15 Q20,20 15,20 Q11,19 14,13 Z', '#81C784') +
        fill('M27,26 Q33,23 36,28 Q35,35 29,37 Q25,33 27,26 Z', '#81C784') +
        line('M24,6 Q14,24 24,42 M24,6 Q34,24 24,42 M7,18 L41,18 M7,30 L41,30', '#E1F5FE', 1.6),
    'theme': fill(circle(24, 24, 17), ('#FFE082', '#FFA000')) + fill(circle(31, 18, 13), '#2E3350') +
        fill(star(36, 30, 3.2), '#FFF59D') + fill(star(39, 14, 2.2), '#FFF59D'),
    'appicon': fill(rr(7, 7, 34, 34, 10), ('#8C7BFF', '#5C5CFF')) + line('M24,15 L24,30 M17,24 L24,31 L31,24', '#FFFFFF', 3.4) +
        fill(rr(16, 34, 16, 3, 1.5), '#48D6C6'),
    'contrast': fill(circle(24, 24, 17), '#F5F5F5') + fill('M24,7 A17,17 0 0 1 24,41 Z', '#263238') +
        line(circle(24, 24, 17), '#90A4AE', 1.6),
    'picture': fill(rr(5, 9, 38, 30, 6), ('#81D4FA', '#29B6F6', 9, 39)) +
        fill('M8,36 L19,22 L27,31 L31,27 L40,36 Z', ('#81C784', '#388E3C', 22, 36)) + fill(circle(32, 17, 3.6), '#FFF59D'),
    'image_off': fill(rr(5, 9, 38, 30, 6), ('#B0BEC5', '#78909C', 9, 39)) +
        fill('M8,36 L19,22 L27,31 L31,27 L40,36 Z', '#546E7A') + fill(circle(32, 17, 3.6), '#ECEFF1') +
        line('M9,7 L39,41', '#FFFFFF', 6.5) + line('M9,7 L39,41', '#E53935', 3.5),
    'card': fill(rr(8, 5, 32, 38, 7), ('#F5F7FA', '#B8C4CE')) + fill(rr(12, 10, 24, 9, 3), ('#8C7BFF', '#5C5CFF', 10, 19)) +
        fill(rr(12, 23, 24, 3, 1.5), '#90A4AE') + fill(rr(12, 29, 16, 3, 1.5), '#90A4AE') + fill(rr(12, 35, 24, 4, 2), '#48D6C6'),
    'layers': fill('M24,22 L42,31 L24,40 L6,31 Z', '#4527A0') + fill('M24,15 L42,24 L24,33 L6,24 Z', ('#9575CD', '#673AB7', 15, 33)) +
        fill('M24,8 L42,17 L24,26 L6,17 Z', ('#D1C4E9', '#9575CD', 8, 26)),
    'heart': fill('M24,41 C10,31 5,23 5,16 C5,10 10,6 15.5,6 C19.5,6 22.5,8.5 24,11.5 C25.5,8.5 28.5,6 32.5,6 C38,6 43,10 43,16 C43,23 38,31 24,41 Z',
                  ('#FF8A80', '#E53935', 6, 41)) + fill(circle(15, 15, 3), '#FFCDD2'),
    'key': fill(circle(15, 24, 10) + ' ' + circle(15, 24, 4), ('#FFE082', '#FFA000', 14, 34), even_odd=True) +
        fill(rr(23, 21.5, 20, 5, 2), ('#FFE082', '#FFA000', 21, 27)) + fill(rr(34, 25, 3.5, 7, 1.2), '#FFA000') +
        fill(rr(39.5, 25, 3.5, 5, 1.2), '#FFA000'),
    'quote': fill(rr(5, 7, 38, 27, 9), ('#80DEEA', '#00ACC1', 7, 34)) + fill('M13,32 L10,42 L22,33 Z', '#00ACC1') +
        fill(circle(18, 20, 3.6), '#FFFFFF') + fill(circle(30, 20, 3.6), '#FFFFFF') +
        line('M21.4,20.5 Q21.6,25 17.5,27', '#FFFFFF', 2.2) + line('M33.4,20.5 Q33.6,25 29.5,27', '#FFFFFF', 2.2),
    'pencil': fill('M12,30 L31,11 L38,18 L19,37 Z', ('#FFD54F', '#FFA000', 11, 37)) + fill('M12,30 L19,37 L8,41 Z', '#FFE0B2') +
        fill('M8,41 L10,35.5 L13.5,39 Z', '#455A64') + fill('M31,11 L34,8 Q36,6 38,8 L41,11 Q43,13 41,15 L38,18 Z', '#F48FB1') +
        line('M29.5,13.5 L35.5,19.5', '#CFD8DC', 2.4),
    'eye': fill('M3,24 Q24,4 45,24 Q24,44 3,24 Z', ('#FFFFFF', '#E3EAF0')) + fill(circle(24, 24, 8.5), ('#64B5F6', '#1565C0', 15, 33)) +
        fill(circle(24, 24, 3.6), '#0D2A4D') + fill(circle(21, 21, 1.8), '#FFFFFF') + line('M3,24 Q24,4 45,24 Q24,44 3,24 Z', '#90A4AE', 1.4),
    'clipboard': fill(rr(9, 7, 30, 37, 5), ('#A1887F', '#6D4C41', 7, 44)) + fill(rr(13, 12, 22, 28, 3), '#FFFFFF') +
        fill(rr(17, 4, 14, 8, 3), ('#CFD8DC', '#90A4AE', 4, 12)) +
        ''.join(fill(rr(16, y, 16, 2.6, 1.3), '#B0BEC5') for y in (18, 24, 30)),
    'hand': line('M5,14 L15,14 M5,14 L9,10 M5,14 L9,18 M43,14 L33,14 M43,14 L39,10 M43,14 L39,18', '#5C5CFF', 3) +
        fill('M21,44 Q14,40 13,33 L12,27 Q12,24 15,25 L18,29 L18,13 Q18,10 21,10 Q24,10 24,13 L24,24 L33,25 Q37,26 36,30 L34,40 Q33,44 28,44 Z',
             ('#FFD7A8', '#F5A15C', 10, 44)),
    'share': line('M15,24 L33,12 M15,24 L33,36', '#00A86B', 3.2) +
        ''.join(fill(circle(x, y, 6.5), ('#7DF5B6', '#00C853', y - 6, y + 6)) for x, y in ((33, 12), (15, 24), (33, 36))),
    'numbers': fill(rr(4, 11, 40, 26, 9), ('#FF8A65', '#E64A19', 11, 37)) +
        line('M13,19 L15,17 L15,30 M20,19.5 Q23.5,16 27,19 Q28.5,22 20.5,30 L28,30 M31.5,18.5 Q38,16 37,21.5 Q36,24 33,24 Q38,24.5 37.5,28 Q36,31.5 31,29.5', '#FFFFFF', 2.4),
    'battery': fill(rr(5, 13, 34, 22, 5), ('#ECEFF1', '#B0BEC5', 13, 35)) + fill(rr(9, 17, 20, 14, 2.5), ('#B9F6CA', '#00C853', 17, 31)) +
        fill(rr(39, 19.5, 4, 9, 1.6), '#90A4AE') + line('M20,19 L16.5,25 L21,25 L17.5,30', '#FFFFFF', 1.8),
    'reset': line('M36.5,14 A15,15 0 1 0 39,27', '#FF7043', 5) + fill('M30.5,6.5 L42.5,8.5 L38,19.5 Z', '#FF7043'),
    'retry': line('M10,22 A14,14 0 0 1 35,14', '#43A047', 4.5) + fill('M30,8 L41,11 L36,21 Z', '#43A047') +
        line('M38,26 A14,14 0 0 1 13,34', '#66BB6A', 4.5) + fill('M18,40 L7,37 L12,27 Z', '#66BB6A'),
    'incognito': fill('M12,21 L15,9 Q24,12.5 33,9 L36,21 Z', ('#607D8B', '#263238', 9, 21)) + fill(rr(5, 20, 38, 5, 2.5), '#37474F') +
        line(circle(16, 33, 5.5), '#263238', 3) + line(circle(32, 33, 5.5), '#263238', 3) + line('M21.5,33 Q24,31 26.5,33', '#263238', 2.6),
    'speed': fill('M5,35 A19,19 0 0 1 43,35 Z', ('#E0F7FA', '#80DEEA', 16, 35)) +
        line('M9,35 A15,15 0 0 1 16,22.5', '#43A047', 4) + line('M18,21 A15,15 0 0 1 30,21', '#FDD835', 4) +
        line('M32,22.5 A15,15 0 0 1 39,35', '#E53935', 4) + line('M24,35 L33,22', '#37474F', 3) + fill(circle(24, 35, 3.6), '#37474F'),
    'box': fill(rr(9, 18, 30, 23, 3), ('#FFB74D', '#E65100', 18, 41)) + fill(rr(6, 9, 36, 10, 3), ('#FFE0B2', '#FB8C00', 9, 19)) +
        fill(rr(18.5, 24, 11, 4, 2), '#FFE0B2'),
    'clock': fill(circle(12, 11, 5.5), '#FF7043') + fill(circle(36, 11, 5.5), '#FF7043') +
        line('M14,40 L11,44 M34,40 L37,44', '#FF7043', 3) + fill(circle(24, 26, 16), '#FF7043') +
        fill(circle(24, 26, 13), ('#FFFFFF', '#E0E0E0', 13, 39)) + line('M24,26 L24,18 M24,26 L30.5,29.5', '#37474F', 2.6),
    'wifi': line('M7,19 Q24,4 41,19', '#B3E5FC', 4.2) + line('M12.5,25.5 Q24,15 35.5,25.5', '#4FC3F7', 4.2) +
        line('M18,32 Q24,26.5 30,32', '#0288D1', 4.2) + fill(circle(24, 38.5, 3.2), '#0288D1'),
    'network': fill(rr(7, 7, 34, 15, 4), ('#B0BEC5', '#546E7A', 7, 22)) + fill(rr(7, 26, 34, 15, 4), ('#B0BEC5', '#546E7A', 26, 41)) +
        fill(circle(13.5, 14.5, 2.2), '#69F0AE') + fill(circle(13.5, 33.5, 2.2), '#69F0AE') +
        fill(rr(20, 13.2, 15, 2.6, 1.3), '#ECEFF1') + fill(rr(20, 32.2, 15, 2.6, 1.3), '#ECEFF1'),
    'shield': fill('M24,4 L40,10 L40,22 Q40,36 24,44 Q8,36 8,22 L8,10 Z', ('#81C784', '#2E7D32')) +
        line('M17,24 L22,29 L31,19', '#FFFFFF', 3.4),
    'cookie': fill(circle(24, 24, 18), ('#FFD08A', '#D08A3C')) +
        ''.join(fill(circle(x, y, r), '#6D4C41') for x, y, r in ((17, 16, 2.6), (29, 14, 2.2), (32, 27, 2.8), (18, 30, 2.5), (25, 23, 1.9))),
    'parallel': fill('M11,6 L19,6 L19,21 L24,21 L15,32 L6,21 L11,21 Z', ('#8C7BFF', '#5C5CFF', 6, 32)) +
        fill('M29,6 L37,6 L37,21 L42,21 L33,32 L24,21 L29,21 Z', ('#7FEBDF', '#26B5A5', 6, 32)) + fill(rr(6, 37, 36, 4, 2), '#90A4AE'),
    'chip': ''.join(fill(rr(x, 7, 3, 7, 1), '#90A4AE') + fill(rr(x, 34, 3, 7, 1), '#90A4AE') for x in (15.5, 22.5, 29.5)) +
        ''.join(fill(rr(7, y, 7, 3, 1), '#90A4AE') + fill(rr(34, y, 7, 3, 1), '#90A4AE') for y in (15.5, 22.5, 29.5)) +
        fill(rr(12, 12, 24, 24, 5), ('#4DB6AC', '#00796B', 12, 36)) + fill(rr(18, 18, 12, 12, 2.5), '#B2DFDB'),
    'folder_x': folder(fill(circle(36, 36, 8), '#E53935') + line('M33,33 L39,39 M39,33 L33,39', '#FFFFFF', 2.4)),
    'folder_video': folder(fill('M21,24 L30,29 L21,34 Z', '#FFFFFF')),
    'folder_music': folder(note(21, 34, ('#FFFFFF', '#FFFFFF'))),
    'storage': fill(rr(5, 13, 38, 22, 6), ('#CFD8DC', '#607D8B', 13, 35)) + fill(rr(10, 22.5, 16, 3, 1.5), '#ECEFF1') +
        fill(circle(36, 24, 2.6), '#69F0AE'),
    'document': doc(''),
    'rename': doc('', lines=True) + small_pencil(-2, -2),
    'code': doc(line('M20,27 L16,31 L20,35 M30,27 L34,31 L30,35 M27,24 L23,38', '#5C5CFF', 2.4), lines=False) +
        fill(rr(17, 16, 14, 2.6, 1.3), '#B0BEC5'),
    'lyrics': doc(note(22, 34), lines=False) + fill(rr(17, 13, 16, 2.6, 1.3), '#B0BEC5'),
    'subtitles': fill(rr(4, 9, 40, 30, 7), ('#4A4A4A', '#1E1E1E', 9, 39)) + fill(rr(9, 25, 14, 4, 2), '#FFFFFF') +
        fill(rr(26, 25, 13, 4, 2), '#FFFFFF') + fill(rr(9, 31.5, 22, 3.6, 1.8), '#FFEB3B'),
    'save': fill(rr(7, 7, 34, 34, 5), ('#64B5F6', '#1565C0', 7, 41)) + fill(rr(14, 7, 19, 11, 1.5), '#E3F2FD') +
        fill(rr(27, 9, 4, 7, 1), '#1565C0') + fill(rr(12.5, 25, 23, 16, 2), '#FFFFFF') +
        fill(rr(16, 29.5, 16, 2.4, 1.2), '#BBDEFB') + fill(rr(16, 34, 16, 2.4, 1.2), '#BBDEFB'),
    'scissors': line('M18,29 L34,7', '#B0BEC5', 4) + line('M30,29 L14,7', '#CFD8DC', 4) +
        line(circle(15, 35, 5.5), '#E53935', 3.6) + line(circle(33, 35, 5.5), '#E53935', 3.6),
    'skip': fill(rr(5, 9, 38, 30, 9), ('#66BB6A', '#2E7D32', 9, 39)) + fill('M14,17 L24,24 L14,31 Z', '#FFFFFF') +
        fill('M24,17 L34,24 L24,31 Z', '#FFFFFF') + fill(rr(34, 17, 3, 14, 1.2), '#FFFFFF'),
    'hd': fill(rr(5, 11, 38, 26, 7), ('#8C7BFF', '#3D3DCC', 11, 37)) +
        line('M13,18 L13,30 M13,24 L20,24 M20,18 L20,30 M26,18 L26,30 L29,30 Q35,30 35,24 Q35,18 29,18 Z', '#FFFFFF', 2.6),
    'album': fill(circle(24, 24, 19), ('#4A4A4A', '#000000')) + line('M11,17 A14,14 0 0 1 17,11', '#9E9E9E', 1.6) +
        line('M37,31 A14,14 0 0 1 31,37', '#9E9E9E', 1.6) + fill(circle(24, 24, 7.5), ('#FF8A80', '#E53935', 17, 31)) +
        fill(circle(24, 24, 2), '#FFFFFF'),
    'film': fill(rr(6, 6, 36, 36, 5), ('#546E7A', '#263238', 6, 42)) +
        ''.join(fill(rr(x, y, 3.4, 3.4, 0.8), '#ECEFF1') for x in (9, 35.6) for y in (10, 17, 24, 31, 37.5)) +
        fill(rr(15.5, 11, 17, 26, 2), ('#4FC3F7', '#0288D1', 11, 37)) + fill('M21,19 L29,24 L21,29 Z', '#FFFFFF'),
    'tag': fill('M5,24 L17,9 Q18,8 20,8 L39,8 Q42,8 42,11 L42,37 Q42,40 39,40 L20,40 Q18,40 17,39 Z', ('#FFE082', '#F9A825', 8, 40)) +
        fill(circle(17, 24, 3.4), '#FFFFFF') + fill(rr(24, 18, 13, 3, 1.5), '#FFF8E1') + fill(rr(24, 27, 9, 3, 1.5), '#FFF8E1'),
    'music': note(14, 36) + note(32, 31) + fill('M16.2,20 L36.6,15 L36.6,19 L16.2,24 Z', '#C2185B'),
    'book': fill('M3,11 L3,41 Q13,38 24,43 Q35,38 45,41 L45,11 Z', ('#A1887F', '#6D4C41', 11, 43)) +
        fill('M24,13 Q15,7 6,9 L6,37 Q15,35 24,40 Z', ('#FFFFFF', '#E6E6E6')) + fill('M24,13 Q33,7 42,9 L42,37 Q33,35 24,40 Z', ('#FFFFFF', '#E6E6E6')) +
        line('M10,16 Q15,15 20,17 M10,22 Q15,21 20,23 M28,17 Q33,15 38,16 M28,23 Q33,21 38,22', '#BDBDBD', 1.8),
    'trash': fill(rr(18, 4, 12, 6, 2), '#E57373') + fill(rr(8, 8, 32, 6, 3), '#EF5350') +
        fill('M11,15 L37,15 L34,42 Q34,44 32,44 L16,44 Q14,44 14,42 Z', ('#EF9A9A', '#C62828', 15, 44)) +
        line('M19.5,20 L20,38 M24,20 L24,38 M28.5,20 L28,38', '#FFCDD2', 2),
    'phone_update': fill(rr(12, 3, 24, 42, 6), ('#546E7A', '#263238', 3, 45)) + fill(rr(15, 8, 18, 30, 2.5), ('#80D8FF', '#2979FF', 8, 38)) +
        line('M24,14 L24,30 M18,24.5 L24,30.5 L30,24.5', '#FFFFFF', 3) + fill(rr(20, 40, 8, 2, 1), '#90A4AE'),
    'sort': fill(rr(7, 9, 34, 7, 3.5), ('#8C7BFF', '#5C5CFF', 9, 16)) + fill(rr(7, 20.5, 25, 7, 3.5), ('#A79CFF', '#7C7CFF', 20, 28)) +
        fill(rr(7, 32, 15, 7, 3.5), ('#7FEBDF', '#26B5A5', 32, 39)),
    'filter': fill('M5,8 Q5,6 7,6 L41,6 Q43,6 43,8 Q43,9 42,10 L29,25 L29,39 Q29,41 27,42 L22,44 Q19,45 19,42 L19,25 L6,10 Q5,9 5,8 Z',
                   ('#E1BEE7', '#8E24AA', 6, 44)),
    'person': fill(circle(24, 15, 9), ('#FFD7A8', '#F5A15C', 6, 24)) + fill('M7,43 Q7,27 24,27 Q41,27 41,43 Z', ('#64B5F6', '#1565C0', 27, 43)),
    'stars': fill(star(20, 27, 16), ('#FFF176', '#FFA000', 11, 43)) + fill(star(38, 11, 7), ('#FFF59D', '#FFB300', 4, 18)),
}

# old line icon -> new color icon (vd_pref_*) or an existing Ajustes icon (vd_setting_*)
MAPPING = {
    'baseline_reset_tv_24': 'reset', 'ic_code': 'code', 'ic_format': 'film', 'if_file_rename': 'rename',
    'ic_terminal': '@vd_setting_terminal', 'ic_image': 'picture', 'ic_update': '@vd_setting_engine', 'ic_textformat': 'rename',
    'ic_money': 'skip', 'ic_language': 'language', 'ic_cut': 'scissors', 'ic_clock': 'clock',
    'baseline_video_metadata': '@vd_setting_media', 'baseline_manage_search_24': '@vd_setting_home', 'ic_update_app': 'phone_update',
    'ic_subtitles_alternative': 'subtitles', 'ic_subtitles': 'subtitles', 'ic_speed': 'speed', 'ic_retries': 'retry',
    'ic_lyrics': 'lyrics', 'ic_lines': 'parallel', 'ic_format_id': 'tag', 'ic_folder_delete': 'folder_x',
    'ic_download_type': '@vd_setting_downloads', 'ic_chapters': 'book', 'ic_card': 'card', 'baseline_swipe_24': 'hand',
    'baseline_share2_24': 'share', 'baseline_recommend_24': 'heart', 'baseline_numbers_24': 'numbers',
    'baseline_install_mobile_24': 'phone_update', 'baseline_format_quote_24': 'quote', 'baseline_archive_24': 'box',
    'outline_storage_24': 'storage', 'outline_data_object_24': 'code', 'metered_networks': 'wifi',
    'ic_video_downloaded': 'folder_video', 'ic_video': 'hd', 'ic_start': 'clock', 'ic_end': 'clock',
    'ic_search_accent': '@vd_setting_home', 'ic_search': '@vd_setting_home', 'ic_plus': 'music', 'ic_nightly': 'incognito',
    'ic_music_downloaded': 'folder_music', 'ic_limitrate': 'speed', 'ic_key': 'key', 'ic_ipv4': 'network', 'ic_frame': 'appicon',
    'ic_edit': 'pencil', 'ic_concurrent_downloads': 'parallel', 'ic_clipboard': 'clipboard', 'ic_battery': 'battery',
    'ic_baseline_file_open_24': '@vd_setting_logs', 'ic_access_folder': '@vd_setting_folders', 'baseline_translate_24': 'language',
    'baseline_timer_24': 'clock', 'baseline_subject_24': 'subtitles', 'baseline_stars_24': 'stars', 'baseline_source_24': 'code',
    'baseline_sort_24': 'sort', 'baseline_settings_suggest_24': 'chip', 'baseline_settings_input_component_24': 'chip',
    'baseline_save_alt_24': 'save', 'baseline_save_24': 'save', 'baseline_pie_chart_24': 'box', 'baseline_person_24': 'person',
    'baseline_note_24': 'shield', 'baseline_network_locked_24': 'shield', 'baseline_layers_24': 'layers',
    'baseline_invert_colors_24': 'contrast', 'baseline_high_quality_24': 'hd', 'baseline_hide_image_24': 'image_off',
    'baseline_format_paint_24': 'theme', 'baseline_folder_24': '@vd_setting_folders', 'baseline_filter_alt_24': 'filter',
    'baseline_fast_forward_24': 'speed', 'baseline_drive_file_move_24': '@vd_setting_folders',
    'baseline_downloading_24': '@vd_setting_downloads', 'baseline_disabled_visible_24': 'eye', 'baseline_description_24': 'document',
    'baseline_delete': 'trash', 'baseline_cookie_24': 'cookie', 'baseline_color_lens_24': '@vd_setting_appearance',
    'baseline_audio_file_24': 'music', 'baseline_arrow_upward_24': 'sort', 'baseline_album_24': 'album',
    'baseline_access_alarm_24': 'clock',
    # root_preferences (Acerca de and the old main list); the Discord and Telegram logos stay as they are
    'baseline_exit_to_app_24': '@vd_setting_close', 'baseline_restore_page_24': '@vd_setting_backup', 'baseline_security_24': 'shield',
    'baseline_star_half_24': 'stars', 'ic_downloads': '@vd_setting_downloads', 'ic_license': 'document',
    'ic_settings': '@vd_setting_advanced', 'ic_translate': 'language',
}

PAGES = ['general_preferences', 'downloading_preferences', 'folders_preference', 'processing_preferences', 'updating_preferences',
         'advanced_preferences', 'app_update_preferences', 'ytdlp_update_preferences', 'temporary_files_preferences', 'root_preferences']


def target(old):
    new = MAPPING[old]
    return new[1:] if new.startswith('@') else f'vd_pref_{new}'


def main():
    for name, body in ICONS.items():
        with open(os.path.join(OUT, f'vd_pref_{name}.xml'), 'w', encoding='utf-8', newline='\n') as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                    '<!-- Made by scripts/iconos-ajustes.py: a full-color settings icon in the style of the Ajustes rows. -->\n'
                    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                    '    xmlns:aapt="http://schemas.android.com/aapt"\n'
                    '    android:width="48dp"\n    android:height="48dp"\n'
                    '    android:viewportWidth="48"\n    android:viewportHeight="48">\n' + body + '</vector>\n')
    xml_dir = os.path.join(OUT, '..', 'xml')
    import re
    for page in PAGES:
        p = os.path.join(xml_dir, page + '.xml')
        s = open(p, encoding='utf-8').read()
        s2 = re.sub(r'((?:app|android):icon="@drawable/)([A-Za-z0-9_]+)(")',
                    lambda m: m.group(1) + (target(m.group(2)) if m.group(2) in MAPPING else m.group(2)) + m.group(3), s)
        if s2 != s:
            open(p, 'w', encoding='utf-8').write(s2)
    print(len(ICONS), 'icons written')


if __name__ == '__main__':
    main()
