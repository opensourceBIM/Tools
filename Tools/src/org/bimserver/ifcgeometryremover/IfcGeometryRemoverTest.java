package org.bimserver.ifcgeometryremover;

/******************************************************************************
 * Copyright (C) 2009-2018  BIMserver.org
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bimserver.utils.PathUtils;

public class IfcGeometryRemoverTest {
	public static void main(String[] args) throws IOException {
		Path baseDir = Paths.get("D:\\Dropbox\\Shared\\BIMserver\\IFC modellen\\simple changes");
		IfcGeometryRemover ifcGeometryRemover = new IfcGeometryRemover();
		for (Path inputFile : PathUtils.list(baseDir)) {
			if (inputFile.getFileName().toString().endsWith(".ifc") && !inputFile.getFileName().toString().startsWith("NOGEOM_")) {
				System.out.println(inputFile.getFileName().toString());
				ifcGeometryRemover.removeGeometry(inputFile, inputFile.getParent().resolve("NOGEOM_" + inputFile.getFileName().toString()));
			}
		}
	}
}