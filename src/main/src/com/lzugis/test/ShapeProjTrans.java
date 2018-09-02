package com.lzugis.test;

import com.lzugis.utils.ProjTransform;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShapeProjTrans {
    private ProjTransform projTransform = new ProjTransform();

    /**
     * 坐标转换，实现从wgs84到gcj02
     * @param input
     * @param output
     */
    public void transShape(String input, String output){
        try {
            File inputFile = new File(input);
            ShapefileDataStore dsInput = new ShapefileDataStore(inputFile.toURL());
            //属性编码
            Charset charset = Charset.forName("GBK");

            dsInput.setCharset(charset);
            String typeName = dsInput.getTypeNames()[0];
            SimpleFeatureSource featureSource = dsInput.getFeatureSource(typeName);
            SimpleFeatureCollection featureCollection = featureSource.getFeatures();

            //获取shp文件的字段
            Map<String, Class> mapFields = new HashMap();
            SimpleFeatureType featureType1 = featureCollection.getSchema();
            List<AttributeDescriptor> attrList1 = featureType1.getAttributeDescriptors();
            Class geomType = null;
            for(int i=0;i<attrList1.size();i++){
                AttributeDescriptor attr = attrList1.get(i);
                String name = attr.getName().toString();
                Class type = attr.getType().getBinding();
                if(name != "the_geom"){
                    mapFields.put(name, type);
                } else {
                    geomType = type;
                }
            }

            //创建输出文件
            File outputFile = new File(output);
            Map<String, Serializable> params = new HashMap<String, Serializable>();
            params.put( ShapefileDataStoreFactory.URLP.key, outputFile.toURI().toURL() );
            ShapefileDataStore dsOutput = (ShapefileDataStore) new ShapefileDataStoreFactory().createNewDataStore(params);
            //定义图形信息和属性信息
            SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
            tb.setCRS(featureType1.getCoordinateReferenceSystem());
            tb.setName("shapefile");
            tb.add("the_geom", geomType);
            for(String key:mapFields.keySet()){
                tb.add(key, mapFields.get(key));
            }
            dsOutput.createSchema(tb.buildFeatureType());
            //设置编码
            dsOutput.setCharset(charset);
            //设置Writer
            FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
                    dsOutput.getFeatureWriter(dsOutput.getTypeNames()[0], Transaction.AUTO_COMMIT);

            SimpleFeatureIterator itertor = featureCollection.features();
            while (itertor.hasNext()) {
                SimpleFeature feature = itertor.next();
                Point geom = (Point) feature.getDefaultGeometry();
                double x = geom.getX(),
                        y = geom.getY();
                double[] lonlat = projTransform.wgs84togcj02(x, y);
                Coordinate coordinate = new Coordinate(lonlat[0], lonlat[1]);
                Geometry geomOut = new Point(coordinate, geom.getPrecisionModel(), geom.getSRID());
                SimpleFeature featureOut = writer.next();
                featureOut.setAttribute("the_geom", geomOut);
                for(String key:mapFields.keySet()){
                    Object val = feature.getAttribute(key);
                    featureOut.setAttribute(key, val);
                }
                writer.write();
            }
            itertor.close();
            writer.close();
            dsInput.dispose();
            dsOutput.dispose();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args){
        double start = System.currentTimeMillis();
        ShapeProjTrans shapeProjTrans = new ShapeProjTrans();
        String path = "C:\\Users\\lzuni\\Desktop\\geoserver\\";
        String input = path+"layer_towers.shp",
                output = path+"out\\layer_towers.shp";
        shapeProjTrans.transShape(input, output);
        double end = System.currentTimeMillis();
        System.out.println("transform success, coast "+(end-start)+"MS");
    }
}
