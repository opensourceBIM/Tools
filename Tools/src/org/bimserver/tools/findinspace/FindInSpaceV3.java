package org.bimserver.tools.findinspace;

/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.bimserver.client.BimServerClient;
import org.bimserver.client.ClientIfcModel;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.geometry.Matrix;
import org.bimserver.geometry.Vector;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.geometry.GeometryData;
import org.bimserver.models.geometry.GeometryInfo;
import org.bimserver.models.geometry.Vector3f;
import org.bimserver.models.ifc2x3tc1.IfcArbitraryClosedProfileDef;
import org.bimserver.models.ifc2x3tc1.IfcArbitraryProfileDefWithVoids;
import org.bimserver.models.ifc2x3tc1.IfcAxis2Placement3D;
import org.bimserver.models.ifc2x3tc1.IfcCartesianPoint;
import org.bimserver.models.ifc2x3tc1.IfcCompositeCurve;
import org.bimserver.models.ifc2x3tc1.IfcCompositeCurveSegment;
import org.bimserver.models.ifc2x3tc1.IfcCurve;
import org.bimserver.models.ifc2x3tc1.IfcExtrudedAreaSolid;
import org.bimserver.models.ifc2x3tc1.IfcPolyline;
import org.bimserver.models.ifc2x3tc1.IfcProduct;
import org.bimserver.models.ifc2x3tc1.IfcProductRepresentation;
import org.bimserver.models.ifc2x3tc1.IfcProfileDef;
import org.bimserver.models.ifc2x3tc1.IfcRectangleProfileDef;
import org.bimserver.models.ifc2x3tc1.IfcRepresentation;
import org.bimserver.models.ifc2x3tc1.IfcRepresentationItem;
import org.bimserver.models.ifc2x3tc1.IfcShapeRepresentation;
import org.bimserver.models.ifc2x3tc1.IfcSpace;
import org.bimserver.models.ifc2x3tc1.IfcTrimmedCurve;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.utils.GeometryUtils;
import org.bimserver.utils.IfcUtils;
import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.opencsv.CSVWriter;

public class FindInSpaceV3 {
	private final Set<String> foundGuids = new HashSet<>();
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private ArrayNode jsonOutput = OBJECT_MAPPER.createArrayNode();

	private final Map<String, Space> guidToSpaces = new HashMap<>();
	private final Map<String, Space> nameToSpaces = new HashMap<>();
	private Space unmapped = new Space(null);
	private boolean trySemantic = false;
	private final Set<String> notFounds = new HashSet<>();
	
	private final String[] typesToIgnore = new String[]{"IfcDistributionPort", "IfcFlowSegment", "IfcFlowTerminal", "IfcBuilding", "IfcSite", "IfcBuildingStorey"};
	private final Set<String> typesToIgnoreSet = new HashSet<>(Arrays.asList(typesToIgnore));
	
	private enum Contains {
		FULLY,
		PARTIALLY, 
		NOT_AT_ALL
	}
	
	private Contains contains = Contains.PARTIALLY;
	
	public static class Product {
		private IfcProduct ifcProduct;
		private Properties properties;
		private Contains contains;

		public Product(IfcProduct ifcProduct, Contains contains) {
			this.ifcProduct = ifcProduct;
			this.contains = contains;
			properties = new Properties();
			for (String propertyName : IfcUtils.listPropertyNames(ifcProduct)) {
				properties.setProperty(propertyName, IfcUtils.getStringProperty(ifcProduct, propertyName));
			}
		}
		
		public Contains getContains() {
			return contains;
		}
		
		public IfcProduct getIfcProduct() {
			return ifcProduct;
		}
		
		public Properties getProperties() {
			return properties;
		}
	}
	
	public class Space {
		private final Map<String, Product> map = new HashMap<>();
		private IfcSpace ifcSpace;
		private String name;
		private String guid;
		
		public Space(IfcSpace ifcSpace) {
			this.ifcSpace = ifcSpace;
			if (ifcSpace != null) {
				this.guid = ifcSpace.getGlobalId();
				this.name = ifcSpace.getName();
			}
		}
		
		public IfcSpace getIfcSpace() {
			return ifcSpace;
		}
		
		public String getGuid() {
			return guid;
		}
		
		public String getName() {
			return name;
		}

		public Map<String, Product> getMap() {
			return map;
		}
		
		public void add(IfcProduct ifcProduct, Contains contains) {
			map.put(ifcProduct.getGlobalId(), new Product(ifcProduct, contains));
			foundGuids.add(ifcProduct.getGlobalId());
		}
	}

	public static void main(String[] args) {
		new FindInSpaceV3().start();
	}
	
	public void writeCsv() throws IOException {
		try (CSVWriter csvWriter = new CSVWriter(new FileWriter(new File("spacecontent.csv")))) {
			Set<String> uniqueKeys = new TreeSet<>();
			for (String guid : guidToSpaces.keySet()) {
				Space space = guidToSpaces.get(guid);
				for (String productGuid : space.getMap().keySet()) {
					Product product = space.getMap().get(productGuid);
					for (Object key : product.getProperties().keySet()) {
						uniqueKeys.add((String)key);
					}
				}
			}
			
			List<String> keys = new ArrayList<>(uniqueKeys);

			String[] header = new String[keys.size() + 6];
			header[0] = "";
			header[1] = "Type";
			header[2] = "GUID";
			header[3] = "Name";
			header[4] = "Contained";
			header[5] = "Ruimtenr";
			for (int i=0; i<keys.size(); i++) {
				String key = keys.get(i);
				header[6 + i] = key;
			}
			csvWriter.writeNext(header);
			csvWriter.writeNext(new String[] { "" });

			for (String guid : guidToSpaces.keySet()) {
				Space space = guidToSpaces.get(guid);
				csvWriter.writeNext(new String[] { "Container", "IfcSpace", space.getGuid(), space.getName(), "", space.getName() });
				
				dumpSpace(csvWriter, keys, space);
			}
			
			csvWriter.writeNext(new String[] { "Not found", "", "", "Not found", "", "Not found" });
			dumpSpace(csvWriter, keys, unmapped);
			List<String> l = new ArrayList<>();
			for (String key : unmapped.getMap().keySet()) {
				Product product = unmapped.getMap().get(key);
				String name = product.getIfcProduct().eClass().getName();
				if (!typesToIgnoreSet.contains(name)) {
					l.add(product.getIfcProduct().getGlobalId());
				}
			}
			Iterable<String> wrappedStrings = Iterables.transform(l, new Function<String, String>() {
		        public String apply(String arg0) {
		            return "\"" + arg0 + "\"";
		        }}); 
			System.out.println(Joiner.on(",").join(wrappedStrings));
		}		
	}

	private void dumpSpace(CSVWriter csvWriter, List<String> keys, Space space) {
		for (String productGuid : space.getMap().keySet()) {
			Product product = space.getMap().get(productGuid);
			
			String name = product.getIfcProduct().eClass().getName();
			if (!typesToIgnoreSet.contains(name)) {
				String[] line = new String[keys.size() + 6];
				line[0] = "";
				line[1] = product.getIfcProduct().eClass().getName();
				line[2] = product.getIfcProduct().getGlobalId();
				line[3] = product.getIfcProduct().getName();
				line[4] = product.getContains().name();
				line[5] = product.getProperties().getProperty("Ruimtenr");
				
				for (int i=0; i<keys.size(); i++) {
					String key = keys.get(i);
					String prop = product.getProperties().getProperty(key);
					if (prop != null) {
						line[i + 6] = prop;
					}
				}
				
				csvWriter.writeNext(line);
			}
		}
		
		csvWriter.writeNext(new String[] { "" });
	}

	private void writeJsonObject(float[] color, double x, double y, double width, double height) {
		ArrayNode materialNodes = OBJECT_MAPPER.createArrayNode();
		ArrayNode translateNodes = OBJECT_MAPPER.createArrayNode();

		ObjectNode baseColor = OBJECT_MAPPER.createObjectNode();
		baseColor.put("r", color[0]);
		baseColor.put("g", color[1]);
		baseColor.put("b", color[2]);
		baseColor.put("a", 1);
		
		ObjectNode materialNode = OBJECT_MAPPER.createObjectNode();
		materialNode.put("type", "material");
		materialNode.set("baseColor", baseColor);
		materialNode.put("alpha", 1);
		materialNode.set("nodes", materialNodes);
		
		ObjectNode translateNode = OBJECT_MAPPER.createObjectNode();
		materialNodes.add(translateNode);
		
		translateNode.put("type", "translate");
		translateNode.put("x", x + width / 2);
		translateNode.put("y", y + height / 2);
		translateNode.put("z", 2000);
		translateNode.set("nodes", translateNodes);
		
		ObjectNode boxNode = OBJECT_MAPPER.createObjectNode();
		translateNodes.add(boxNode);
		
		ArrayNode size = OBJECT_MAPPER.createArrayNode();
		size.add(width / 2);
		size.add(height / 2);
		size.add(4000 / 2);
		
		boxNode.put("type", "geometry/box");
		boxNode.set("size", size);
		boxNode.put("wire", true);
		
		jsonOutput.add(materialNode);
	}
	
	private void start() {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				SProject spacesProject = client.getServiceInterface().getTopLevelProjectByName("Spaces Aligned");
				SProject objectsProject = client.getServiceInterface().getTopLevelProjectByName("Installations Aligned");

				ClientIfcModel spacesModel = client.getModel(spacesProject, spacesProject.getLastRevisionId(), true, false, true);
				ClientIfcModel objectsModel = client.getModel(objectsProject, objectsProject.getLastRevisionId(), true, false, true);

				for (IfcSpace ifcSpace : spacesModel.getAll(IfcSpace.class)) {
					Space space = new Space(ifcSpace);
					guidToSpaces.put(ifcSpace.getGlobalId(), space);
					nameToSpaces.put(ifcSpace.getName(), space);
					
					Vector3f geometryMinBounds = ifcSpace.getGeometry().getBounds().getMin();
					Vector3f geometryMaxBounds = ifcSpace.getGeometry().getBounds().getMax();
					
					if (ifcSpace.getRepresentation() != null) {
//							IfcLocalPlacement objectPlacement = (IfcLocalPlacement) ifcSpace.getObjectPlacement();
//							IfcAxis2Placement3D axis2Placement3D = (IfcAxis2Placement3D) objectPlacement.getRelativePlacement();
//							System.out.println(axis2Placement3D.getLocation().getCoordinates());
						
						System.out.println(ifcSpace.getGlobalId());
						GeometryInfo spaceGeometry = ifcSpace.getGeometry();
//							double[] spaceMatrix = GeometryUtils.toDoubleArray(spaceGeometry.getTransformation());
						IfcProductRepresentation representation = ifcSpace.getRepresentation();
						for (IfcRepresentation ifcRepresentation : representation.getRepresentations()) {
							if (ifcRepresentation instanceof IfcShapeRepresentation) {
								IfcShapeRepresentation ifcShapeRepresentation = (IfcShapeRepresentation) ifcRepresentation;
								for (IfcRepresentationItem ifcRepresentationItem : ifcShapeRepresentation.getItems()) {
									if (ifcRepresentationItem instanceof IfcExtrudedAreaSolid) {
										IfcExtrudedAreaSolid ifcExtrudedAreaSolid = (IfcExtrudedAreaSolid) ifcRepresentationItem;
										IfcAxis2Placement3D position = ifcExtrudedAreaSolid.getPosition();
										IfcCartesianPoint location = position.getLocation();
										
//											1 0 0 0 <- 3de argument
//											0 1 0 0 <- cross product van 2 en 3 (levert ortogonale vector op)
//											0 0 1 0 <- 2st argument
//											5 0 0 1 <- 1st argument
										
										double[] matrix = Matrix.identity();
										if (position.getAxis() != null && position.getRefDirection() != null) {
											double[] cross = Vector.crossProduct(new double[]{position.getAxis().getDirectionRatios().get(0), position.getAxis().getDirectionRatios().get(1), position.getAxis().getDirectionRatios().get(2), 1}, new double[]{position.getRefDirection().getDirectionRatios().get(0), position.getRefDirection().getDirectionRatios().get(1), position.getRefDirection().getDirectionRatios().get(2), 1});
											matrix = new double[]{
												position.getRefDirection().getDirectionRatios().get(0), position.getRefDirection().getDirectionRatios().get(1), position.getRefDirection().getDirectionRatios().get(2), 0,
												cross[0], cross[1], cross[2], 0,
												position.getAxis().getDirectionRatios().get(0), position.getAxis().getDirectionRatios().get(1), position.getAxis().getDirectionRatios().get(2), 0,
												position.getLocation().getCoordinates().get(0), position.getLocation().getCoordinates().get(1), position.getLocation().getCoordinates().get(2), 1
											};
//												matrix = Matrix.changeOrientation(matrix);
											
											Matrix.dump(matrix);
										} else if (position.getLocation() != null) {
											matrix = new double[]{
												1, 0, 0, 0,
												0, 1, 0, 0,
												0, 0, 1, 0,
												position.getLocation().getCoordinates().get(0), position.getLocation().getCoordinates().get(1), 0, 1
											};
										}
										
										IfcProfileDef ifcProfileDef = ifcExtrudedAreaSolid.getSweptArea();
										if (ifcProfileDef instanceof IfcArbitraryProfileDefWithVoids) {
											IfcArbitraryProfileDefWithVoids ifcArbitraryProfileDefWithVoids = (IfcArbitraryProfileDefWithVoids) ifcProfileDef;
											Path2D path2d = new Path2D.Float();
											IfcCurve outerCurve = ifcArbitraryProfileDefWithVoids.getOuterCurve();
											Cube cube = new Cube();
											if (outerCurve instanceof IfcPolyline) {
												IfcPolyline ifcPolyline = (IfcPolyline) outerCurve;
												IfcCartesianPoint first = ifcPolyline.getPoints().get(0);
												double[] res = new double[4];

												Matrix.multiplyMV(res, 0, matrix, 0, new double[]{first.getCoordinates().get(0), first.getCoordinates().get(1), 0, 1}, 0);
												path2d.moveTo(res[0], res[1]);
												
												cube.add(new Coord(res[0], res[1], 0));
												for (IfcCartesianPoint cartesianPoint : ifcPolyline.getPoints()) {
													EList<Double> coords = cartesianPoint.getCoordinates();
													
													Matrix.multiplyMV(res, 0, matrix, 0, new double[]{coords.get(0), coords.get(1), 0, 1}, 0);
													cube.add(new Coord(res[0], res[1], 0));
													
													path2d.lineTo(res[0], res[1]);
												}
												path2d.closePath();
											}
											Area area = new Area(path2d);

											double[] min = cube.getMin();
											double[] max = cube.getMax();
											
											System.out.println(geometryMinBounds.getX() + "," + geometryMinBounds.getY() + ", " + geometryMinBounds.getZ());
											System.out.println(geometryMaxBounds.getX() + "," + geometryMaxBounds.getY() + ", " + geometryMaxBounds.getZ());
											
											writeJsonObject(new float[]{0f, 0f, 1f}, min[0], min[1], max[0] - min[0], max[1] - min[1]);

											checkWithPath(space, objectsModel, area, "IfcArbitraryProfileDefWithVoids");
										} else if (ifcProfileDef instanceof IfcArbitraryClosedProfileDef) {
											IfcArbitraryClosedProfileDef ifcArbitraryClosedProfileDef = (IfcArbitraryClosedProfileDef) ifcProfileDef;
											Path2D path2d = new Path2D.Float();
											IfcCurve outerCurve = ifcArbitraryClosedProfileDef.getOuterCurve();
											Cube cube = new Cube();
											boolean first = true;
											if (outerCurve instanceof IfcPolyline) {
												IfcPolyline ifcPolyline = (IfcPolyline) outerCurve;

												double[] res = new double[4];
												
												for (IfcCartesianPoint cartesianPoint : ifcPolyline.getPoints()) {
													EList<Double> coords = cartesianPoint.getCoordinates();

													Matrix.multiplyMV(res, 0, matrix, 0, new double[]{coords.get(0), coords.get(1), 0, 1}, 0);
													
													cube.add(new Coord(res[0], res[1], 0));

													if (first) {
														path2d.moveTo(res[0], res[1]);
														first = false;
													} else {
														path2d.lineTo(res[0], res[1]);
													}
												}
												path2d.closePath();

												double[] min = cube.getMin();
												double[] max = cube.getMax();
												
												System.out.println(geometryMinBounds.getX() + "," + geometryMinBounds.getY() + ", " + geometryMinBounds.getZ());
												System.out.println(geometryMaxBounds.getX() + "," + geometryMaxBounds.getY() + ", " + geometryMaxBounds.getZ());
												
												writeJsonObject(new float[]{1f, 0f, 0f}, min[0], min[1], max[0] - min[0], max[1] - min[1]);
												
												Area area = new Area(path2d);
												
												checkWithPath(space, objectsModel, area, "IfcArbitraryClosedProfileDef");
											} else if (outerCurve instanceof IfcCompositeCurve) {
												IfcCompositeCurve ifcCompositeCurve = (IfcCompositeCurve)outerCurve;

												for (IfcCompositeCurveSegment ifcCompositeCurveSegment : ifcCompositeCurve.getSegments()) {
													IfcCurve curve = ifcCompositeCurveSegment.getParentCurve();
													if (curve instanceof IfcPolyline) {
														IfcPolyline ifcPolyline = (IfcPolyline)curve;
														double[] res = new double[4];
														for (IfcCartesianPoint cartesianPoint : ifcPolyline.getPoints()) {
															EList<Double> coords = cartesianPoint.getCoordinates();

															Matrix.multiplyMV(res, 0, matrix, 0, new double[]{coords.get(0), coords.get(1), 0, 1}, 0);
															
															cube.add(new Coord(res[0], res[1], 0));
															if (first) {
																path2d.moveTo(res[0], res[1]);
																first = false;
															} else {
																path2d.lineTo(res[0], res[1]);
															}
														}
													} else if (curve instanceof IfcTrimmedCurve) {
													} else {
														System.out.println(curve);
													}
												}
												path2d.closePath();
												Area area = new Area(path2d);
												checkWithPath(space, objectsModel, area, "IfcArbitraryClosedProfileDef");
											}
											
										} else if (ifcProfileDef instanceof IfcRectangleProfileDef) {
											IfcRectangleProfileDef ifcRectangleProfileDef = (IfcRectangleProfileDef) ifcProfileDef;

											double[] min = new double[]{ifcRectangleProfileDef.getPosition().getLocation().getCoordinates().get(0) - ifcRectangleProfileDef.getXDim() / 2, ifcRectangleProfileDef.getPosition().getLocation().getCoordinates().get(1) - ifcRectangleProfileDef.getYDim() / 2, 0, 1};
											double[] max = new double[]{ifcRectangleProfileDef.getPosition().getLocation().getCoordinates().get(0) + ifcRectangleProfileDef.getXDim() / 2, ifcRectangleProfileDef.getPosition().getLocation().getCoordinates().get(1) + ifcRectangleProfileDef.getYDim() / 2, 0, 1};
											
											Cube cube = new Cube(min, max);
											cube.transform(matrix);
											double[] transformedMin = cube.getMin();
											double[] transformedMax = cube.getMax();
											
											System.out.println(Arrays.toString(transformedMin));
											System.out.println(Arrays.toString(transformedMax));
											
											System.out.println(geometryMinBounds.getX() + "," + geometryMinBounds.getY() + ", " + geometryMinBounds.getZ());
											System.out.println(geometryMaxBounds.getX() + "," + geometryMaxBounds.getY() + ", " + geometryMaxBounds.getZ());
											
											writeJsonObject(new float[]{0f, 1f, 0f}, transformedMin[0], transformedMin[1], transformedMax[0] - transformedMin[0], transformedMax[1] - transformedMin[1]);

											// System.out.println(minBounds.getX()
											// + "," + minBounds.getY() + ",
											// " + minBounds.getZ() + " " +
											// maxBounds.getX() + ", " +
											// maxBounds.getY() + ", " +
											// maxBounds.getZ());
											// System.out.println(minBoundsSemantic[0]
											// + "," + minBoundsSemantic[1]
											// + ", " + " " +
											// maxBoundsSemantic[0] + ", " +
											// maxBoundsSemantic[1]);
											// System.out.println();

											for (IfcProduct ifcProduct : objectsModel.getAllWithSubTypes(IfcProduct.class)) {
												if (ifcProduct.getGeometry() != null) {
													GeometryInfo geometry = ifcProduct.getGeometry();
													GeometryData data = geometry.getData();
													float[] vertices = GeometryUtils.toFloatArray(data.getVertices().getData());
													boolean allInside = true;
													boolean moreThanOneInside = false;
													double[] productMatrix = GeometryUtils.toDoubleArray(geometry.getTransformation());
													double[] result = new double[4];
													for (int i = 0; i < vertices.length; i += 3) {
														Matrix.multiplyMV(result, 0, productMatrix, 0, new double[] { vertices[i], vertices[i + 1], vertices[i + 2], 1 }, 0);
														if (result[0] >= transformedMin[0] && result[1] >= transformedMin[1] && result[0] <= transformedMax[0] && result[1] <= transformedMax[1]) {
															moreThanOneInside = true;
															// Inside
														} else {
															allInside = false;
														}
													}
													if (allInside) {
														space.add(ifcProduct, Contains.FULLY);
													} else if (contains == Contains.PARTIALLY && moreThanOneInside) {
														space.add(ifcProduct, Contains.PARTIALLY);
													}
												}
											}
										} else {
											System.out.println("Unimplemented: " + ifcProfileDef);
										}
									}
								}
							}
						}
					}
				}

				for (IfcProduct ifcProduct : objectsModel.getAllWithSubTypes(IfcProduct.class)) {
					if (!foundGuids.contains(ifcProduct.getGlobalId())) {
						boolean found = false;
						if (trySemantic) {
							String ruimteNr = IfcUtils.getStringProperty(ifcProduct, "Ruimtenr");
							if (ruimteNr != null) {
								if (nameToSpaces.containsKey(ruimteNr)) {
									nameToSpaces.get(ruimteNr).add(ifcProduct, Contains.NOT_AT_ALL);
									found = true;
								}
							}
						}
						if (!found) {
							unmapped.add(ifcProduct, Contains.NOT_AT_ALL);
						}
					}
				}
			}
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		try {
			OBJECT_MAPPER.writeValue(new File("output.json"), jsonOutput);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			writeCsv();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getGeometryType(IfcSpace ifcSpace) {
		if (ifcSpace.getRepresentation() != null) {
			GeometryInfo spaceGeometry = ifcSpace.getGeometry();
			IfcProductRepresentation representation = ifcSpace.getRepresentation();
			for (IfcRepresentation ifcRepresentation : representation.getRepresentations()) {
				if (ifcRepresentation instanceof IfcShapeRepresentation) {
					IfcShapeRepresentation ifcShapeRepresentation = (IfcShapeRepresentation) ifcRepresentation;
					for (IfcRepresentationItem ifcRepresentationItem : ifcShapeRepresentation.getItems()) {
						if (ifcRepresentationItem instanceof IfcExtrudedAreaSolid) {
							IfcExtrudedAreaSolid ifcExtrudedAreaSolid = (IfcExtrudedAreaSolid)ifcRepresentationItem;
							IfcProfileDef ifcProfileDef = ifcExtrudedAreaSolid.getSweptArea();
							return "IfcExtrudedAreaSolid, " + ifcProfileDef.eClass().getName();
						}
					}
				}
			}
		}
		return "Unimplemented type";
	}

	private void checkWithPath(Space space, ClientIfcModel objectsModel, Area area, String geometryType) {
		for (IfcProduct ifcProduct : objectsModel.getAllWithSubTypes(IfcProduct.class)) {
			if (ifcProduct.getGeometry() != null) {
				GeometryInfo geometry = ifcProduct.getGeometry();
				GeometryData data = geometry.getData();
				float[] vertices = GeometryUtils.toFloatArray(data.getVertices().getData());
				boolean allInside = true;
				boolean moreThanOneInside = false;
				double[] matrix = GeometryUtils.toDoubleArray(geometry.getTransformation());
				double[] result = new double[4];
				for (int i = 0; i < vertices.length; i += 3) {
					Matrix.multiplyMV(result, 0, matrix, 0, new double[] { vertices[i], vertices[i + 1], vertices[i + 2], 1 }, 0);
					if (area.contains(new Point2D.Double(result[0], result[1]))) {
						moreThanOneInside = true;
					} else {
						allInside = false;
					}
				}
				if (allInside) {
					space.add(ifcProduct, Contains.FULLY);
				} else if (contains == Contains.PARTIALLY && moreThanOneInside) {
					space.add(ifcProduct, Contains.PARTIALLY);
				}
			}
		}
	}
}
