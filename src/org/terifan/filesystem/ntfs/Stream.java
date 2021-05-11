package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


class Stream
{
	public long Clusters; // UInt64                      // Total number of clusters.
	public long Size; // UInt64                          // Total number of bytes.
	public AttributeType Type;
	public int NameIndex;
	public List<Fragment> _fragments;

	public Stream(int nameIndex, AttributeType type, long size)
	{
		NameIndex = nameIndex;
		Type = type;
		Size = size;
	}

	public List<Fragment> getFragments()
	{
		if (_fragments == null)
			_fragments = new ArrayList<Fragment>();

		return _fragments;
	}


	@Override
	public String toString()
	{
		return "Stream{" + "Clusters=" + Clusters + ", Size=" + Size + ", Type=" + Type + ", NameIndex=" + NameIndex + ", _fragments=" + _fragments.size() + '}';
	}
}
