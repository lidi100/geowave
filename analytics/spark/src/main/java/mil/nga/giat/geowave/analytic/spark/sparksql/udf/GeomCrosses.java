package mil.nga.giat.geowave.analytic.spark.sparksql.udf;

import com.vividsolutions.jts.geom.Geometry;

public class GeomCrosses extends
		GeomFunction
{

	@Override
	public boolean apply(
			Geometry geom1,
			Geometry geom2 ) {
		return geom1.crosses(geom2);
	}

}
