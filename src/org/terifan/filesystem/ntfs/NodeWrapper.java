package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


// Add some functionality to the basic node
class NodeWrapper implements INode
{
	NtfsReader _reader;
	int _nodeIndex; // UInt32
	Node _node;
	String _fullName;

	public NodeWrapper(NtfsReader reader, int nodeIndex, Node node) // UInt32
	{
		_reader = reader;
		_nodeIndex = nodeIndex;
		_node = node;
	}

	public int getNodeIndex()
	{
		return _nodeIndex;
	}

	public int getParentNodeIndex()
	{
		return _node.ParentNodeIndex;
	}

	public int getAttributes()
	{
		return _node.Attributes;
	}

	public String getName()
	{
		return _reader.GetNameFromIndex(_node.NameIndex);
	}

	public long getSize() // UInt64
	{
		return _node.Size;
	}

	public String getFullName()
	{
		if (_fullName == null)
			_fullName = _reader.getNodeFullNameCore(_nodeIndex);

		return _fullName;
	}

	public List<IStream> getStreams()
	{
		if (_reader._streams == null)
			throw new IllegalStateException("The streams haven't been retrieved. Make sure to use the proper RetrieveMode.");

		Stream[] streams = _reader._streams[_nodeIndex];
		if (streams == null)
			return null;

		List<IStream> newStreams = new ArrayList<IStream>();
		for (int i = 0; i < streams.length; ++i)
			newStreams.add(new StreamWrapper(_reader, this, i));

		return newStreams;
	}

	public DateTime getCreationTime()
	{
		if (_reader._standardInformations == null)
			throw new IllegalStateException("The StandardInformation haven't been retrieved. Make sure to use the proper RetrieveMode.");

		return _reader._standardInformations[_nodeIndex].CreationTime;
	}

	public DateTime getLastChangeTime()
	{
		if (_reader._standardInformations == null)
			throw new IllegalStateException("The StandardInformation haven't been retrieved. Make sure to use the proper RetrieveMode.");

		return _reader._standardInformations[_nodeIndex].LastChangeTime;
	}

	public DateTime getLastAccessTime()
	{
		if (_reader._standardInformations == null)
			throw new IllegalStateException("The StandardInformation haven't been retrieved. Make sure to use the proper RetrieveMode.");

		return _reader._standardInformations[_nodeIndex].LastAccessTime;
	}
}
