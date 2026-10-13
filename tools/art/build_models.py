"""
Builds the 3D models the game ships, from the Quaternius packs in art/quaternius/extracted (CC0).

Each model becomes one small .glb in app/src/main/assets/models/:
  - textures shrunk for phones, colour maps only (normal/roughness maps are dropped: the art is stylized)
  - characters merged with their hair and just the animations the game uses
  - old OBJ packs (crops, furniture) converted to glTF with their material colours

Run from the repo root:  python tools/art/build_models.py
Needs Pillow. Reads only model files; nothing in the art folders is executed.
"""
import io
import json
import os
import struct
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'art', 'quaternius', 'extracted')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'models')

MEGAKIT = os.path.join(SRC, 'Stylized Nature MegaKit[Standard]', 'glTF')
CHARACTERS = os.path.join(SRC, 'Universal Base Characters[Standard]', 'Universal Base Characters[Standard]')
CROPS = os.path.join(SRC, 'Nature Crops Pack - Jan 2020', 'Nature Crops Pack - Jan 2020', 'OBJ')
FURNITURE = os.path.join(SRC, 'Furniture Pack - March 2019', 'Furniture Pack - March 2019', 'OBJ')
UAL1 = os.path.join(SRC, 'Universal Animation Library[Standard]', 'Universal Animation Library[Standard]', 'Unreal-Godot', 'UAL1_Standard.glb')
UAL2 = os.path.join(SRC, 'Universal Animation Library 2[Standard]', 'Universal Animation Library 2[Standard]', 'Unreal-Godot', 'UAL2_Standard.glb')

# What the game uses. Names on the left are the files the app loads.
NATURE = {
    'tree_1': 'CommonTree_1', 'tree_2': 'CommonTree_2', 'tree_3': 'CommonTree_3',
    'pine_1': 'Pine_1', 'pine_2': 'Pine_2',
    'bush': 'Bush_Common', 'bush_flowers': 'Bush_Common_Flowers',
    'rock_1': 'Rock_Medium_1', 'rock_2': 'Rock_Medium_2', 'rock_3': 'Rock_Medium_3',
    'flowers_1': 'Flower_3_Group', 'flowers_2': 'Flower_4_Group',
    'grass': 'Grass_Common_Short', 'mushroom': 'Mushroom_Common', 'plant': 'Plant_1',
}
CROP_MODELS = {f'{crop.lower()}_{stage}': f'{crop}_{stage}' for crop in ('Carrot', 'Tomato', 'Pumpkin') for stage in (1, 2, 3, 4)}
FURNITURE_MODELS = {
    'bed_double': 'BedDouble', 'chair': 'Chair', 'sofa': 'Sofa', 'table': 'Table',
    'bookcase': 'Bookcase_Books', 'nightstand': 'NightStand', 'closet': 'Closet',
}
PEOPLE = {
    # file: (body gltf, skin texture, hair gltf)
    'person_a_dark': ('Superhero_Male_FullBody', 'T_Superhero_Male_Dark.png', 'Hair_SimpleParted'),
    'person_a_light': ('Superhero_Male_FullBody', 'T_Superhero_Male_Ligh.png', 'Hair_SimpleParted'),
    'person_b_dark': ('Superhero_Female_FullBody', 'T_Superhero_Female_Dark_BaseColor.png', 'Hair_Long'),
    'person_b_light': ('Superhero_Female_FullBody', 'T_Superhero_Female_Light_BaseColor.png', 'Hair_Long'),
}
MODULAR_MEN = os.path.join(SRC, 'Ultimate Modular Men- Feb 2022', 'Ultimate Modular Men- Feb 2022', 'Individual Characters', 'glTF')
MODULAR_WOMEN = os.path.join(SRC, 'Ultimate Modular Women - April 2022', 'Ultimate Modular Women - April 2022', 'Individual Characters', 'glTF')
# Clothed everyday people (CC0). The file name is what the app loads: person_<body>_<outfit>.
CLOTHED = {
    'person_0_0': (MODULAR_MEN, 'Casual_Hoodie'), 'person_0_1': (MODULAR_MEN, 'Casual_2'), 'person_0_2': (MODULAR_MEN, 'Farmer'),
    'person_1_0': (MODULAR_WOMEN, 'Casual'), 'person_1_1': (MODULAR_WOMEN, 'Formal'), 'person_1_2': (MODULAR_WOMEN, 'Worker'),
}
# The clothed people's clips the game uses: standing, walking, running, waving, busy hands, and a swing for chopping.
CLOTHED_CLIPS = ['Idle', 'Idle_Neutral', 'Walk', 'Run', 'Wave', 'Interact', 'Sword_Slash']

# Animation clips the game plays, by their names in the Universal Animation Library.
CLIPS = [
    'Idle_Loop', 'Walk_Loop', 'Jog_Fwd_Loop', 'Sitting_Enter', 'Sitting_Idle_Loop', 'Idle_Talking_Loop',
    'Interact', 'Fixing_Kneeling', 'Dance_Loop',  # from library 1
    'TreeChopping_Loop', 'Farm_PlantSeed', 'Farm_Watering', 'Farm_Harvest', 'Chest_Open', 'Consume', 'Yes',  # library 2
]

TEXTURE_MAX = 512
CHARACTER_TEXTURE_MAX = 512


# ---------------------------------------------------------------- glTF reading and writing

def read_gltf(path):
    """A glTF or GLB as (json, binary buffer bytes, folder for external files)."""
    folder = os.path.dirname(path)
    if path.lower().endswith('.glb'):
        data = open(path, 'rb').read()
        json_len, = struct.unpack_from('<I', data, 12)
        doc = json.loads(data[20:20 + json_len])
        bin_len, = struct.unpack_from('<I', data, 20 + json_len)
        blob = data[28 + json_len:28 + json_len + bin_len]
        return doc, bytearray(blob), folder
    doc = json.load(open(path, encoding='utf-8'))
    blob = bytearray()
    if doc.get('buffers'):
        uri = doc['buffers'][0].get('uri')
        if uri and uri.startswith('data:'):
            import base64
            blob = bytearray(base64.b64decode(uri.split(',', 1)[1]))
        elif uri:
            blob = bytearray(open(os.path.join(folder, uri), 'rb').read())
    return doc, blob, folder


def pad4(blob):
    while len(blob) % 4:
        blob.append(0)


def add_view(doc, blob, data):
    pad4(blob)
    view = {'buffer': 0, 'byteOffset': len(blob), 'byteLength': len(data)}
    blob.extend(data)
    doc.setdefault('bufferViews', []).append(view)
    return len(doc['bufferViews']) - 1


# Filament draws only these; extra texture/colour channels (made for other engines) make it refuse the model,
# and the nature packs' COLOR_0 is a wind-sway mask that would tint everything dark.
KEEP_ATTRIBUTES = {'POSITION', 'NORMAL', 'TEXCOORD_0', 'JOINTS_0', 'WEIGHTS_0'}


def write_glb(doc, blob, path):
    for mesh in doc.get('meshes', []):
        for prim in mesh['primitives']:
            prim['attributes'] = {k: v for k, v in prim['attributes'].items() if k in KEEP_ATTRIBUTES}
    pad4(blob)
    doc['buffers'] = [{'byteLength': len(blob)}]
    raw = json.dumps(doc, separators=(',', ':')).encode('utf-8')
    while len(raw) % 4:
        raw += b' '
    total = 12 + 8 + len(raw) + 8 + len(blob)
    with open(path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, total))
        f.write(struct.pack('<II', len(raw), 0x4E4F534A))
        f.write(raw)
        f.write(struct.pack('<II', len(blob), 0x004E4942))
        f.write(blob)
    return total


def encode_image(path, max_size):
    im = Image.open(path)
    im.thumbnail((max_size, max_size), Image.LANCZOS)
    has_alpha = im.mode in ('RGBA', 'LA') and im.getextrema()[-1][0] < 255
    out = io.BytesIO()
    if has_alpha:
        im.convert('RGBA').save(out, 'PNG', optimize=True)
        return out.getvalue(), 'image/png'
    im.convert('RGB').save(out, 'JPEG', quality=85)
    return out.getvalue(), 'image/jpeg'


def slim_materials(doc, blob, folder, max_size, image_override=None):
    """Keeps only colour textures, shrunk and embedded; drops the other maps."""
    textures = doc.get('textures', [])
    images = doc.get('images', [])
    used = {}  # old texture index -> new texture index
    new_textures, new_images = [], []
    for material in doc.get('materials', []):
        for key in ('normalTexture', 'occlusionTexture', 'emissiveTexture'):
            material.pop(key, None)
        pbr = material.setdefault('pbrMetallicRoughness', {})
        pbr.pop('metallicRoughnessTexture', None)
        pbr['metallicFactor'] = 0.0
        pbr['roughnessFactor'] = 0.85
        ref = pbr.get('baseColorTexture')
        if ref is None:
            continue
        old = ref['index']
        if old not in used:
            source = images[textures[old]['source']]
            uri = source.get('uri')
            if image_override and material.get('name') in image_override:
                uri = image_override[material['name']]
            path = os.path.join(folder, uri)
            if not os.path.exists(path):  # some packs keep textures in a sibling folder
                path = os.path.join(folder, '..', 'Textures', os.path.basename(uri))
            data, mime = encode_image(path, max_size)
            new_images.append({'bufferView': add_view(doc, blob, data), 'mimeType': mime})
            texture = {'source': len(new_images) - 1}
            if 'sampler' in textures[old]:
                texture['sampler'] = textures[old]['sampler']
            new_textures.append(texture)
            used[old] = len(new_textures) - 1
        ref['index'] = used[old]
        ref.pop('texCoord', None) if ref.get('texCoord') == 0 else None
    doc['textures'] = new_textures
    doc['images'] = new_images
    if not new_textures:
        doc.pop('textures'); doc.pop('images')
        doc.pop('samplers', None)


def copy_accessor(src_doc, src_blob, dst_doc, dst_blob, index, cache):
    """Copies one accessor (and its data) from another document; returns its new index."""
    if index in cache:
        return cache[index]
    acc = dict(src_doc['accessors'][index])
    view = src_doc['bufferViews'][acc['bufferView']]
    start = view.get('byteOffset', 0)
    data = bytes(src_blob[start:start + view['byteLength']])
    new_view = add_view(dst_doc, dst_blob, data)
    if 'byteStride' in view:
        dst_doc['bufferViews'][new_view]['byteStride'] = view['byteStride']
    acc['bufferView'] = new_view
    dst_doc.setdefault('accessors', []).append(acc)
    cache[index] = len(dst_doc['accessors']) - 1
    return cache[index]


COMPONENTS = {'SCALAR': 1, 'VEC3': 3, 'VEC4': 4}


def constant_track(doc, blob, index, tolerance=1e-4):
    """The single value of a float track whose keyframes are all the same, as packed bytes; else None."""
    acc = doc['accessors'][index]
    if acc.get('componentType') != 5126 or acc['type'] not in COMPONENTS:
        return None
    width = COMPONENTS[acc['type']]
    view = doc['bufferViews'][acc['bufferView']]
    start = view.get('byteOffset', 0) + acc.get('byteOffset', 0)
    values = struct.unpack_from(f'<{acc["count"] * width}f', blob, start)
    first = values[:width]
    for k in range(1, acc['count']):
        if any(abs(values[k * width + i] - first[i]) > tolerance for i in range(width)):
            return None
    return struct.pack(f'<{width}f', *first)


def held_sampler(doc, blob, src_doc, sampler, held):
    """
    A track that never changes, shrunk to two identical keyframes at the clip's start and end.
    (One keyframe would be smaller still, but Filament's animator reads past a lone keyframe,
    which threw the characters' feet out of place.)
    """
    times = src_doc['accessors'][sampler['input']]
    start, end = times.get('min', [0.0])[0], times.get('max', [0.0])[0]
    if end <= start:
        end = start + 0.001
    kind = src_doc['accessors'][sampler['output']]['type']
    return {
        'input': add_accessor(doc, blob, struct.pack('<2f', start, end), 'SCALAR', [start], [end]),
        'output': add_accessor(doc, blob, held + held, kind),
        'interpolation': 'LINEAR',
    }


def add_accessor(doc, blob, data, kind, mins=None, maxs=None):
    acc = {'bufferView': add_view(doc, blob, data), 'componentType': 5126, 'count': len(data) // (4 * COMPONENTS[kind]), 'type': kind}
    if mins is not None:
        acc['min'], acc['max'] = mins, maxs
    doc.setdefault('accessors', []).append(acc)
    return len(doc['accessors']) - 1


# ---------------------------------------------------------------- builders

def build_textured(src_path, out_name, max_size=TEXTURE_MAX):
    doc, blob, folder = read_gltf(src_path)
    doc.pop('extensionsUsed', None)
    slim_materials(doc, blob, folder, max_size)
    return write_glb(doc, blob, os.path.join(OUT, out_name + '.glb'))


def build_person(out_name, body, skin, hair):
    doc, blob, folder = read_gltf(os.path.join(CHARACTERS, 'Base Characters', 'Godot - UE', body + '.gltf'))
    # The body file references one hair already; drop meshes other than body, eyes and eyebrows? They're all needed.
    names = {n.get('name'): i for i, n in enumerate(doc['nodes'])}

    # Hair: same skeleton, so its mesh joins the body's skin as-is.
    hair_doc, hair_blob, hair_folder = read_gltf(os.path.join(CHARACTERS, 'Hairstyles', 'Rigged to Head Bone', 'glTF (Godot -Unreal)', hair + '.gltf'))
    hair_joints = [hair_doc['nodes'][j].get('name') for j in hair_doc['skins'][0]['joints']]
    body_joints = [doc['nodes'][j].get('name') for j in doc['skins'][0]['joints']]
    if hair_joints != body_joints:
        sys.exit(f'{hair}: skeleton differs from {body}')
    cache = {}
    material_base = len(doc['materials'])
    doc['materials'].extend(hair_doc['materials'])
    texture_base = len(doc.get('textures', []))
    image_base = len(doc.get('images', []))
    for t in hair_doc.get('textures', []):
        doc.setdefault('textures', []).append({**t, 'source': t['source'] + image_base})
    for im in hair_doc.get('images', []):
        doc.setdefault('images', []).append({'uri': os.path.relpath(os.path.join(hair_folder, im['uri']), folder)})
    for m in doc['materials'][material_base:]:
        pbr = m.get('pbrMetallicRoughness', {})
        if 'baseColorTexture' in pbr:
            pbr['baseColorTexture'] = {**pbr['baseColorTexture'], 'index': pbr['baseColorTexture']['index'] + texture_base}
        for key in ('normalTexture', 'occlusionTexture'):
            m.pop(key, None)
    for node in hair_doc['nodes']:
        if 'mesh' not in node:
            continue
        mesh = hair_doc['meshes'][node['mesh']]
        prims = []
        for p in mesh['primitives']:
            prim = {'attributes': {k: copy_accessor(hair_doc, hair_blob, doc, blob, v, cache) for k, v in p['attributes'].items()}}
            if 'indices' in p:
                prim['indices'] = copy_accessor(hair_doc, hair_blob, doc, blob, p['indices'], cache)
            if 'material' in p:
                prim['material'] = p['material'] + material_base
            prims.append(prim)
        doc['meshes'].append({'name': mesh.get('name', hair), 'primitives': prims})
        doc['nodes'].append({'name': node.get('name', hair), 'mesh': len(doc['meshes']) - 1, 'skin': 0})
        doc['scenes'][doc.get('scene', 0)]['nodes'].append(len(doc['nodes']) - 1)

    # Animations: copied from the library and pointed at this body's bones by name.
    doc['animations'] = []
    for library in (UAL1, UAL2):
        lib_doc, lib_blob, _ = read_gltf(library)
        lib_cache = {}
        for anim in lib_doc.get('animations', []):
            if anim.get('name') not in CLIPS:
                continue
            samplers, channels = [], []
            for ch in anim['channels']:
                target_name = lib_doc['nodes'][ch['target']['node']].get('name')
                if target_name not in names:
                    continue
                s = anim['samplers'][ch['sampler']]
                held = constant_track(lib_doc, lib_blob, s['output'])
                if held is not None:
                    samplers.append(held_sampler(doc, blob, lib_doc, s, held))
                else:
                    samplers.append({
                        'input': copy_accessor(lib_doc, lib_blob, doc, blob, s['input'], lib_cache),
                        'output': copy_accessor(lib_doc, lib_blob, doc, blob, s['output'], lib_cache),
                        'interpolation': s.get('interpolation', 'LINEAR'),
                    })
                channels.append({'sampler': len(samplers) - 1, 'target': {'node': names[target_name], 'path': ch['target']['path']}})
            doc['animations'].append({'name': anim['name'], 'samplers': samplers, 'channels': channels})
    missing = set(CLIPS) - {a['name'] for a in doc['animations']}
    if missing:
        sys.exit(f'missing clips: {missing}')

    # The skin texture picks light or dark; everything is shrunk for phones.
    skin_material = next(m['name'] for m in doc['materials'] if m['name'].startswith('MI_Superhero'))
    slim_materials(doc, blob, folder, CHARACTER_TEXTURE_MAX, image_override={skin_material: os.path.join('..', 'Textures', skin)})
    doc.pop('extensionsUsed', None)
    return write_glb(doc, blob, os.path.join(OUT, out_name + '.glb'))


def build_clothed(folder, name, out_name):
    """A clothed character: same meshes and skeleton, only the clips the game uses, packed compactly."""
    src, src_blob, _ = read_gltf(os.path.join(folder, name + '.gltf'))
    doc = {k: v for k, v in src.items() if k not in ('accessors', 'bufferViews', 'buffers', 'animations', 'meshes', 'skins')}
    blob = bytearray()
    cache = {}
    doc['meshes'] = []
    for mesh in src['meshes']:
        prims = []
        for p_ in mesh['primitives']:
            prim = dict(p_)
            prim['attributes'] = {k: copy_accessor(src, src_blob, doc, blob, v, cache) for k, v in p_['attributes'].items() if k in KEEP_ATTRIBUTES}
            if 'indices' in p_:
                prim['indices'] = copy_accessor(src, src_blob, doc, blob, p_['indices'], cache)
            prim.pop('targets', None)
            prims.append(prim)
        doc['meshes'].append({**mesh, 'primitives': prims})
    doc['skins'] = []
    for skin in src.get('skins', []):
        new = dict(skin)
        if 'inverseBindMatrices' in skin:
            new['inverseBindMatrices'] = copy_accessor(src, src_blob, doc, blob, skin['inverseBindMatrices'], cache)
        doc['skins'].append(new)
    doc['animations'] = []
    for anim in src.get('animations', []):
        if anim.get('name') not in CLOTHED_CLIPS:
            continue
        samplers, channels = [], []
        for ch in anim['channels']:
            smp = anim['samplers'][ch['sampler']]
            held = constant_track(src, src_blob, smp['output'])
            if held is not None:
                samplers.append(held_sampler(doc, blob, src, smp, held))
            else:
                samplers.append({
                    'input': copy_accessor(src, src_blob, doc, blob, smp['input'], cache),
                    'output': copy_accessor(src, src_blob, doc, blob, smp['output'], cache),
                    'interpolation': smp.get('interpolation', 'LINEAR'),
                })
            channels.append({'sampler': len(samplers) - 1, 'target': ch['target']})
        doc['animations'].append({'name': anim['name'], 'samplers': samplers, 'channels': channels})
    missing = set(CLOTHED_CLIPS) - {a['name'] for a in doc['animations']}
    if missing:
        sys.exit(f'{name}: missing clips {missing}')
    for m in doc.get('materials', []):
        pbr = m.setdefault('pbrMetallicRoughness', {})
        pbr['metallicFactor'] = 0.0
        pbr['roughnessFactor'] = 0.85
    return write_glb(doc, blob, os.path.join(OUT, out_name + '.glb'))


def read_mtl(path):
    colours, current = {}, None
    if not os.path.exists(path):
        return colours
    for line in open(path, encoding='utf-8', errors='replace'):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'newmtl':
            current = parts[1]
            colours[current] = [0.8, 0.8, 0.8]
        elif parts[0] == 'Kd' and current and len(parts) >= 4:
            colours[current] = [float(x) for x in parts[1:4]]
    return colours


def build_obj(src_path, out_name):
    """OBJ + MTL to glTF: positions, normals, one primitive per material colour."""
    positions, normals = [], []
    groups = {}  # material -> list of (vertex index, normal index)
    current = 'default'
    mtl = None
    for line in open(src_path, encoding='utf-8', errors='replace'):
        parts = line.split()
        if not parts:
            continue
        tag = parts[0]
        if tag == 'v':
            positions.append([float(x) for x in parts[1:4]])
        elif tag == 'vn':
            normals.append([float(x) for x in parts[1:4]])
        elif tag == 'usemtl':
            current = parts[1]
        elif tag == 'mtllib':
            mtl = ' '.join(parts[1:])
        elif tag == 'f':
            corners = []
            for c in parts[1:]:
                bits = c.split('/')
                v = int(bits[0]) - 1 if int(bits[0]) > 0 else len(positions) + int(bits[0])
                n = (int(bits[2]) - 1) if len(bits) > 2 and bits[2] else -1
                corners.append((v, n))
            for i in range(1, len(corners) - 1):  # fan-triangulate
                groups.setdefault(current, []).extend([corners[0], corners[i], corners[i + 1]])
    colours = read_mtl(os.path.join(os.path.dirname(src_path), mtl)) if mtl else {}

    doc = {'asset': {'version': '2.0', 'generator': 'our-little-life build_models.py'}, 'scene': 0,
           'scenes': [{'nodes': [0]}], 'nodes': [{'name': out_name, 'mesh': 0}], 'meshes': [{'name': out_name, 'primitives': []}],
           'materials': [], 'accessors': [], 'bufferViews': []}
    blob = bytearray()
    for material, corners in groups.items():
        # Unwelded vertices: each corner its own vertex, so flat-shaded normals stay crisp.
        pos = [positions[v] for v, _ in corners]
        nor = [normals[n] if 0 <= n < len(normals) else [0, 1, 0] for _, n in corners]
        pdata = b''.join(struct.pack('<3f', *p) for p in pos)
        ndata = b''.join(struct.pack('<3f', *n) for n in nor)
        mins = [min(p[i] for p in pos) for i in range(3)]
        maxs = [max(p[i] for p in pos) for i in range(3)]
        pv = add_view(doc, blob, pdata)
        doc['accessors'].append({'bufferView': pv, 'componentType': 5126, 'count': len(pos), 'type': 'VEC3', 'min': mins, 'max': maxs})
        nv = add_view(doc, blob, ndata)
        doc['accessors'].append({'bufferView': nv, 'componentType': 5126, 'count': len(nor), 'type': 'VEC3'})
        # Filament's loader expects indexed triangles, so give every corner its own index.
        count = len(pos)
        if count < 65536:
            idata, itype = struct.pack(f'<{count}H', *range(count)), 5123
        else:
            idata, itype = struct.pack(f'<{count}I', *range(count)), 5125
        iv = add_view(doc, blob, idata)
        doc['accessors'].append({'bufferView': iv, 'componentType': itype, 'count': count, 'type': 'SCALAR'})
        index_accessor = len(doc['accessors']) - 1
        kd = colours.get(material, [0.8, 0.8, 0.8])
        doc['materials'].append({'name': material, 'pbrMetallicRoughness': {'baseColorFactor': kd + [1.0], 'metallicFactor': 0.0, 'roughnessFactor': 0.85}})
        doc['meshes'][0]['primitives'].append({'attributes': {'POSITION': index_accessor - 2, 'NORMAL': index_accessor - 1},
                                               'indices': index_accessor, 'material': len(doc['materials']) - 1})
    return write_glb(doc, blob, os.path.join(OUT, out_name + '.glb'))


def main():
    os.makedirs(OUT, exist_ok=True)
    total = 0
    for name, src in NATURE.items():
        total += build_textured(os.path.join(MEGAKIT, src + '.gltf'), name)
    for name, src in CROP_MODELS.items():
        total += build_obj(os.path.join(CROPS, src + '.obj'), name)
    for name, src in FURNITURE_MODELS.items():
        total += build_obj(os.path.join(FURNITURE, src + '.obj'), name)
    for name, (folder, src) in CLOTHED.items():
        total += build_clothed(folder, src, name)
    # The free Universal Base Characters are bare bodies in underwear (clothes are in the paid edition),
    # so they aren't shipped. build_person stays for clothed characters on the same skeleton.
    if '--people' in sys.argv:
        for name, (body, skin, hair) in PEOPLE.items():
            total += build_person(name, body, skin, hair)
    for f in sorted(os.listdir(OUT)):
        print(f'{f:28s} {os.path.getsize(os.path.join(OUT, f)) / 1024:8.0f} KB')
    print(f'total {total / 1048576:.1f} MB')


if __name__ == '__main__':
    main()
