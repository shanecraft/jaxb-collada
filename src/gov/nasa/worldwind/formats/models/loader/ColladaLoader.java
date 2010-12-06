/*
 * ColladaLoader.java create 3/12/2010
 * This loader implements geometry
 * creation of Collada models
 * 
 * A collada file contains scenes, which use geometries.
 * Geometries have vertices, faces (polygons), materials
 * The materials are stored in their own library
 */

package gov.nasa.worldwind.formats.models.loader;

import gov.nasa.worldwind.formats.models.ModelLoadException;
import gov.nasa.worldwind.formats.models.geometry.Face;
import gov.nasa.worldwind.formats.models.geometry.Material;
import gov.nasa.worldwind.formats.models.geometry.Model;
import gov.nasa.worldwind.formats.models.geometry.Vec4;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.collada._2005._11.colladaschema.*;
import org.collada._2005._11.colladaschema.Source.TechniqueCommon;

public class ColladaLoader implements iLoader {

	/**
	 * @throws ModelLoadException
	 */
	@Override
	public Model load(String path) throws ModelLoadException {
		// TODO Auto-generated method stub
		Model model = new Model(path);
		try {
			this.parseCollada(path,model);
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return model;
	}

	/**
	 * Parse the Mesh from the collada file
	 * 
	 * @param path
	 * @throws JAXBException 
	 * @throws FileNotFoundException 
	 */
	private void parseCollada(String path,Model output) throws JAXBException,
	FileNotFoundException {
		JAXBContext jc = JAXBContext.newInstance
		( "org.collada._2005._11.colladaschema" );
		Unmarshaller u = jc.createUnmarshaller();
		COLLADA col =
			  (COLLADA)u.unmarshal(
			    new FileInputStream( path ) );
		
		//TODO: Parse Geometries and Materials from Libraries
		List libraries = col.getLibraryAnimationsAndLibraryAnimationClipsAndLibraryCameras();
		for (Object library : libraries) {
			if(library instanceof LibraryGeometries)
			{
				this.parseGeometries((LibraryGeometries)library,output);
			}
			if(library instanceof LibraryMaterials)
			{
				this.parseMaterials((LibraryMaterials)library,output);
			}
			if(library instanceof LibraryEffects)
			{
				this.parseEffects((LibraryMaterials)library,output);
			}
		}
		
		//TODO: Parse Scene and Add primitives
			
	}

	private void parseEffects(LibraryMaterials library, Model output) {
		// TODO Auto-generated method stub
		
	}

	private void parseMaterials(LibraryMaterials library,Model output) {
		// TODO create a list of Materials
		List<org.collada._2005._11.colladaschema.Material> matlist = library.getMaterials();
		Map<String,Material> matSrc = new HashMap<String, Material>();
		for (org.collada._2005._11.colladaschema.Material material : matlist) {
			Material mat = new Material();
			String matId = material.getId();
			// To get color need to link material to Effects
			mat.ambientColor = Color.BLUE;
		    mat.specularColor = Color.BLUE;
		    mat.diffuseColor = Color.BLUE;
		    mat.emissive = Color.BLUE;
		    mat.shininess = 1.0f;
		    mat.shininess2 = 1.0f;
		    mat.transparency = 0.0f;
			output.addMaterial(mat);
		}
		
	}

	private void parseGeometries(LibraryGeometries library,Model output) {	
		List<Geometry> geoms = library.getGeometries();
		for (Geometry geometry : geoms) {
			Mesh m = geometry.getMesh();
			
			//Geometry to Mesh is 1-1 in Collada but 1-Many in Model
			gov.nasa.worldwind.formats.models.geometry.Mesh targetMesh =
				new gov.nasa.worldwind.formats.models.geometry.Mesh();
			
			targetMesh.name = geometry.getName();
			
			//Parse sources first, then bind to vertices and polygons
			// Sources are points and normals
			
			//Use a hashmap to store sources
			Map<String,Vec4[]> sourceMap = new HashMap<String, Vec4[]>();
			
			List sources = m.getSources();
			for (Object object : sources) {
				Source s = (Source)object;
				FloatArray flts = s.getFloatArray();
				TechniqueCommon tech = s.getTechniqueCommon();
				String sourceId = s.getId();
				//TODO: Apply parse technique to get XYZ arrays
				Vec4[] data = this.parseFloatArray(flts,tech);
				sourceMap.put("#"+sourceId, data);
			}
			
			//Parse vertices and bind using data sources above
			Vertices vert = m.getVertices();
			List inputs = vert.getInputs();
			for (Object object : inputs) {
				InputLocal input = (InputLocal)object;
				String sourceHash = input.getSource();
				Vec4[] vertices = sourceMap.get(sourceHash);
				targetMesh.vertices = vertices;
				targetMesh.numOfVerts = vertices.length;
				
				//Calculate bounds of models
				for (Vec4 vec4 : vertices) {
					targetMesh.bounds.calc(vec4);
					output.getBounds().calc(vec4);
				}
				
				//Read vec4 to sourceMap
				String vertId = vert.getId();
				sourceMap.put("#"+vertId, vertices);
			}
			
			//Parse Normals and bind using data sources above
			//Parse polygons and bind vertices from above by reference
			List polys = m.getLinesAndLinestripsAndPolygons();
			for (Object object : polys) {
				if(object instanceof Polygons)
				{
					Face[] faces = parseFaces((Polygons)object,sourceMap,targetMesh);
					targetMesh.faces = faces;
					targetMesh.numOfFaces = faces.length;
				}
			}
			
			//Add mesh to model passed in
			output.addMesh(targetMesh);
		}
	}

	private Vec4[] parseFloatArray(FloatArray flts, TechniqueCommon tech) {
		// Use technique to parse array
		int totFloats = flts.getCount().intValue();
		int techVecs = tech.getAccessor().getCount().intValue();
		int stride = tech.getAccessor().getStride().intValue();
		
		if(techVecs*stride==totFloats)
		{
			Vec4[] data = new Vec4[techVecs];
			Double[] values = new Double[totFloats];
			flts.getValues().toArray(values);
			
			for(int i=0;i<techVecs;i++)
			{
				Vec4 targetVec = new Vec4();
				targetVec.x = values[i*stride+0].floatValue();
				targetVec.y = values[i*stride+1].floatValue();
				targetVec.z = values[i*stride+2].floatValue();
				data[i] = targetVec;
			}
			return data;
		}
		return null;
	}

	private Face[] parseFaces(Polygons polys,Map<String,Vec4[]> sourceMap,
			gov.nasa.worldwind.formats.models.geometry.Mesh targetMesh) {
		// TODO Use lookup to generate faces
		Vec4[] normals = null;
		Vec4[] vertices = null;
		Vec4[] textures = null;
		int normoffset = -1;
		int vertoffset = -1;
		int texoffset = -1;
		List inputlist = polys.getInputs();
		int inputCount = inputlist.size();
		for (Object object : inputlist) {
			InputLocalOffset input = (InputLocalOffset)object;
			String semantic = input.getSemantic();
			if(semantic.equals("NORMAL"))
			{
				String normHash = input.getSource();
				normals = sourceMap.get(normHash);
				normoffset = input.getOffset().intValue();
				//TODO Find a better spot to bind the normals
				targetMesh.normals = normals;
			}
			if(semantic.equals("VERTEX"))
			{
				String vertHash = input.getSource();
				vertices = sourceMap.get(vertHash);
				vertoffset = input.getOffset().intValue();
			}
			//TODO: Textured objects will have a semantic Texture ??
			if(semantic.equals("TEXTURE"))
			{
				String texHash = input.getSource();
				textures = sourceMap.get(texHash);
				texoffset = input.getOffset().intValue();
			}
		}
		
		List polylist = polys.getPSAndPhs();
		ArrayList<Face> faces = new ArrayList<Face>();
		for (Object object : polylist) {
			try{
				ArrayList<BigInteger> vertRefs = 
					(ArrayList<BigInteger>)((JAXBElement)object).getValue();
				// Each number set in polygon references vertex,normal and texture
				// coordinate in a given order
				int facePoints = vertRefs.size()/inputCount;
				Face face = new Face(facePoints);
				BigInteger[] indices = new BigInteger[vertRefs.size()];
				vertRefs.toArray(indices);
				//TODO Late binding normals is a pain
				
				for(int i=0;i<facePoints;i++)
				{
					// If vertices are defined set vertIndex
					if(vertoffset!=-1)
					{
						face.vertIndex[i] = indices[(i*inputCount)
					                            +vertoffset].intValue();
					}
					else
					{
						face.vertIndex[i] = -1;
					}
					
					// If normals are defined set normIndex
					if(normoffset!=-1)
					{
						face.normalIndex[i] = indices[(i*inputCount)
					                            +normoffset].intValue();
					}
					else
					{
						face.normalIndex[i] = -1;
					}
					
					// If textures are defined set coordIndex
					if(texoffset!=-1)
					{
						face.coordIndex[i] = indices[(i*inputCount)
					                            +texoffset].intValue();
					}
					else
					{
						face.coordIndex[i] = -1;
					}
				}
				
				faces.add(face);
			}
			catch(ClassCastException e)
			{
				e.printStackTrace();
			}			
		}
		Face[] face_arr = new Face[faces.size()];
		return faces.toArray(face_arr);
	}

}