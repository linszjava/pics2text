#!/usr/bin/env python3
import json
import os
import sys

import pyvips


def load_source(photo, output_profile):
    source = pyvips.Image.new_from_file(photo["sourcePath"], access="sequential", fail_on="error").autorot()
    if source.width != photo["sourceWidth"] or source.height != photo["sourceHeight"]:
        raise RuntimeError(
            f'source dimensions changed for photo {photo["photoId"]}: '
            f'{source.width}x{source.height}')
    if source.get_typeof("icc-profile-data") != 0:
        source = source.icc_transform(output_profile, embedded=True, intent="perceptual")
    else:
        source = source.icc_transform(
            output_profile, input_profile=output_profile, embedded=False, intent="perceptual")
    crop = photo["crop"]
    left = max(0, min(source.width - 1, round(crop["x"] * source.width)))
    top = max(0, min(source.height - 1, round(crop["y"] * source.height)))
    width = max(1, min(source.width - left, round(crop["width"] * source.width)))
    height = max(1, min(source.height - top, round(crop["height"] * source.height)))
    return source.crop(left, top, width, height).thumbnail_image(
        photo["width"], height=photo["height"], size="force")


def render_tile(tile, photos_by_slot, work_dir, output_profile):
    bounds = tile["bounds"]
    image = pyvips.Image.black(bounds["width"], bounds["height"], bands=3).new_from_image([255, 255, 255])
    for slot_index in tile["slotIndexes"]:
        photo = photos_by_slot[slot_index]
        placed = load_source(photo, output_profile)
        left = max(photo["x"], bounds["x"])
        top = max(photo["y"], bounds["y"])
        right = min(photo["x"] + photo["width"], bounds["x"] + bounds["width"])
        bottom = min(photo["y"] + photo["height"], bounds["y"] + bounds["height"])
        fragment = placed.crop(left - photo["x"], top - photo["y"], right - left, bottom - top)
        image = image.insert(fragment, left - bounds["x"], top - bounds["y"], expand=False)
    path = os.path.join(work_dir, f'tile-{tile["row"]}-{tile["column"]}.v')
    image.write_to_file(path)
    return path


def render_rows(tiles, columns, photos, work_dir, output_profile):
    """Render and release one tile row at a time to bound disk and open-file use."""
    row_paths = []
    for row_number, start in enumerate(range(0, len(tiles), columns)):
        paths = [render_tile(tile, photos, work_dir, output_profile)
                 for tile in tiles[start:start + columns]]
        row = pyvips.Image.arrayjoin(
            [pyvips.Image.new_from_file(path, access="sequential") for path in paths],
            across=len(paths))
        row_path = os.path.join(work_dir, f"row-{row_number}.v")
        row.write_to_file(row_path)
        row_paths.append(row_path)
        for path in paths:
            os.unlink(path)
    return row_paths


def join_vertical_bounded(paths, work_dir, group_size=64):
    """Join through bounded intermediate strips to respect the native open-file limit."""
    level = 0
    current = paths
    while len(current) > group_size:
        next_level = []
        for group_number, start in enumerate(range(0, len(current), group_size)):
            group = current[start:start + group_size]
            strip = pyvips.Image.arrayjoin(
                [pyvips.Image.new_from_file(path, access="sequential") for path in group], across=1)
            strip_path = os.path.join(work_dir, f"strip-{level}-{group_number}.v")
            strip.write_to_file(strip_path)
            next_level.append(strip_path)
        current = next_level
        level += 1
    return pyvips.Image.arrayjoin(
        [pyvips.Image.new_from_file(path, access="sequential") for path in current], across=1)


def marks(image, spec):
    bleed = spec["bleedPx"]
    if bleed <= 0:
        return image
    x0, y0 = bleed, bleed
    x1 = bleed + spec["contentWidthPx"] - 1
    y1 = bleed + spec["contentHeightPx"] - 1
    length = max(2, min(bleed, round(spec["dpi"] / 8)))
    ink = [0, 0, 0]
    if spec.get("cropMarks"):
        for x in (x0, x1):
            image = image.draw_line(ink, x, max(0, y0 - length), x, y0 - 1)
            image = image.draw_line(ink, x, y1 + 1, x, min(image.height - 1, y1 + length))
        for y in (y0, y1):
            image = image.draw_line(ink, max(0, x0 - length), y, x0 - 1, y)
            image = image.draw_line(ink, x1 + 1, y, min(image.width - 1, x1 + length), y)
    if spec.get("registrationMarks"):
        cx, cy = image.width // 2, image.height // 2
        image = image.draw_line(ink, cx - length, max(0, y0 - length // 2), cx + length, max(0, y0 - length // 2))
        image = image.draw_line(ink, max(0, x0 - length // 2), cy - length,
                                max(0, x0 - length // 2), cy + length)
    return image


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: render_print.py SPEC OUTPUT")
    with open(sys.argv[1], "r", encoding="utf-8") as handle:
        spec = json.load(handle)
    pyvips.cache_set_max_mem(int(spec["nativeMemoryBytes"]))
    pyvips.cache_set_max_files(int(spec["maxOpenFiles"]))
    work_dir = os.path.dirname(sys.argv[1])
    profile = spec.get("iccProfilePath")
    if not profile or not os.path.isfile(profile):
        raise RuntimeError(f"ICC profile is missing: {profile}")
    photos = {photo["slotIndex"]: photo for photo in spec["photos"]}
    columns = max(tile["column"] for tile in spec["tiles"]) + 1
    rows = render_rows(spec["tiles"], columns, photos, work_dir, profile)
    output = join_vertical_bounded(rows, work_dir)
    output = marks(output, spec)
    options = dict(compression=spec["compression"], tile=True, tile_width=spec["tileSize"],
                   tile_height=spec["tileSize"], bigtiff=True, pyramid=False,
                   xres=spec["dpi"] / 25.4, yres=spec["dpi"] / 25.4, resunit="inch")
    options["profile"] = profile
    output.tiffsave(sys.argv[2], **options)
    print(f"LIBVIPS_VERSION={pyvips.version(0)}.{pyvips.version(1)}.{pyvips.version(2)}")


if __name__ == "__main__":
    main()
