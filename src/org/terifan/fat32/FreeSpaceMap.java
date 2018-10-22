package org.terifan.fat32;

import org.terifan.util.Tuple;
import java.util.TreeMap;


class FreeSpaceMap
{
	private TreeMap<Integer,Integer> mMap;

	
	public FreeSpaceMap()
	{
		mMap = new TreeMap<>();
	}


	public Tuple<Integer,Integer> alloc(int aPageCount)
	{
		for (Integer freeBlockOffset : mMap.keySet())
		{
			//if (mMap.get(freeBlockOffset) >= aPageCount)
			{
				int freeBlockLength = mMap.remove(freeBlockOffset);

				if (freeBlockLength > aPageCount)
				{
					mMap.put(freeBlockOffset+aPageCount, freeBlockLength-aPageCount);
				}

				return new Tuple<>(freeBlockOffset, Math.min(freeBlockLength, aPageCount));
			}
		}

		return null;
	}


	public void free(int aPageIndex, int aPageCount)
	{
		// merge new free block with next free block

		Integer ceilKey = mMap.ceilingKey(aPageIndex);
		if (ceilKey != null && aPageIndex + aPageCount == ceilKey)
		{
			aPageCount += mMap.get(ceilKey);
			mMap.remove(ceilKey);
		}

		// merge new free block with previous free block

		Integer floorKey = mMap.floorKey(aPageIndex);
		if (floorKey != null)
		{
			int pageCount = mMap.get(floorKey);
			if (floorKey + pageCount == aPageIndex)
			{
				aPageIndex = floorKey;
				aPageCount += pageCount;
			}
		}

		mMap.put(aPageIndex, aPageCount);
	}


	protected void debug()
	{
		System.out.println(mMap);
	}

/*
	public static void main(String... args)
	{
		try
		{
			FreeSpaceMap map = new FreeSpaceMap();
			map.free(15, 2);
			map.free(21, 2);
			map.free(25, 2);
			map.free(29, 2);
			map.free(27, 2);
			map.free(17, 4);
			System.out.println(map.alloc(4));
			System.out.println(map.mMap);
		}
		catch (Exception e)
		{
			e.printStackTrace(System.out);
		}
	}
*/
}