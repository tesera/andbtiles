package com.tesera.andbtiles.test;

import com.tesera.andbtiles.Andbtiles;
import com.tesera.andbtiles.exceptions.AndbtilesException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class AndbtilesTests {

    @Test
    public void testInstantiation() {
        Andbtiles andbtiles = new Andbtiles(Robolectric.getShadowApplication().getApplicationContext());
        assertNotNull(andbtiles);
    }

    @Test
    public void testMaps() {
        Andbtiles andbtiles = new Andbtiles(Robolectric.getShadowApplication().getApplicationContext());
        assertNotNull(andbtiles.getMaps());
    }

    @Test(expected = AndbtilesException.class)
    public void testWrongLocalProvider() throws AndbtilesException {
        Andbtiles andbtiles = new Andbtiles(Robolectric.getShadowApplication().getApplicationContext());
        andbtiles.addLocalMbTilesProvider("wrong_path_to_sd", "wrong_path_to_geo_json_file");
    }

    @Test(expected = AndbtilesException.class)
    public void testWrongTile() throws AndbtilesException {
        Andbtiles andbtiles = new Andbtiles(Robolectric.getShadowApplication().getApplicationContext());
        andbtiles.getTile("mapId", 0, 0, 0);
    }
}