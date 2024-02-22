/*
 Copyright 1995-2015 Esri

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

import java.io.File;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * OperatorImportFromWkbLocal implementation.
 */
class OperatorImportFromWkbLocal extends OperatorImportFromWkb {

	static final class WkbHelper {
		WkbHelper(ByteBuffer buffer) {
			wkbBuffer = buffer;
			adjustment = 0;
		}

		int getInt(int offset) {
			return wkbBuffer.getInt(adjustment + offset);
		}

		double getDouble(int offset) {
			return wkbBuffer.getDouble(adjustment + offset);
		}

		ByteBuffer wkbBuffer;
		int adjustment;
	}

	static private void coverageHelper(String id) {
		String tempFilePath = "target/temp/coverage_importFromWkbPolygon.txt";

		// Create a File object with the specified path
		File tempFile = new File(tempFilePath);

		try {
			// Check if the file exists
			if (!tempFile.exists()) {
				// Create the file if it doesn't exist
				if (tempFile.getParentFile() != null) {
					tempFile.getParentFile().mkdirs(); // Create parent directories if necessary
				}
				tempFile.createNewFile(); // Create the file
				System.out.println("Temporary file created at: " + tempFile.getAbsolutePath());
			}
			FileWriter writer = new FileWriter(tempFile, true);
			// Write the new content to the file
			writer.write(id);
			writer.write(System.lineSeparator()); // Add a newline after the new content

			// Close the FileWriter
			writer.close();

		} catch (Exception e) {
			System.err.println(e.getMessage());
		}

	}

	@Override
	public Geometry execute(int importFlags, Geometry.Type type,
			ByteBuffer wkbBuffer, ProgressTracker progress_tracker) {

		ByteOrder initialOrder = wkbBuffer.order();

		// read byte ordering
		int byteOrder = wkbBuffer.get(0);

		if (byteOrder == WkbByteOrder.wkbNDR)
			wkbBuffer.order(ByteOrder.LITTLE_ENDIAN);
		else
			wkbBuffer.order(ByteOrder.BIG_ENDIAN);

		WkbHelper wkbHelper = new WkbHelper(wkbBuffer);

		try {
			return importFromWkb(importFlags, type, wkbHelper);
		} finally {
			wkbBuffer.order(initialOrder);
		}
	}

	@Override
	public OGCStructure executeOGC(int importFlags, ByteBuffer wkbBuffer,
			ProgressTracker progress_tracker) {

		ByteOrder initialOrder = wkbBuffer.order();

		// read byte ordering
		int byteOrder = wkbBuffer.get(0);

		if (byteOrder == WkbByteOrder.wkbNDR)
			wkbBuffer.order(ByteOrder.LITTLE_ENDIAN);
		else
			wkbBuffer.order(ByteOrder.BIG_ENDIAN);

		ArrayList<OGCStructure> stack = new ArrayList<OGCStructure>(0);
		AttributeStreamOfInt32 numGeometries = new AttributeStreamOfInt32(0);
		AttributeStreamOfInt32 indices = new AttributeStreamOfInt32(0);
		WkbHelper wkbHelper = new WkbHelper(wkbBuffer);

		OGCStructure root = new OGCStructure();
		root.m_structures = new ArrayList<OGCStructure>(0);
		stack.add(root); // add dummy root
		numGeometries.add(1);
		indices.add(0);

		boolean bCheckConsistentAttributes = false;
		boolean bHasZs = false;
		boolean bHasMs = false;

		try {

			while (!stack.isEmpty()) {

				if (indices.getLast() == numGeometries.getLast()) {
					stack.remove(stack.size() - 1);
					indices.removeLast();
					numGeometries.removeLast();
					continue;
				}

				OGCStructure last = stack.get(stack.size() - 1);
				indices.write(indices.size() - 1, indices.getLast() + 1);
				Geometry geometry;

				int wkbType = wkbHelper.getInt(1);
				int ogcType;

				// strip away attributes from type identifier

				if (wkbType > 3000) {
					ogcType = wkbType - 3000;

					if (bCheckConsistentAttributes) {
						if (!bHasZs || !bHasMs)
							throw new IllegalArgumentException();
					} else {
						bHasZs = true;
						bHasMs = true;
						bCheckConsistentAttributes = true;
					}
				} else if (wkbType > 2000) {
					ogcType = wkbType - 2000;

					if (bCheckConsistentAttributes) {
						if (bHasZs || !bHasMs)
							throw new IllegalArgumentException();
					} else {
						bHasZs = false;
						bHasMs = true;
						bCheckConsistentAttributes = true;
					}
				} else if (wkbType > 1000) {
					ogcType = wkbType - 1000;

					if (bCheckConsistentAttributes) {
						if (!bHasZs || bHasMs)
							throw new IllegalArgumentException();
					} else {
						bHasZs = true;
						bHasMs = false;
						bCheckConsistentAttributes = true;
					}
				} else {
					ogcType = wkbType;

					if (bCheckConsistentAttributes) {
						if (bHasZs || bHasMs)
							throw new IllegalArgumentException();
					} else {
						bHasZs = false;
						bHasMs = false;
						bCheckConsistentAttributes = true;
					}
				}
				if (ogcType == 7) {
					int count = wkbHelper.getInt(5);
					wkbHelper.adjustment += 9;

					OGCStructure next = new OGCStructure();
					next.m_type = ogcType;
					next.m_structures = new ArrayList<OGCStructure>(0);
					last.m_structures.add(next);
					stack.add(next);
					indices.add(0);
					numGeometries.add(count);
				} else {
					geometry = importFromWkb(importFlags,
							Geometry.Type.Unknown, wkbHelper);
					OGCStructure leaf = new OGCStructure();
					leaf.m_type = ogcType;
					leaf.m_geometry = geometry;
					last.m_structures.add(leaf);
				}
			}
		} finally {
			wkbBuffer.order(initialOrder);
		}

		return root;
	}

	private static Geometry importFromWkb(int importFlags, Geometry.Type type,
			WkbHelper wkbHelper) {

		// read type
		int wkbType = wkbHelper.getInt(1);

		switch (wkbType) {
			case WkbGeometryType.wkbPolygon:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(false, importFlags, false, false,
						wkbHelper);

			case WkbGeometryType.wkbPolygonM:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(false, importFlags, false, true,
						wkbHelper);

			case WkbGeometryType.wkbPolygonZ:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(false, importFlags, true, false,
						wkbHelper);

			case WkbGeometryType.wkbPolygonZM:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(false, importFlags, true, true,
						wkbHelper);

			case WkbGeometryType.wkbMultiPolygon:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(true, importFlags, false, false,
						wkbHelper);

			case WkbGeometryType.wkbMultiPolygonM:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(true, importFlags, false, true,
						wkbHelper);

			case WkbGeometryType.wkbMultiPolygonZ:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(true, importFlags, true, false,
						wkbHelper);

			case WkbGeometryType.wkbMultiPolygonZM:
				if (type.value() != Geometry.GeometryType.Polygon
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolygon(true, importFlags, true, true,
						wkbHelper);

			case WkbGeometryType.wkbLineString:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(false, importFlags, false, false,
						wkbHelper);

			case WkbGeometryType.wkbLineStringM:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(false, importFlags, false, true,
						wkbHelper);

			case WkbGeometryType.wkbLineStringZ:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(false, importFlags, true, false,
						wkbHelper);

			case WkbGeometryType.wkbLineStringZM:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(false, importFlags, true, true,
						wkbHelper);

			case WkbGeometryType.wkbMultiLineString:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(true, importFlags, false, false,
						wkbHelper);

			case WkbGeometryType.wkbMultiLineStringM:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(true, importFlags, false, true,
						wkbHelper);

			case WkbGeometryType.wkbMultiLineStringZ:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(true, importFlags, true, false,
						wkbHelper);

			case WkbGeometryType.wkbMultiLineStringZM:
				if (type.value() != Geometry.GeometryType.Polyline
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPolyline(true, importFlags, true, true,
						wkbHelper);

			case WkbGeometryType.wkbMultiPoint:
				if (type.value() != Geometry.GeometryType.MultiPoint
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbMultiPoint(importFlags, false, false, wkbHelper);

			case WkbGeometryType.wkbMultiPointM:
				if (type.value() != Geometry.GeometryType.MultiPoint
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbMultiPoint(importFlags, false, true, wkbHelper);

			case WkbGeometryType.wkbMultiPointZ:
				if (type.value() != Geometry.GeometryType.MultiPoint
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbMultiPoint(importFlags, true, false, wkbHelper);

			case WkbGeometryType.wkbMultiPointZM:
				if (type.value() != Geometry.GeometryType.MultiPoint
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbMultiPoint(importFlags, true, true, wkbHelper);

			case WkbGeometryType.wkbPoint:
				if (type.value() != Geometry.GeometryType.Point
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPoint(importFlags, false, false, wkbHelper);

			case WkbGeometryType.wkbPointM:
				if (type.value() != Geometry.GeometryType.Point
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPoint(importFlags, false, true, wkbHelper);

			case WkbGeometryType.wkbPointZ:
				if (type.value() != Geometry.GeometryType.Point
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPoint(importFlags, true, false, wkbHelper);

			case WkbGeometryType.wkbPointZM:
				if (type.value() != Geometry.GeometryType.Point
						&& type.value() != Geometry.GeometryType.Unknown)
					throw new GeometryException("invalid shape type");
				return importFromWkbPoint(importFlags, true, true, wkbHelper);

			default:
				throw new GeometryException("invalid shape type");
		}
	}

	private static int[] findTotalPointAndPartCount(int offset, int polygonCount, boolean bZs, boolean bMs,
			WkbHelper wkbHelper) {
		int point_count = 0;
		int partCount = 0;
		int tempOffset = offset;
		for (int ipolygon = 0; ipolygon < polygonCount; ipolygon++) {
			tempOffset += 5; // skip redundant byte order and type fields
			int ipartcount = wkbHelper.getInt(tempOffset);
			tempOffset += 4;

			for (int ipart = 0; ipart < ipartcount; ipart++) {
				int ipointcount = wkbHelper.getInt(tempOffset);
				tempOffset += 4;

				// If ipointcount == 0, then we have an empty part
				if (ipointcount == 0) {
					continue;
				}

				if (ipointcount <= 2) {
					tempOffset += ipointcount * 2 * 8;

					if (bZs) {
						tempOffset += ipointcount * 8;
					}
					if (bMs) {
						tempOffset += ipointcount * 8;
					}
					if (ipointcount == 1) {
						point_count += ipointcount + 1;
					} else {
						point_count += ipointcount;
					}
					partCount++;

					continue;
				}

				double startx = wkbHelper.getDouble(tempOffset);
				tempOffset += 8;
				double starty = wkbHelper.getDouble(tempOffset);
				tempOffset += 8;
				double startz = NumberUtils.TheNaN;
				double startm = NumberUtils.TheNaN;

				if (bZs) {
					startz = wkbHelper.getDouble(tempOffset);
					tempOffset += 8;
				}
				if (bMs) {
					startm = wkbHelper.getDouble(tempOffset);
					tempOffset += 8;
				}

				tempOffset += (ipointcount - 2) * 2 * 8;

				if (bZs) {
					tempOffset += (ipointcount - 2) * 8;
				}
				if (bMs) {
					tempOffset += (ipointcount - 2) * 8;
				}
				double endx = wkbHelper.getDouble(tempOffset);
				tempOffset += 8;
				double endy = wkbHelper.getDouble(tempOffset);
				tempOffset += 8;
				double endz = NumberUtils.TheNaN;
				double endm = NumberUtils.TheNaN;

				if (bZs) {
					endz = wkbHelper.getDouble(tempOffset);
					tempOffset += 8;
				}
				if (bMs) {
					endm = wkbHelper.getDouble(tempOffset);
					tempOffset += 8;
				}

				if ((startx == endx || (NumberUtils.isNaN(startx) && NumberUtils
						.isNaN(endx)))
						&& (starty == endy || (NumberUtils.isNaN(starty) && NumberUtils
								.isNaN(endy)))
						&& (!bZs || startz == endz || (NumberUtils
								.isNaN(startz) && NumberUtils.isNaN(endz)))
						&& (!bMs || startm == endm || (NumberUtils
								.isNaN(startm) && NumberUtils.isNaN(endm)))) {
					point_count += ipointcount - 1;
				} else {
					point_count += ipointcount;
				}
				partCount++;
			}
		}
		int[] returnArray = { point_count, partCount };
		return returnArray;
	}

	public static int[] checkStartEqualsEnd(int tempOffset, int ipointcount, int ipartend, boolean bSkipLastPoint,
			boolean bZs, boolean bMs,
			WkbHelper wkbHelper) {

		double startx = wkbHelper.getDouble(tempOffset);
		tempOffset += 8;
		double starty = wkbHelper.getDouble(tempOffset);
		tempOffset += 8;
		double startz = NumberUtils.TheNaN;
		double startm = NumberUtils.TheNaN;

		if (bZs) {
			startz = wkbHelper.getDouble(tempOffset);
			tempOffset += 8;
		}
		if (bMs) {
			startm = wkbHelper.getDouble(tempOffset);
			tempOffset += 8;
		}

		tempOffset += (ipointcount - 2) * 2 * 8;

		if (bZs) {
			tempOffset += (ipointcount - 2) * 8;
		}
		if (bMs) {
			tempOffset += (ipointcount - 2) * 8;
		}
		double endx = wkbHelper.getDouble(tempOffset);
		tempOffset += 8;
		double endy = wkbHelper.getDouble(tempOffset);
		tempOffset += 8;
		double endz = NumberUtils.TheNaN;
		double endm = NumberUtils.TheNaN;

		if (bZs) {
			endz = wkbHelper.getDouble(tempOffset);
			tempOffset += 8;
		}
		if (bMs) {
			endm = wkbHelper.getDouble(tempOffset);
			tempOffset += 8;
		}

		if ((startx == endx || (NumberUtils.isNaN(startx) && NumberUtils
				.isNaN(endx)))
				&& (starty == endy || (NumberUtils.isNaN(starty) && NumberUtils
						.isNaN(endy)))
				&& (!bZs || startz == endz || (NumberUtils
						.isNaN(startz) && NumberUtils.isNaN(endz)))
				&& (!bMs || startm == endm || (NumberUtils
						.isNaN(startm) && NumberUtils.isNaN(endm)))) {
			ipartend--;
		} else {
			bSkipLastPoint = false;
		}
		int[] returnArray = { ipartend, (bSkipLastPoint ? 1 : 0) };
		return returnArray;
	}

	// ----------------- Cyclomatic Complexity Count --------------------
	// Before Refactoring: 81 (both Lizard and manual count)
	// After refactoring:
	// Lizard: 34
	// Manual Count:
	// Decisions: If: 29, ?: 0, For: 4, While: 0, ||: 0, &&: 0, case: 0 = 33
	// Exit points: return: 1, throw: 0
	// CC: 33 - 1 + 2 = 34
	// Improvement: 58%
	private static Geometry importFromWkbPolygon(boolean bMultiPolygon,
			int importFlags, boolean bZs, boolean bMs, WkbHelper wkbHelper) {
		coverageHelper("0");
		int offset;
		int polygonCount;

		if (bMultiPolygon) {
			coverageHelper("1");
			polygonCount = wkbHelper.getInt(5);
			offset = 9;
		} else {
			coverageHelper("2");
			polygonCount = 1;
			offset = 0;
		}

		// Find total point count and part count
		int[] return_array = findTotalPointAndPartCount(offset, polygonCount, bZs, bMs, wkbHelper);
		int point_count = return_array[0];
		int partCount = return_array[1];

		AttributeStreamOfDbl position = null;
		AttributeStreamOfDbl zs = null;
		AttributeStreamOfDbl ms = null;
		AttributeStreamOfInt32 parts = null;
		AttributeStreamOfInt8 pathFlags = null;

		Geometry newPolygon;
		MultiPathImpl polygon;

		newPolygon = new Polygon();
		polygon = (MultiPathImpl) newPolygon._getImpl();

		if (bZs) {
			coverageHelper("3");
			polygon.addAttribute(VertexDescription.Semantics.Z);
		} else
			coverageHelper("4");
		if (bMs) {
			coverageHelper("5");
			polygon.addAttribute(VertexDescription.Semantics.M);
		} else
			coverageHelper("6");
		if (point_count > 0) {
			coverageHelper("7");
			parts = (AttributeStreamOfInt32) (AttributeStreamBase
					.createIndexStream(partCount + 1, 0));
			pathFlags = (AttributeStreamOfInt8) (AttributeStreamBase
					.createByteStream(parts.size(), (byte) PathFlags.enumClosed));
			position = (AttributeStreamOfDbl) (AttributeStreamBase
					.createAttributeStreamWithSemantics(
							VertexDescription.Semantics.POSITION, point_count));

			if (bZs) {
				coverageHelper("8");
				zs = (AttributeStreamOfDbl) (AttributeStreamBase
						.createAttributeStreamWithSemantics(
								VertexDescription.Semantics.Z, point_count));
			} else
				coverageHelper("9");
			if (bMs) {
				coverageHelper("10");
				ms = (AttributeStreamOfDbl) (AttributeStreamBase
						.createAttributeStreamWithSemantics(
								VertexDescription.Semantics.M, point_count));
			} else
				coverageHelper("11");
		} else
			coverageHelper("12");

		boolean bCreateMs = false, bCreateZs = false;
		int ipartend = 0;
		int ipolygonend = 0;
		int part_index = 0;

		// read Coordinates

		for (int ipolygon = 0; ipolygon < polygonCount; ipolygon++) {
			coverageHelper("13");
			offset += 5; // skip redundant byte order and type fields
			int ipartcount = wkbHelper.getInt(offset);
			offset += 4;
			int ipolygonstart = ipolygonend;
			ipolygonend = ipolygonstart + ipartcount;

			for (int ipart = ipolygonstart; ipart < ipolygonend; ipart++) {
				coverageHelper("14");
				int ipointcount = wkbHelper.getInt(offset);
				offset += 4;

				if (ipointcount == 0) {
					coverageHelper("15");
					continue;
				} else
					coverageHelper("16");
				int ipartstart = ipartend;
				ipartend += ipointcount;
				boolean bSkipLastPoint = true;

				if (ipointcount == 1) {
					coverageHelper("17");
					ipartstart++;
					ipartend++;
					bSkipLastPoint = false;
				} else if (ipointcount == 2) {
					coverageHelper("18");
					bSkipLastPoint = false;
				} else {
					coverageHelper("19");
					// Check if start point is equal to end point
					int[] returnArray = checkStartEqualsEnd(offset, ipointcount, ipartend, bSkipLastPoint, bZs, bMs,
							wkbHelper);
					ipartend = returnArray[0];
					bSkipLastPoint = (returnArray[1] == 1);
				}

				if (ipart == ipolygonstart) {
					coverageHelper("20");
					pathFlags.setBits(ipart,
							(byte) PathFlags.enumOGCStartPolygon);
				} else
					coverageHelper("21");
				parts.write(++part_index, ipartend);

				// We must write from the buffer backwards - ogc polygon
				// format is opposite of shapefile format
				for (int i = ipartstart; i < ipartend; i++) {
					coverageHelper("22");
					double x = wkbHelper.getDouble(offset);
					offset += 8;
					double y = wkbHelper.getDouble(offset);
					offset += 8;

					position.write(2 * i, x);
					position.write(2 * i + 1, y);

					if (bZs) {
						coverageHelper("23");
						double z = wkbHelper.getDouble(offset);
						offset += 8;

						zs.write(i, z);
						if (!VertexDescription.isDefaultValue(
								VertexDescription.Semantics.Z, z)) {
							coverageHelper("24");
							bCreateZs = true;
						} else
							coverageHelper("25");
					} else
						coverageHelper("26");

					if (bMs) {
						coverageHelper("27");
						double m = wkbHelper.getDouble(offset);
						offset += 8;

						ms.write(i, m);
						if (!VertexDescription.isDefaultValue(
								VertexDescription.Semantics.M, m)) {
							coverageHelper("28");
							bCreateMs = true;
						} else
							coverageHelper("29");
					} else
						coverageHelper("30");
				}
				coverageHelper("31");

				if (bSkipLastPoint) {
					coverageHelper("32");
					offset += 2 * 8;

					if (bZs) {
						coverageHelper("33");
						offset += 8;
					} else
						coverageHelper("34");
					if (bMs) {
						coverageHelper("35");
						offset += 8;
					} else
						coverageHelper("36");
				} else if (ipointcount == 1) {
					coverageHelper("37");
					double x = position.read(2 * ipartstart);
					double y = position.read(2 * ipartstart + 1);
					position.write(2 * (ipartstart - 1), x);
					position.write(2 * (ipartstart - 1) + 1, y);

					if (bZs) {
						coverageHelper("38");
						double z = zs.read(ipartstart);
						zs.write(ipartstart - 1, z);
					} else
						coverageHelper("39");

					if (bMs) {
						coverageHelper("40");
						double m = ms.read(ipartstart);
						ms.write(ipartstart - 1, m);
					} else
						coverageHelper("41");
				} else
					coverageHelper("42");
			}
			coverageHelper("43");
		}
		coverageHelper("44");

		// set envelopes and assign AttributeStreams

		if (point_count > 0) {
			coverageHelper("45");
			polygon.setPathStreamRef(parts); // sets m_parts
			polygon.setPathFlagsStreamRef(pathFlags);
			polygon.setAttributeStreamRef(VertexDescription.Semantics.POSITION,
					position);

			if (bZs) {
				coverageHelper("46");
				if (!bCreateZs) {
					coverageHelper("47");
					zs = null;
				} else
					coverageHelper("48");

				polygon.setAttributeStreamRef(VertexDescription.Semantics.Z, zs);
			} else
				coverageHelper("49");

			if (bMs) {
				coverageHelper("50");
				if (!bCreateMs) {
					coverageHelper("51");
					ms = null;
				} else
					coverageHelper("52");

				polygon.setAttributeStreamRef(VertexDescription.Semantics.M, ms);
			} else
				coverageHelper("53");

			polygon.notifyModified(MultiPathImpl.DirtyFlags.DirtyAll);

			AttributeStreamOfInt8 path_flags_clone = new AttributeStreamOfInt8(
					pathFlags);

			for (int i = 0; i < path_flags_clone.size() - 1; i++) {
				coverageHelper("54");
				if (((int) path_flags_clone.read(i) & (int) PathFlags.enumOGCStartPolygon) != 0) {// Should
																									// be
																									// clockwise
					coverageHelper("55");
					if (!InternalUtils.isClockwiseRing(polygon, i)) {
						coverageHelper("56");
						polygon.reversePath(i);
					} // make clockwise
					else
						coverageHelper("57");
				} else {// Should be counter-clockwise
					coverageHelper("58");
					if (InternalUtils.isClockwiseRing(polygon, i)) {
						coverageHelper("59");
						polygon.reversePath(i);
					} // make counter-clockwise
					else
						coverageHelper("60");
				}
			}
			coverageHelper("61");

			polygon.setPathFlagsStreamRef(path_flags_clone);
		}
		coverageHelper("62");

		if ((importFlags & (int) WkbImportFlags.wkbImportNonTrusted) == 0) {
			coverageHelper("63");
			polygon.setIsSimple(MultiVertexGeometryImpl.GeometryXSimple.Weak,
					0.0, false);
		} else
			coverageHelper("64");
		polygon.setDirtyOGCFlags(false);
		wkbHelper.adjustment += offset;

		return newPolygon;
	}

	private static Geometry importFromWkbPolyline(boolean bMultiPolyline,
			int importFlags, boolean bZs, boolean bMs, WkbHelper wkbHelper) {
		int offset;
		int originalPartCount;

		if (bMultiPolyline) {
			originalPartCount = wkbHelper.getInt(5);
			offset = 9;
		} else {
			originalPartCount = 1;
			offset = 0;
		}

		// Find total point count and part count
		int point_count = 0;
		int partCount = 0;
		int tempOffset = offset;
		for (int ipart = 0; ipart < originalPartCount; ipart++) {
			tempOffset += 5; // skip redundant byte order and type fields
			int ipointcount = wkbHelper.getInt(tempOffset);
			tempOffset += 4;

			// If ipointcount == 0, then we have an empty part
			if (ipointcount == 0)
				continue;

			point_count += ipointcount;
			partCount++;

			if (ipointcount == 1)
				point_count++;

			tempOffset += ipointcount * 2 * 8;

			if (bZs)
				tempOffset += ipointcount * 8;

			if (bMs)
				tempOffset += ipointcount * 8;
		}

		AttributeStreamOfDbl position = null;
		AttributeStreamOfDbl zs = null;
		AttributeStreamOfDbl ms = null;
		AttributeStreamOfInt32 parts = null;
		AttributeStreamOfInt8 pathFlags = null;

		Polyline newpolyline;
		MultiPathImpl polyline;

		newpolyline = new Polyline();
		polyline = (MultiPathImpl) newpolyline._getImpl();

		if (bZs)
			polyline.addAttribute(VertexDescription.Semantics.Z);

		if (bMs)
			polyline.addAttribute(VertexDescription.Semantics.M);

		if (point_count > 0) {
			parts = (AttributeStreamOfInt32) (AttributeStreamBase
					.createIndexStream(partCount + 1, 0));
			pathFlags = (AttributeStreamOfInt8) (AttributeStreamBase
					.createByteStream(parts.size(), (byte) 0));
			position = (AttributeStreamOfDbl) (AttributeStreamBase
					.createAttributeStreamWithSemantics(
							VertexDescription.Semantics.POSITION, point_count));

			if (bZs)
				zs = (AttributeStreamOfDbl) (AttributeStreamBase
						.createAttributeStreamWithSemantics(
								VertexDescription.Semantics.Z, point_count));

			if (bMs)
				ms = (AttributeStreamOfDbl) (AttributeStreamBase
						.createAttributeStreamWithSemantics(
								VertexDescription.Semantics.M, point_count));
		}

		boolean bCreateMs = false, bCreateZs = false;
		int ipartend = 0;
		int part_index = 0;

		// read Coordinates
		for (int ipart = 0; ipart < originalPartCount; ipart++) {
			offset += 5; // skip redundant byte order and type fields

			int ipointcount = wkbHelper.getInt(offset);
			offset += 4;

			if (ipointcount == 0)
				continue;

			int ipartstart = ipartend;
			ipartend = ipartstart + ipointcount;

			if (ipointcount == 1) {
				ipartstart++;
				ipartend++;
			}

			parts.write(++part_index, ipartend);

			for (int i = ipartstart; i < ipartend; i++) {
				double x = wkbHelper.getDouble(offset);
				offset += 8;
				double y = wkbHelper.getDouble(offset);
				offset += 8;

				position.write(2 * i, x);
				position.write(2 * i + 1, y);

				if (bZs) {
					double z = wkbHelper.getDouble(offset);
					offset += 8;

					zs.write(i, z);
					if (!VertexDescription.isDefaultValue(
							VertexDescription.Semantics.Z, z))
						bCreateZs = true;
				}

				if (bMs) {
					double m = wkbHelper.getDouble(offset);
					offset += 8;

					ms.write(i, m);
					if (!VertexDescription.isDefaultValue(
							VertexDescription.Semantics.M, m))
						bCreateMs = true;
				}
			}

			if (ipointcount == 1) {
				double x = position.read(2 * ipartstart);
				double y = position.read(2 * ipartstart + 1);
				position.write(2 * (ipartstart - 1), x);
				position.write(2 * (ipartstart - 1) + 1, y);

				if (bZs) {
					double z = zs.read(ipartstart);
					zs.write(ipartstart - 1, z);
				}

				if (bMs) {
					double m = ms.read(ipartstart);
					ms.write(ipartstart - 1, m);
				}
			}
		}

		// set envelopes and assign AttributeStreams

		if (point_count > 0) {
			polyline.setPathStreamRef(parts); // sets m_parts
			polyline.setPathFlagsStreamRef(pathFlags);
			polyline.setAttributeStreamRef(
					VertexDescription.Semantics.POSITION, position);

			if (bZs) {
				if (!bCreateZs)
					zs = null;

				polyline.setAttributeStreamRef(VertexDescription.Semantics.Z,
						zs);
			}

			if (bMs) {
				if (!bCreateMs)
					ms = null;

				polyline.setAttributeStreamRef(VertexDescription.Semantics.M,
						ms);
			}

			polyline.notifyModified(MultiPathImpl.DirtyFlags.DirtyAll);
		}

		wkbHelper.adjustment += offset;

		return newpolyline;
	}

	private static Geometry importFromWkbMultiPoint(int importFlags,
			boolean bZs, boolean bMs, WkbHelper wkbHelper) {
		int offset = 5; // skip byte order and type

		// set point count
		int point_count = wkbHelper.getInt(offset);
		offset += 4;

		AttributeStreamOfDbl position = null;
		AttributeStreamOfDbl zs = null;
		AttributeStreamOfDbl ms = null;

		MultiPoint newmultipoint;
		MultiPointImpl multipoint;

		newmultipoint = new MultiPoint();
		multipoint = (MultiPointImpl) newmultipoint._getImpl();

		if (bZs)
			multipoint.addAttribute(VertexDescription.Semantics.Z);

		if (bMs)
			multipoint.addAttribute(VertexDescription.Semantics.M);

		if (point_count > 0) {
			position = (AttributeStreamOfDbl) (AttributeStreamBase
					.createAttributeStreamWithSemantics(
							VertexDescription.Semantics.POSITION, point_count));

			if (bZs)
				zs = (AttributeStreamOfDbl) (AttributeStreamBase
						.createAttributeStreamWithSemantics(
								VertexDescription.Semantics.Z, point_count));

			if (bMs)
				ms = (AttributeStreamOfDbl) (AttributeStreamBase
						.createAttributeStreamWithSemantics(
								VertexDescription.Semantics.M, point_count));
		}

		boolean bCreateMs = false, bCreateZs = false;
		for (int i = 0; i < point_count; i++) {
			offset += 5; // skip redundant byte order and type fields

			// read xy coordinates
			double x = wkbHelper.getDouble(offset);
			offset += 8;
			double y = wkbHelper.getDouble(offset);
			offset += 8;

			position.write(2 * i, x);
			position.write(2 * i + 1, y);

			if (bZs) {
				double z = wkbHelper.getDouble(offset);
				offset += 8;

				zs.write(i, z);
				if (!VertexDescription.isDefaultValue(
						VertexDescription.Semantics.Z, z))
					bCreateZs = true;
			}

			if (bMs) {
				double m = wkbHelper.getDouble(offset);
				offset += 8;

				ms.write(i, m);
				if (!VertexDescription.isDefaultValue(
						VertexDescription.Semantics.M, m))
					bCreateMs = true;
			}
		}

		// set envelopes and assign AttributeStreams

		if (point_count > 0) {
			multipoint.resize(point_count);
			multipoint.setAttributeStreamRef(
					VertexDescription.Semantics.POSITION, position);

			if (bZs) {
				if (!bCreateZs)
					zs = null;

				multipoint.setAttributeStreamRef(VertexDescription.Semantics.Z,
						zs);
			}

			if (bMs) {
				if (!bCreateMs)
					ms = null;

				multipoint.setAttributeStreamRef(VertexDescription.Semantics.M,
						ms);
			}

			multipoint.notifyModified(MultiPointImpl.DirtyFlags.DirtyAll);
		}

		wkbHelper.adjustment += offset;

		return newmultipoint;
	}

	private static Geometry importFromWkbPoint(int importFlags, boolean bZs,
			boolean bMs, WkbHelper wkbHelper) {
		int offset = 5; // skip byte order and type

		// set xy coordinate
		double x = wkbHelper.getDouble(offset);
		offset += 8;
		double y = wkbHelper.getDouble(offset);
		offset += 8;

		double z = NumberUtils.TheNaN;
		if (bZs) {
			z = wkbHelper.getDouble(offset);
			offset += 8;
		}

		double m = NumberUtils.TheNaN;
		if (bMs) {
			m = wkbHelper.getDouble(offset);
			offset += 8;
		}

		boolean bEmpty = NumberUtils.isNaN(x);
		Point point = new Point();

		if (!bEmpty) {
			point.setX(x);
			point.setY(y);
		}

		// set Z
		if (bZs) {
			point.addAttribute(VertexDescription.Semantics.Z);
			if (!bEmpty)
				point.setZ(z);
		}

		// set M
		if (bMs) {
			point.addAttribute(VertexDescription.Semantics.M);
			if (!bEmpty)
				point.setM(m);
		}

		wkbHelper.adjustment += offset;

		return point;
	}
}
