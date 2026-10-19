package com.micaftic.morpher.core.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouletteGeometryTest {
    @Test void straightEdgeDoesNotReactToTheRemovedCircularCorner(){
        double angle=-Math.PI/2+Math.PI/8;
        assertTrue(RouletteGeometry.contains(0,104*Math.cos(angle),104*Math.sin(angle)));
        assertFalse(RouletteGeometry.contains(0,146*Math.cos(angle),146*Math.sin(angle)));
        assertFalse(RouletteGeometry.contains(0,20*Math.cos(angle),20*Math.sin(angle)));
    }
    @Test void rasterizedPixelCentersAndClickTargetsDescribeTheSameEightSectors(){
        for(int sector=0;sector<8;sector++)for(int y=-152;y<=152;y++){
            int[] span=RouletteGeometry.span(RouletteGeometry.sector(sector,56,151),y);
            for(int x=-152;x<=152;x++)assertEquals(span!=null&&x>=span[0]&&x<span[1],
                    RouletteGeometry.contains(sector,x+.5,y+.5),"sector="+sector+" x="+x+" y="+y);
        }
    }
}
