package org.bimserver.tools.psetschema;

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

import org.bimserver.tools.ifctocsv.PropertyType.TypePropertyTableValue;

public class TableValue extends PropertyTypeDefinition {

	private TypePropertyTableValue typePropertyTableValue;

	public TableValue(TypePropertyTableValue typePropertyTableValue) {
		this.typePropertyTableValue = typePropertyTableValue;
	}
	
	@Override
	public String toString() {
		return "table: " + typePropertyTableValue.getDefinedValue().getDataType().getType();
	}
}
