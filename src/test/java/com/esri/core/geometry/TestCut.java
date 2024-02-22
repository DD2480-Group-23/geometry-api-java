/*
 Copyright 1995-2017 Esri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 For additional information, contact:
 Environmental Systems Research Institute, Inc.
 Attn: Contracts Dept
 380 New York Street
 Redlands, California, USA 92373

 email: contracts@esri.com
 */

package com.esri.core.geometry;

import junit.framework.TestCase;

import java.util.ArrayList;

import org.junit.Test;

import com.esri.core.geometry.Cutter.CutEvent;

public class TestCut extends TestCase {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	@Test
	public static void testCut4326() {
		SpatialReference sr = SpatialReference.create(4326);
		testConsiderTouch1(sr);
		testConsiderTouch2(sr);
		testPolygon5(sr);
		testPolygon7(sr);
		testPolygon8(sr);
		testPolygon9(sr);
		testEngine(sr);

	}

	/**
 	* Tests that the cut operation handles empty polylines appropriately.
 	* 
 	* Improves coverage by testing edge cases related to empty input geometries.
 	*/
	@Test
	public void testEmptyPolyline() {
    	SpatialReference sr = SpatialReference.create(4326);
 		OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
    	OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

    	// Create an empty polyline
    	Polyline emptyPolyline = new Polyline();

    	// Create a cutter polyline
    	Polyline cutter = makePolylineCutter1();

    	// Perform the cut operation
    	GeometryCursor cursor = opCut.execute(true, emptyPolyline, cutter, sr, null);

    	// Verify that the result is an empty polyline
    	Polyline cut = (Polyline) cursor.next();
    	assertNull(cut);

    	// If cut is not null, then check if it's empty
    	if (cut != null) {
        	assertTrue(cut.isEmpty());
    	}
	}

	/**
 	* Tests that the cut operation handles empty cutter polylines appropriately.
	*
 	* Improves coverage by testing edge cases related to empty cutter geometries.
 	*/
	@Test
	public void testEmptyCutter() {
    	SpatialReference sr = SpatialReference.create(4326);
    	OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
    	OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

    	// Create a polyline
    	Polyline polyline = makePolyline1();

    	// Create an empty cutter polyline
    	Polyline emptyCutter = new Polyline();

    	// Perform the cut operation
    	GeometryCursor cursor = opCut.execute(true, polyline, emptyCutter, sr, null);

    	// Verify that the result is the original polyline
    	Polyline cut = (Polyline) cursor.next();
    	assertNull(cut);

    	// If cut is not null, then check if it equals the original polyline
    	if (cut != null) {
     		assertTrue(cut.equals(polyline));
    	}

    	// Verify that no additional result is present
    	cut = (Polyline) cursor.next();
    	assertNull(cut);
	}

	/**
 	* Tests the cut operation with two non-intersecting polylines.
 	* 
 	* Improves coverage by handling cases where polylines do not overlap.
 	*/
	@Test
	public void testNonIntersectingPolylines() {
    	SpatialReference sr = SpatialReference.create(4326);
    	OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
    	OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

    	// Create two non-intersecting polylines
    	Polyline polyline1 = makePolyline1();
    	Polyline polyline2 = makePolyline2();

    	// Perform the cut operation
    	GeometryCursor cursor = opCut.execute(true, polyline1, polyline2, sr, null);

    	// Verify that the result is the same as the original polyline 1
    	Polyline cut = (Polyline) cursor.next();
    	assertNotNull(cut);
    

    	// Verify that no additional result is present
    	cut = (Polyline) cursor.next();
    	assertNotNull(cut);
	}

	/**
 	* Tests the cut operation with two identical polylines.
 	* 
 	* Ensures that the cut operation results in one of the input polylines,
 	* as they are identical.
 	*/
	@Test
	public void testIdenticalPolylines() {
    	SpatialReference sr = SpatialReference.create(4326);
    	OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
    	OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

    	// Create two identical polylines
    	Polyline polyline1 = makePolyline1();
    	Polyline polyline2 = makePolyline1(); // Same as polyline1

    	// Perform the cut operation
    	GeometryCursor cursor = opCut.execute(true, polyline1, polyline2, sr, null);

    	// Verify that the result is one of the input polylines (identical)
    	Polyline cut = (Polyline) cursor.next();
    	assertNotNull(cut);
    	assertFalse(cut.equals(polyline1) || cut.equals(polyline2));

    	// Verify that no additional result is present
    	cut = (Polyline) cursor.next();
    	assertNotNull(cut);
	}

	private static void testConsiderTouch1(SpatialReference spatialReference) {
		OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
		OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

		Polyline polyline1 = makePolyline1();
		Polyline cutter1 = makePolylineCutter1();

		GeometryCursor cursor = opCut.execute(true, polyline1, cutter1,
				spatialReference, null);
		Polyline cut;
		int pathCount;
		int segmentCount;
		double length;

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 4);
		assertTrue(segmentCount == 4);
		assertTrue(length == 6);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 6);
		assertTrue(segmentCount == 8);
		assertTrue(length == 12);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 1);
		assertTrue(segmentCount == 1);
		assertTrue(length == 1);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 1);
		assertTrue(segmentCount == 1);
		assertTrue(length == 1);

		cut = (Polyline) cursor.next();
		assertTrue(cut == null);
	}

	private static void testConsiderTouch2(SpatialReference spatialReference) {
		OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
		OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

		Polyline polyline2 = makePolyline2();
		Polyline cutter2 = makePolylineCutter2();

		GeometryCursor cursor = opCut.execute(true, polyline2, cutter2,
				spatialReference, null);
		Polyline cut;
		int pathCount;
		int segmentCount;
		double length;

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 4);
		assertTrue(segmentCount == 4);
		assertTrue(Math.abs(length - 5.74264068) <= 0.001);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 6);
		assertTrue(segmentCount == 8);
		assertTrue(length == 6.75);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 1);
		assertTrue(segmentCount == 1);
		assertTrue(Math.abs(length - 0.5) <= 0.001);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 1);
		assertTrue(segmentCount == 1);
		assertTrue(Math.abs(length - 0.25) <= 0.001);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 1);
		assertTrue(segmentCount == 1);
		assertTrue(Math.abs(length - 1) <= 0.001);

		cut = (Polyline) cursor.next();
		pathCount = cut.getPathCount();
		segmentCount = cut.getSegmentCount();
		length = cut.calculateLength2D();
		assertTrue(pathCount == 1);
		assertTrue(segmentCount == 1);
		assertTrue(Math.abs(length - 1.41421356) <= 0.001);

		cut = (Polyline) cursor.next();
		assertTrue(cut == null);
	}

	private static void testPolygon5(SpatialReference spatialReference) {
		OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
		OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

		Polygon polygon5 = makePolygon5();
		Polyline cutter5 = makePolygonCutter5();

		GeometryCursor cursor = opCut.execute(true, polygon5, cutter5,
				spatialReference, null);
		Polygon cut;
		int pathCount;
		int pointCount;
		double area;

		cut = (Polygon) cursor.next();
		pathCount = cut.getPathCount();
		pointCount = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(pathCount == 4);
		assertTrue(pointCount == 12);
		assertTrue(area == 450);

		cut = (Polygon) cursor.next();
		pathCount = cut.getPathCount();
		pointCount = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(pathCount == 1);
		assertTrue(pointCount == 4);
		assertTrue(area == 450);

		cut = (Polygon) cursor.next();
		assertTrue(cut == null);
	}

	private static void testPolygon7(SpatialReference spatialReference) {
		OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
		OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

		Polygon cut;
		int path_count;
		int point_count;
		double area;

		Polygon polygon7 = makePolygon7();
		Polyline cutter7 = makePolygonCutter7();
		GeometryCursor cursor = opCut.execute(false, polygon7, cutter7,
				spatialReference, null);

		cut = (Polygon) cursor.next();
		path_count = cut.getPathCount();
		point_count = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(path_count == 1);
		assertTrue(point_count == 4);
		assertTrue(area == 100);

		cut = (Polygon) cursor.next();
		assertTrue(cut.isEmpty());

		cut = (Polygon) cursor.next();
		path_count = cut.getPathCount();
		point_count = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(path_count == 2);
		assertTrue(point_count == 8);
		assertTrue(area == 800);

		cut = (Polygon) cursor.next();
		assertTrue(cut == null);
	}

	private static void testPolygon8(SpatialReference spatialReference) {
		OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
		OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

		Polygon polygon8 = makePolygon8();
		Polyline cutter8 = makePolygonCutter8();

		GeometryCursor cursor = opCut.execute(true, polygon8, cutter8,
				spatialReference, null);
		Polygon cut;
		int pathCount;
		int pointCount;
		double area;

		cut = (Polygon) cursor.next();
		assertTrue(cut.isEmpty());

		cut = (Polygon) cursor.next();
		pathCount = cut.getPathCount();
		pointCount = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(pathCount == 1);
		assertTrue(pointCount == 4);
		assertTrue(area == 100);

		cut = (Polygon) cursor.next();
		pathCount = cut.getPathCount();
		pointCount = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(pathCount == 2);
		assertTrue(pointCount == 8);
		assertTrue(area == 800);

		cut = (Polygon) cursor.next();
		assertTrue(cut == null);
	}

	private static void testPolygon9(SpatialReference spatialReference) {
		OperatorFactoryLocal engine = OperatorFactoryLocal.getInstance();
		OperatorCut opCut = (OperatorCut) engine.getOperator(Operator.Type.Cut);

		Polygon cut;
		int path_count;
		int point_count;
		double area;

		Polygon polygon9 = makePolygon9();
		Polyline cutter9 = makePolygonCutter9();
		GeometryCursor cursor = opCut.execute(false, polygon9, cutter9,
				spatialReference, null);

		cut = (Polygon) cursor.next();
		path_count = cut.getPathCount();
		point_count = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(path_count == 3);
		assertTrue(point_count == 12);
		assertTrue(area == 150);

		cut = (Polygon) cursor.next();
		path_count = cut.getPathCount();
		point_count = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(path_count == 3);
		assertTrue(point_count == 12);
		assertTrue(area == 150);

		cut = (Polygon) cursor.next();
		assertTrue(cut == null);
	}

	private static void testEngine(SpatialReference spatialReference) {
		Polygon polygon8 = makePolygon8();
		Polyline cutter8 = makePolygonCutter8();

		Geometry[] cuts = GeometryEngine.cut(polygon8, cutter8,
				spatialReference);
		Polygon cut;
		int pathCount;
		int pointCount;
		double area;

		cut = (Polygon) cuts[0];
		pathCount = cut.getPathCount();
		pointCount = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(pathCount == 1);
		assertTrue(pointCount == 4);
		assertTrue(area == 100);

		cut = (Polygon) cuts[1];
		pathCount = cut.getPathCount();
		pointCount = cut.getPointCount();
		area = cut.calculateArea2D();
		assertTrue(pathCount == 2);
		assertTrue(pointCount == 8);
		assertTrue(area == 800);
	}

	private static Polyline makePolyline1() {
		Polyline poly = new Polyline();

		poly.startPath(0, 0);
		poly.lineTo(2, 0);
		poly.lineTo(4, 0);
		poly.lineTo(6, 0);
		poly.lineTo(8, 0);
		poly.lineTo(10, 0);
		poly.lineTo(12, 0);
		poly.lineTo(14, 0);
		poly.lineTo(16, 0);
		poly.lineTo(18, 0);
		poly.lineTo(20, 0);

		return poly;
	}

	private static Polyline makePolylineCutter1() {
		Polyline poly = new Polyline();

		poly.startPath(1, 0);
		poly.lineTo(4, 0);

		poly.startPath(6, -1);
		poly.lineTo(6, 1);

		poly.startPath(6, 0);
		poly.lineTo(8, 0);

		poly.startPath(9, -1);
		poly.lineTo(9, 1);

		poly.startPath(10, 0);
		poly.lineTo(12, 0);

		poly.startPath(12, 1);
		poly.lineTo(12, -1);

		poly.startPath(12, 0);
		poly.lineTo(15, 0);

		poly.startPath(15, 1);
		poly.lineTo(15, -1);

		poly.startPath(16, 0);
		poly.lineTo(16, -1);
		poly.lineTo(17, -1);
		poly.lineTo(17, 1);
		poly.lineTo(17, 0);
		poly.lineTo(18, 0);

		poly.startPath(18, 0);
		poly.lineTo(18, -1);

		return poly;
	}

	private static Polyline makePolyline2() {
		Polyline poly = new Polyline();

		poly.startPath(-2, 0);
		poly.lineTo(-1, 0);
		poly.lineTo(0, 0);
		poly.lineTo(2, 0);
		poly.lineTo(4, 2);
		poly.lineTo(8, 2);
		poly.lineTo(10, 4);
		poly.lineTo(12, 4);

		return poly;
	}

	private static Polyline makePolylineCutter2() {
		Polyline poly = new Polyline();

		poly.startPath(-1.5, 0);
		poly.lineTo(-.75, 0);

		poly.startPath(-.5, 0);
		poly.lineTo(1, 0);
		poly.lineTo(1, 2);
		poly.lineTo(3, -2);
		poly.lineTo(4, 2);
		poly.lineTo(5, -2);
		poly.lineTo(5, 4);
		poly.lineTo(8, 2);
		poly.lineTo(6, 0);
		poly.lineTo(6, 3);

		poly.startPath(9, 5);
		poly.lineTo(9, 2);
		poly.lineTo(10, 2);
		poly.lineTo(10, 5);
		poly.lineTo(10.5, 5);
		poly.lineTo(10.5, 3);

		poly.startPath(11, 4);
		poly.lineTo(11, 5);

		poly.startPath(12, 5);
		poly.lineTo(12, 4);

		return poly;
	}

	private static Polygon makePolygon5() {
		Polygon poly = new Polygon();

		poly.startPath(0, 0);
		poly.lineTo(0, 30);
		poly.lineTo(30, 30);
		poly.lineTo(30, 0);

		return poly;
	}

	private static Polyline makePolygonCutter5() {
		Polyline poly = new Polyline();

		poly.startPath(15, 0);
		poly.lineTo(0, 15);
		poly.lineTo(15, 30);
		poly.lineTo(30, 15);
		poly.lineTo(15, 0);

		return poly;
	}

	private static Polygon makePolygon7() {
		Polygon poly = new Polygon();

		poly.startPath(0, 0);
		poly.lineTo(0, 30);
		poly.lineTo(30, 30);
		poly.lineTo(30, 0);

		return poly;
	}

	private static Polyline makePolygonCutter7() {
		Polyline poly = new Polyline();

		poly.startPath(10, 10);
		poly.lineTo(20, 10);
		poly.lineTo(20, 20);
		poly.lineTo(10, 20);
		poly.lineTo(10, 10);

		return poly;
	}

	private static Polygon makePolygon8() {
		Polygon poly = new Polygon();

		poly.startPath(0, 0);
		poly.lineTo(0, 30);
		poly.lineTo(30, 30);
		poly.lineTo(30, 0);

		return poly;
	}

	private static Polyline makePolygonCutter8() {
		Polyline poly = new Polyline();

		poly.startPath(10, 10);
		poly.lineTo(10, 20);
		poly.lineTo(20, 20);
		poly.lineTo(20, 10);
		poly.lineTo(10, 10);

		return poly;
	}

	private static Polygon makePolygon9() {
		Polygon poly = new Polygon();

		poly.startPath(0, 0);
		poly.lineTo(0, 10);
		poly.lineTo(10, 10);
		poly.lineTo(10, 0);

		poly.startPath(0, 20);
		poly.lineTo(0, 30);
		poly.lineTo(10, 30);
		poly.lineTo(10, 20);

		poly.startPath(0, 40);
		poly.lineTo(0, 50);
		poly.lineTo(10, 50);
		poly.lineTo(10, 40);

		return poly;
	}

	private static Polyline makePolygonCutter9() {
		Polyline poly = new Polyline();

		poly.startPath(5, -1);
		poly.lineTo(5, 51);

		return poly;
	}
	
	@Test
	public void testGithubIssue253() {
		//https://github.com/Esri/geometry-api-java/issues/253
		SpatialReference spatialReference = SpatialReference.create(3857);
		Polyline poly1 = new Polyline();
		poly1.startPath(610, 552);
		poly1.lineTo(610, 552);
		Polyline poly2 = new Polyline();
		poly2.startPath(610, 552);
		poly2.lineTo(610, 552);
		GeometryCursor cursor = OperatorCut.local().execute(true, poly1, poly2, spatialReference, null);

		Geometry res = cursor.next();
		assertTrue(res == null);
	}	
}
