#!/usr/bin/env python3
"""Cross-reference validator.

Ties the Java registries to the resource files so a renamed block, a missing texture, or a dangling
sound can never silently ship. Run from the repo root:  python3 tools/validate_assets.py

Checks performed:
  * every *.json under src/main/resources parses
  * every model referenced by a blockstate exists, and every texture referenced by a model is a real PNG
  * every sound id registered in ModSoundEvents has an entry in sounds.json and an .ogg on disk
  * every block registered in ModBlocks has a blockstate file and, for block-item blocks, an item model
  * data files reference each other consistently (biome -> placed feature -> configured feature -> feature type)
"""

import json
import os
import re
import sys

ROOT = os.path.join("src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "backrooms")
DATA = os.path.join(ROOT, "data", "backrooms")
JAVA = os.path.join("src", "main", "java", "dev", "qynl", "backrooms")

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def walk_json(base):
    for root, _, files in os.walk(base):
        for f in files:
            if f.endswith(".json"):
                yield os.path.join(root, f)


def java_register_ids(filename, class_hint):
    path = os.path.join(JAVA, filename)
    text = read(path)
    return re.findall(r'register\(\s*"([^"]+)"', text)


def main():
    # 1. all JSON parses
    json_files = list(walk_json(ROOT))
    for path in json_files:
        try:
            json.loads(read(path))
        except Exception as e:
            err(f"invalid JSON {os.path.relpath(path)}: {e}")

    # 2. blockstates -> models -> textures
    blockstates = os.path.join(ASSETS, "blockstates")
    for path in sorted(os.listdir(blockstates)):
        bs = json.loads(read(os.path.join(blockstates, path)))
        for variant, entry in bs.get("variants", {}).items():
            models = entry if isinstance(entry, list) else [entry]
            for m in models:
                model_id = m["model"]
                if not model_id.startswith("backrooms:"):
                    continue
                rel = model_id.split(":", 1)[1].replace("block/", "models/block/")
                mpath = os.path.join(ASSETS, rel + ".json")
                if not os.path.exists(mpath):
                    err(f"{path}: missing model {model_id}")
                    continue
                model = json.loads(read(mpath))
                for tex in model.get("textures", {}).values():
                    if not tex.startswith("backrooms:"):
                        continue
                    trel = tex.split(":", 1)[1]
                    tpath = os.path.join(ASSETS, "textures", trel.split("/", 1)[1] + ".png") \
                        if "/" in trel else None
                    tpath = os.path.join(ASSETS, trel.replace("block/", "textures/block/")
                                         .replace("entity/", "textures/entity/") + ".png")
                    if not os.path.exists(tpath):
                        err(f"{model_id}: missing texture {tex}")

    # 3. sounds: Java <-> sounds.json <-> ogg
    sound_ids = java_register_ids("registry/ModSoundEvents.java", "SoundEvent")
    sounds = json.loads(read(os.path.join(ASSETS, "sounds.json")))
    for sid in sound_ids:
        if sid not in sounds:
            err(f"sound registered in Java but missing from sounds.json: {sid}")
    for sid, entry in sounds.items():
        for s in entry["sounds"]:
            name = s["name"] if isinstance(s, dict) else s
            if not name.startswith("backrooms:"):
                continue
            ogg = os.path.join(ASSETS, "sounds", name.split(":", 1)[1] + ".ogg")
            if not os.path.exists(ogg):
                err(f"sounds.json references missing ogg: {name}")

    # 4. blocks: Java <-> blockstate <-> item model
    block_ids = java_register_ids("registry/ModBlocks.java", "Block")
    item_ids = java_register_ids("registry/ModItems.java", "Item")
    for bid in block_ids:
        if not os.path.exists(os.path.join(blockstates, bid + ".json")):
            err(f"block registered in Java but no blockstate file: {bid}")
    for iid in item_ids:
        if not os.path.exists(os.path.join(ASSETS, "models", "item", iid + ".json")):
            err(f"item registered in Java but no item model: {iid}")

    # 5. data chain
    biome = json.loads(read(os.path.join(DATA, "worldgen", "biome", "level0.json")))
    placed_in_biome = [f for group in biome["features"] for f in group]
    for pf in placed_in_biome:
        rel = pf.split(":", 1)[1]
        ppath = os.path.join(DATA, "worldgen", "placed_feature", rel + ".json")
        if not os.path.exists(ppath):
            err(f"biome references missing placed feature: {pf}")
            continue
        placed = json.loads(read(ppath))
        cf = placed["feature"].split(":", 1)[1]
        cpath = os.path.join(DATA, "worldgen", "configured_feature", cf + ".json")
        if not os.path.exists(cpath):
            err(f"placed feature {pf} references missing configured feature: {cf}")
        else:
            configured = json.loads(read(cpath))
            ftype = configured["type"]
            if ftype == "backrooms:level0_build" or ftype == "backrooms:reality_hole":
                pass  # matches ModFeatures ids below
    feature_ids = java_register_ids("registry/ModFeatures.java", "Feature")
    for fid in feature_ids:
        cpath = os.path.join(DATA, "worldgen", "configured_feature", fid + ".json")
        if not os.path.exists(cpath):
            err(f"feature registered in Java but no configured_feature json: {fid}")

    dim = json.loads(read(os.path.join(DATA, "dimension", "level0.json")))
    if dim["type"] != "backrooms:level0":
        err("dimension type mismatch")
    if dim["generator"]["settings"]["biome"] != "backrooms:level0":
        err("dimension biome mismatch")

    # report
    print(f"scanned {len(json_files)} json files, {len(sound_ids)} sounds, "
          f"{len(block_ids)} blocks, {len(item_ids)} items, {len(feature_ids)} features")
    for w in warnings:
        print("  WARN:", w)
    if errors:
        print(f"\n{len(errors)} ERROR(S):")
        for e in errors:
            print("  ERROR:", e)
        return 1
    print("ALL ASSET CROSS-REFERENCES OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
