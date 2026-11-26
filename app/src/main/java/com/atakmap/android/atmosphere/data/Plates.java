// SPDX-License-Identifier: AGPL-3.0-or-later
// Atmosphere — an ATAK plugin
// Copyright (C) 2026 Andreas Johansson (TAKWERX)
//
// This program is free software: you can redistribute it and/or modify it under
// the terms of the GNU Affero General Public License as published by the Free
// Software Foundation, either version 3 of the License, or (at your option) any
// later version, with the additional permission in LICENSE-EXCEPTION.md.
//
// This program is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
// PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

package com.atakmap.android.atmosphere.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The tectonic plates, bundled: the PB2002 model (Peter Bird, 2003), the standard
 * digital model of the plate boundaries, carried in the APK as an asset and never
 * fetched. The boundaries come classed — subduction, spreading ridge, transform,
 * convergent, rift — beside the plate outlines and the orogens, the regions
 * deforming too broadly for a single line.
 *
 * <p>The asset was derived from the model's GeoJSON conversion at
 * github.com/fraxen/tectonicplates (ODC-By 1.0). Every line in it was split
 * wherever a segment stepped more than 180° of longitude: ATAK draws a
 * −180°→+180° segment the long way round the globe, and the two features that
 * carried one (North America, Antarctica) smeared the whole Pacific at globe
 * zoom until they were cut at the antimeridian.
 */
public final class Plates {

    /** The bundled asset, in the plugin's own assets. */
    public static final String ASSET = "plate_boundaries.json";

    public static final int BOUNDARY = 0;
    public static final int OUTLINE = 1;
    public static final int OROGEN = 2;

    /** Boundary class codes, PB2002's own, in legend order. */
    public static final String[] CLASSES = { "SUB", "OCB", "CCB", "OSR", "CRB", "OTF", "CTF" };

    /** One feature of the model: a classed boundary, a plate outline or an orogen. */
    public static final class Feature {
        public final int kind;
        /** PB2002 class code for a boundary ("SUB"), empty otherwise. */
        public final String cls;
        public final String name;
        /** The plate pair for a boundary ("Pacific / North America"), empty otherwise. */
        public final String pair;
        /** The model's literature source for a boundary, empty otherwise. */
        public final String source;
        /** GeoJSON geometry: LineString or MultiLineString; Polygon for an orogen. */
        public final JSONObject geometry;

        Feature(int kind, String cls, String name, String pair, String source,
                JSONObject geometry) {
            this.kind = kind;
            this.cls = cls;
            this.name = name;
            this.pair = pair;
            this.source = source;
            this.geometry = geometry;
        }
    }

    private Plates() {
    }

    /** The class's name as the legend writes it. */
    public static String label(String cls) {
        switch (cls) {
            case "SUB": return "Subduction zone";
            case "OCB": return "Oceanic convergent";
            case "CCB": return "Continental convergent";
            case "OSR": return "Oceanic spreading ridge";
            case "CRB": return "Continental rift";
            case "OTF": return "Oceanic transform fault";
            case "CTF": return "Continental transform fault";
            default: return "Unclassified boundary";
        }
    }

    /** The class's line color, opaque ARGB. */
    public static int color(String cls) {
        switch (cls) {
            case "SUB": return 0xFFCC2222;
            case "OCB": return 0xFFEE6644;
            case "CCB": return 0xFFFF9933;
            case "OSR": return 0xFF39FF14;
            case "CRB": return 0xFF88DD44;
            case "OTF": return 0xFFFFE500;
            case "CTF": return 0xFFFFB300;
            default: return 0xFFDDDDDD;
        }
    }

    /** The class's line weight; the subduction zones draw heaviest. */
    public static float weight(String cls) {
        return "SUB".equals(cls) ? 3.5f : "OTF".equals(cls) ? 2f : 2.5f;
    }

    /** The plate outlines' color: white, translucent, under everything else. */
    public static final int OUTLINE_COLOR = 0x80FFFFFF;

    /** The orogens' edge and fill. */
    public static final int OROGEN_STROKE = 0xA0FFB300;
    public static final int OROGEN_FILL = 0x30FFB300;

    /** Reads the bundled asset; empty on any failure, never a throw. */
    public static List<Feature> load(Context pluginContext) {
        try {
            final InputStream in = pluginContext.getAssets().open(ASSET);
            try {
                final ByteArrayOutputStream buf = new ByteArrayOutputStream();
                final byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0)
                    buf.write(chunk, 0, n);
                return parse(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            } finally {
                in.close();
            }
        } catch (Exception e) {
            // A missing or unreadable asset draws nothing; it must never take ATAK down.
            return new ArrayList<>();
        }
    }

    /** Parses the asset's JSON; a bad record is skipped, never a throw for the lot. */
    public static List<Feature> parse(String body) {
        final List<Feature> out = new ArrayList<>();
        try {
            final JSONObject doc = new JSONObject(body);
            final JSONArray bs = doc.optJSONArray("boundaries");
            for (int i = 0; bs != null && i < bs.length(); i++) {
                final JSONObject f = bs.optJSONObject(i);
                if (f == null)
                    continue;
                final JSONObject g = f.optJSONObject("g");
                if (g == null)
                    continue;
                out.add(new Feature(BOUNDARY, f.optString("c", "UNK"), f.optString("n", ""),
                        f.optString("p", ""), f.optString("s", ""), g));
            }
            final JSONArray ps = doc.optJSONArray("outlines");
            for (int i = 0; ps != null && i < ps.length(); i++) {
                final JSONObject f = ps.optJSONObject(i);
                if (f == null)
                    continue;
                final JSONObject g = f.optJSONObject("g");
                if (g == null)
                    continue;
                out.add(new Feature(OUTLINE, "", f.optString("n", ""), "", "", g));
            }
            final JSONArray os = doc.optJSONArray("orogens");
            for (int i = 0; os != null && i < os.length(); i++) {
                final JSONObject f = os.optJSONObject(i);
                if (f == null)
                    continue;
                final JSONObject g = f.optJSONObject("g");
                if (g == null)
                    continue;
                out.add(new Feature(OROGEN, "", f.optString("n", ""), "", "", g));
            }
        } catch (Exception e) {
            // An unreadable document yields what was read before it broke; see load().
        }
        return out;
    }
}
