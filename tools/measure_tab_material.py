"""Measure a screenshot of the three-band tab sample (Pillow and NumPy).

Hold the lens halfway between the left and center tabs. Supply the screen scale
and the vertical center of the plate in physical pixels. The gallery's center
band is 92 logical units wide on both platforms.
"""

import argparse
import json
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image, ImageCms


def pixels(path):
    with Image.open(path) as image:
        if profile := image.info.get("icc_profile"):
            image = ImageCms.profileToProfile(image, BytesIO(profile), ImageCms.createProfile("sRGB"), outputMode="RGB")
        return np.asarray(image.convert("RGB"), dtype=float)


def measure(image, scale, center_y):
    center_x = image.shape[1] / 2
    luminance = image @ [0.2126, 0.7152, 0.0722]

    def height(span, radius):
        columns = np.arange(span[0], span[1] + 0.1, 0.5)
        offsets = np.arange(*radius, 0.02)
        heights = []
        for column in columns:
            values = luminance[:, round(center_x + column * scale)]
            peaks = []
            for sign in (-1, 1):
                line = np.interp(center_y + sign * offsets * scale, np.arange(len(values)), values)
                peaks.append(offsets[np.argmax(line)])
            heights.append(sum(peaks))
        return round(float(np.median(heights)), 1)

    def boundary(position, row):
        center = center_x + position * scale
        xs = np.arange(round(center - 21 * scale), round(center + 21 * scale) + 1)
        top, bottom = (round(center_y + (row + dy) * scale) for dy in (-2, 2))
        line = np.median(image[top:bottom + 1, xs], axis=0)
        first, last = np.median(line[:6], axis=0), np.median(line[-6:], axis=0)
        axis = last - first
        if np.dot(axis, axis) < 100:
            raise ValueError("Both colored bands must be visible.")
        fraction = (line - first) @ axis / np.dot(axis, axis)
        positions = []
        for level in (0.1, 0.5, 0.9):
            matches = np.flatnonzero((fraction[:-1] <= level) & (fraction[1:] > level))
            if len(matches) == 0:
                raise ValueError("Could not locate a color boundary.")
            i = matches[np.argmin(abs(xs[matches] - center))]
            x = xs[i] + (level - fraction[i]) / (fraction[i + 1] - fraction[i])
            positions.append((x - center) / scale)
        return positions

    def seam(position):
        inside = np.median([boundary(position, row) for row in (-12, -6, 0, 6, 12)], axis=0)
        outside = boundary(position, -56)
        return {"offset": round(float(inside[1] - outside[1]), 2), "spread": round(float(inside[2] - inside[0]), 2)}

    def selected_ink(column):
        # Use the solid blue glyph interiors only. The mint band has more green
        # than blue and the indigo is below the selected symbol's blue level.
        x0, x1 = (round(center_x + (column + dx) * scale) for dx in (-12, 12))
        y0, y1 = (round(center_y + dy * scale) for dy in (-19, 4))
        region = image[y0:y1, x0:x1]
        core = region[1:-1, 1:-1]
        red, green, blue = np.moveaxis(core, 2, 0)
        mask = (blue >= 180) & (blue >= green - 2) & (blue > red + 65) & (green > red + 35)
        for neighbor in (region[:-2, 1:-1], region[2:, 1:-1], region[1:-1, :-2], region[1:-1, 2:]):
            mask &= np.max(abs(neighbor - core), axis=2) <= 3
        if mask.sum() < 10:
            raise ValueError("Park the blue lens between the left and center tabs.")
        return np.median(core[mask], axis=0).round().astype(int).tolist()

    return {
        "height": {"plate": height((-101, -96), (29.5, 35)), "lens": height((-62, -53), (33, 41)),
                   "inner_plate": height((-62, -53), (24, 32))},
        "band": {"left": seam(-46), "right": seam(46)},
        "selected_srgb": {"clock": selected_ink(-87), "bookmark": selected_ink(0)},
    }


def measure_outer_rim(image, scale, center_y):
    """Scan the unobstructed left cap on the indigo band at the clear setting."""
    light = image @ [0.2126, 0.7152, 0.0722]
    center_x = image.shape[1] / 2

    def sample(x, y):
        x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
        u, v = x - x0, y - y0
        top = light[y0, x0] * (1 - u) + light[y0, x0 + 1] * u
        bottom = light[y0 + 1, x0] * (1 - u) + light[y0 + 1, x0 + 1] * u
        return top * (1 - v) + bottom * v

    def crossing(points, values, threshold):
        hits = np.flatnonzero((values[1:] >= threshold) & (values[:-1] < threshold))
        if not len(hits):
            raise ValueError("The left cap must be visible against the indigo band.")
        i = hits[0]
        return points[i] + (threshold - values[i]) * (points[i + 1] - points[i]) / (values[i + 1] - values[i])

    def flat_edge(direction):
        edges = []
        travel = np.arange(-39, -26, .04)
        for column in (-102, -99, -96):
            x = round(center_x + column * scale)
            values = np.median(light[:, x-2:x+3], axis=1)
            line = np.interp(center_y + direction * travel * scale, np.arange(len(values)), values)
            threshold = (np.median(line[:9]) + np.median(line[-9:])) / 2
            edges.append(center_y + direction * scale * crossing(travel, line, threshold))
        return np.median(edges)

    top, bottom = flat_edge(1), flat_edge(-1)
    mid, radius = (top + bottom) / 2, (bottom - top) / 2
    xs = np.arange(center_x - 158 * scale, center_x - 103 * scale, .07 * scale)
    inside = light[round(mid - 2 * scale), round(center_x - 120 * scale)]

    def contour(level):
        values = sample(xs, mid - level * radius)
        return crossing(xs, values, (np.median(values[:18]) + inside) / 2)

    end = np.median([contour(level) for level in (-.06, -.03, 0, .03, .06)])
    inset = {str(level): round(float(((contour(level) + contour(-level)) / 2 - end) / scale), 2)
             for level in (.25, .5, .7071, .8, .9, .95)}
    glow = {}
    depth = np.arange(-2, 5.01, .1)  # Signed distance from the measured contour, in pt or dp.
    for angle in (0, 50, 90):
        if angle == 90:
            x, y, nx, ny = center_x - 100 * scale, top, 0, 1
        else:
            level = np.sin(np.deg2rad(angle))
            x, y = contour(level), mid - level * radius
            slope = (contour(level + .012) - contour(level - .012)) / (.024 * radius)
            nx = 1 / np.hypot(1, slope)
            ny = slope * nx
        line = np.median([sample(x + (nx * depth - ny * shift) * scale, y + (ny * depth + nx * shift) * scale)
                          for shift in (-.4, -.2, 0, .2, .4)], axis=0)
        base = np.median(line[(depth >= 3) & (depth <= 4.8)])
        peak = np.max(line[(depth >= -.35) & (depth <= 1.8)])
        glow[str(angle)] = round(float(peak - base), 1)
    # At this fixed stop the lifted lens intersects the upper plate rim over
    # uniform indigo. Follow that rim once per logical unit from left to right.
    ref_x = round(center_x - 105 * scale)
    outside = image[round(center_y - 50 * scale), ref_x]
    direction = image[round(center_y - 7 * scale), ref_x] - outside
    if np.dot(direction, direction) < 100:
        raise ValueError("The indigo band and the lifted plate must both be visible.")
    ys = np.arange(-40, -24, .04)
    edge_y = []
    for column in range(-89, -74):
        line = (image[:, round(center_x + column * scale)] - outside) @ direction / np.dot(direction, direction)
        line = np.interp(center_y + ys * scale, np.arange(len(line)), line)
        estimates = []
        for threshold in (.3, .4, .5, .6, .7):
            hits = np.flatnonzero((line[:-1] < threshold) & (line[1:] >= threshold))
            if len(hits):
                i = hits[-1]  # The outer lens can have a separate highlight above the plate.
                estimates.append(ys[i] + .04 * (threshold - line[i]) / (line[i+1] - line[i]))
        if not estimates:
            raise ValueError("Could not trace the upper plate rim through the lens.")
        edge_y.append(float(np.median(estimates)))
    return {
        "cap_inset_at_fraction_of_radius": inset, "rim_luminance_above_interior": glow,
        "upper_join": {"x_start_from_center": -89, "x_step": 1, "y_offsets": [round(n, 2) for n in edge_y],
                       "max_adjacent_change": round(float(np.max(np.abs(np.diff(edge_y)))), 2)},
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("screenshot", type=Path)
    parser.add_argument("--scale", required=True, type=float, help="physical pixels per point or dp")
    parser.add_argument("--center-y", required=True, type=float, help="plate center in screenshot pixels")
    parser.add_argument("--rim", action="store_true", help="also measure the outer rim at the clear setting")
    args = parser.parse_args()
    image = pixels(args.screenshot)
    result = measure(image, args.scale, args.center_y)
    if args.rim:
        result["outer_rim"] = measure_outer_rim(image, args.scale, args.center_y)
    print(json.dumps(result, indent=2))
