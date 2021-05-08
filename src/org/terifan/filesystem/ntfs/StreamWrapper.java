package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


// Add some functionality to the basic stream
class StreamWrapper implements IStream
{
	NtfsReader _reader;
	NodeWrapper _parentNode;
	int _streamIndex;

	public StreamWrapper(NtfsReader reader, NodeWrapper parentNode, int streamIndex)
	{
		_reader = reader;
		_parentNode = parentNode;
		_streamIndex = streamIndex;
	}

	public String getName()
	{
		return _reader.GetNameFromIndex(_reader._streams[_parentNode.getNodeIndex()][_streamIndex].NameIndex);
	}

	public long getSize()
	{
		return _reader._streams[_parentNode.getNodeIndex()][_streamIndex].Size;
	}

	public List<IFragment> getFragments()
	{
		//if ((_reader._retrieveMode & RetrieveMode.Fragments) != RetrieveMode.Fragments)
		//    throw new NotSupportedException("The fragments haven't been retrieved. Make sure to use the proper RetrieveMode.");

		List<Fragment> fragments = _reader._streams[_parentNode.getNodeIndex()][_streamIndex].getFragments();

		if (fragments == null || fragments.size() == 0)
			return null;

		List<IFragment> newFragments = new ArrayList<IFragment>();
		for (Fragment fragment : fragments)
			newFragments.add(new FragmentWrapper(this, fragment));

		return newFragments;
	}
}
