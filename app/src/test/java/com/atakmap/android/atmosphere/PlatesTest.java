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

package com.atakmap.android.atmosphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.atakmap.android.atmosphere.data.Plates;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bundled PB2002 asset, checked as data: the counts the model publishes, every
 * geometry a kind the renderer draws, and — above all — no segment anywhere stepping
 * more than 180° of longitude, the ATAK rendering trap the asset was split to avoid.
 */
public class PlatesTest {

    private static String asset() throws Exception {
        // Unit tests run from the module directory or the repository root.
        File f = new File("src/main/assets/" + Plates.ASSET);
        if (!f.exists())
            f = new File("app/src/main/assets/" + Plates.ASSET);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void parsesTheWholeModel() throws Exception {
        final List<Plates.Feature> all = Plates.parse(asset());
        int boundaries = 0, outlines = 0, orogens = 0;
        final Map<String, Integer> byClass = new HashMap<>();
        for (Plates.Feature f : all) {
            if (f.kind == Plates.BOUNDARY) {
                boundaries++;
                final Integer n = byClass.get(f.cls);
                byClass.put(f.cls, n == null ? 1 : n + 1);
                assertFalse(f.name.isEmpty());
                assertFalse(f.pair.isEmpty());
            } else if (f.kind == Plates.OUTLINE) {
                outlines++;
                assertFalse(f.name.isEmpty());
            } else {
                orogens++;
                assertEquals("Polygon", f.geometry.getString("type"));
            }
        }
        // PB2002: 241 boundary segments, 52 plates (54 outline features: two plates
        // split into parts in the source), 13 orogens (14: one splits at the dateline).
        assertEquals(241, boundaries);
        assertEquals(54, outlines);
        assertEquals(14, orogens);
        // The dominant-STEPCLASS tally, with Type == subduction overriding.
        assertEquals(Integer.valueOf(65), byClass.get("SUB"));
        assertEquals(Integer.valueOf(66), byClass.get("OSR"));
        assertEquals(Integer.valueOf(29), byClass.get("OTF"));
        assertEquals(Integer.valueOf(24), byClass.get("CTF"));
        assertEquals(Integer.valueOf(23), byClass.get("CRB"));
        assertEquals(Integer.valueOf(17), byClass.get("CCB"));
        assertEquals(Integer.valueOf(17), byClass.get("OCB"));
    }

    @Test
    public void noSegmentCrossesTheAntimeridian() throws Exception {
        // ATAK draws a −180°→+180° segment the long way round the globe; one such
        // segment smears the whole Pacific. The asset must be pre-split.
        for (Plates.Feature f : Plates.parse(asset())) {
            final String type = f.geometry.getString("type");
            final JSONArray coords = f.geometry.getJSONArray("coordinates");
            if ("LineString".equals(type))
                assertNoJump(f.name, coords);
            else if ("MultiLineString".equals(type))
                for (int i = 0; i < coords.length(); i++)
                    assertNoJump(f.name, coords.getJSONArray(i));
            else if ("Polygon".equals(type))
                for (int i = 0; i < coords.length(); i++)
                    assertNoJump(f.name, coords.getJSONArray(i));
            else
                throw new AssertionError(f.name + ": unexpected geometry " + type);
        }
    }

    private static void assertNoJump(String name, JSONArray run) throws Exception {
        assertTrue(name + ": a run of fewer than two points", run.length() >= 2);
        for (int i = 1; i < run.length(); i++) {
            final double a = run.getJSONArray(i - 1).getDouble(0);
            final double b = run.getJSONArray(i).getDouble(0);
            assertTrue(name + ": segment jumps " + Math.abs(b - a) + "° of longitude",
                    Math.abs(b - a) <= 180);
        }
    }

    @Test
    public void legendCoversEveryClass() {
        for (String cls : Plates.CLASSES) {
            assertFalse(Plates.label(cls).isEmpty());
            assertTrue((Plates.color(cls) >>> 24) == 0xFF);
            assertTrue(Plates.weight(cls) > 0);
        }
        // An unknown class still draws, in the fallback gray.
        assertEquals("Unclassified boundary", Plates.label("???"));
        assertEquals(0xFFDDDDDD, Plates.color("???"));
    }

    @Test
    public void attributionRidesTheAsset() throws Exception {
        final JSONObject doc = new JSONObject(asset());
        assertTrue(doc.getString("model").contains("Bird"));
        assertTrue(doc.getString("source").contains("fraxen/tectonicplates"));
        assertTrue(doc.getString("source").contains("ODC-By"));
    }
}
