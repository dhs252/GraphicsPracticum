
package cs4620.ray1.accel;

import java.util.Arrays;
import java.util.Comparator;

import cs4620.ray1.IntersectionRecord;
import cs4620.ray1.Ray;
import cs4620.ray1.surface.Surface;
import egl.math.Vector3d;

/**
 * Class for Axis-Aligned-Bounding-Box to speed up the intersection look up time.
 *
 * @author ss932, pramook
 */
public class Bvh implements AccelStruct {   
	/** A shared surfaces array that will be used across every node in the tree. */
	private Surface[] surfaces;

	/** A comparator class that can sort surfaces by x, y, or z coordinate.
	 *  See the subclass declaration below for details.
	 */
	static MyComparator cmp = new MyComparator();
	
	/** The root of the BVH tree. */
	BvhNode root;

	public Bvh() { }

	/**
	 * Set outRecord to the first intersection of ray with the scene. Return true
	 * if there was an intersection and false otherwise. If no intersection was
	 * found outRecord is unchanged.
	 *
	 * @param outRecord the output IntersectionRecord
	 * @param ray the ray to intersect
	 * @param anyIntersection if true, will immediately return when found an intersection
	 * @return true if and intersection is found.
	 */
	public boolean intersect(IntersectionRecord outRecord, Ray rayIn, boolean anyIntersection) {
		return intersectHelper(root, outRecord, rayIn, anyIntersection);
	}
	
	/**
	 * A helper method to the main intersect method. It finds the intersection with
	 * any of the surfaces under the given BVH node.  
	 *   
	 * @param node a BVH node that we would like to find an intersection with surfaces under it
	 * @param outRecord the output InsersectionMethod
	 * @param rayIn the ray to intersect
	 * @param anyIntersection if true, will immediately return when found an intersection
	 * @return true if an intersection is found with any surface under the given node
	 */
	private boolean intersectHelper(BvhNode node, IntersectionRecord outRecord, Ray rayIn, boolean anyIntersection)
	{
		
		//First check if the node intersects the ray
		Ray ray = new Ray(rayIn.origin, rayIn.direction);
		ray.start = rayIn.start;
		ray.end = rayIn.end;
		if (!node.intersects(ray)){
			return false;
		}
		
		//Recursively check children
		if (!node.isLeaf()){
			boolean hit = intersectHelper(node.child[0], outRecord, ray, anyIntersection);
			if (hit){
				if (anyIntersection){
					return true;
				} else {
					ray.end = outRecord.t;
				}
			}
			hit = intersectHelper(node.child[1], outRecord, ray, anyIntersection) || hit;
			return hit;
		} 
		//Or just loop through ones in this box
		else {
			boolean ret = false;
			IntersectionRecord tmp = new IntersectionRecord();
			for(int i = node.surfaceIndexStart; i < node.surfaceIndexEnd; i++) {
				if(surfaces[i].intersect(tmp, ray) && tmp.t < ray.end ) {
					if(anyIntersection){
						return true;
					}
					ret = true;
					ray.end = tmp.t;
					if(outRecord != null)
						outRecord.set(tmp);
				}
			}
			return ret;
		}
	}


	@Override
	public void build(Surface[] surfaces) {
		this.surfaces = surfaces;
		root = createTree(0, surfaces.length);
	}
	
	/**
	 * Create a BVH [sub]tree.  This tree node will be responsible for storing
	 * and processing surfaces[start] to surfaces[end-1]. If the range is small enough,
	 * this will create a leaf BvhNode. Otherwise, the surfaces will be sorted according
	 * to the axis of the axis-aligned bounding box that is widest, and split into 2
	 * children.
	 * 
	 * @param start The start index of surfaces
	 * @param end The end index of surfaces
	 */
	private BvhNode createTree(int start, int end) {

		// ==== Step 1 ====
		// Find out the BIG bounding box enclosing all the surfaces in the range [start, end)
		// and store them in minB and maxB.
		// Hint: To find the bounding box for each surface, use getMinBound() and getMaxBound() */
		Vector3d minB = new Vector3d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY); 
		Vector3d maxB = new Vector3d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);

		for (int i = start; i < end; i++){
			Vector3d curMax = surfaces[i].getMaxBound();
			maxB.x = Math.max(maxB.x, curMax.x);
			maxB.y = Math.max(maxB.y, curMax.y);
			maxB.z = Math.max(maxB.z, curMax.z);
			
			Vector3d curMin = surfaces[i].getMinBound();
			minB.x = Math.min(minB.x, curMin.x);
			minB.y = Math.min(minB.y, curMin.y);
			minB.z = Math.min(minB.z, curMin.z);
		}
		

		// ==== Step 2 ====
		// Check for the base case. 
		// If the range [start, end) is small enough (e.g. less than or equal to 10), just return a new leaf node.
		if (end - start <= 10){
			BvhNode leafNode = new BvhNode(minB, maxB, null, null, start, end);
			return leafNode;
		}

		// ==== Step 3 ====
		// Figure out the widest dimension (x or y or z).
		// If x is the widest, set widestDim = 0. If y, set widestDim = 1. If z, set widestDim = 2.
		int widestDim = 0;
		double maxDimSize = maxB.x - minB.x;
		if (maxB.y - minB.y > maxDimSize){
			widestDim = 1;
			maxDimSize = maxB.y - minB.y;
		}
		if (maxB.z - minB.z > maxDimSize){
			widestDim = 2;
			maxDimSize = maxB.z - maxB.z;
		}

		// ==== Step 4 ====
		// Sort surfaces according to the widest dimension.
		cmp.setIndex(widestDim);
		Arrays.sort(surfaces, start, end, cmp);

		// ==== Step 5 ====
		// Recursively create left and right children.
		int center = start + (end - start)/2;
		BvhNode leftChild = createTree(start, center);
		BvhNode rightChild = createTree(center, end);
		
		return new BvhNode(minB, maxB, leftChild, rightChild, start, end);
	}

}

/**
 * A subclass that compares the average position two surfaces by a given
 * axis. Use the setIndex(i) method to select which axis should be considered.
 * i=0 -> x-axis, i=1 -> y-axis, and i=2 -> z-axis.  
 *
 */
class MyComparator implements Comparator<Surface> {
	int index;
	public MyComparator() {  }

	public void setIndex(int index) {
		this.index = index;
	}

	public int compare(Surface o1, Surface o2) {
		double v1 = o1.getAveragePosition().get(index);
		double v2 = o2.getAveragePosition().get(index);
		if(v1 < v2) return 1;
		if(v1 > v2) return -1;
		return 0;
	}

}