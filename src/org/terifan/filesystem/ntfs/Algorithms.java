package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class Algorithms
{
	public static HashMap<Long, List<INode>> AggregateByFragments(Iterable<INode> nodes, Long minimumFragments)
	{
		HashMap<Long, List<INode>> fragmentsAggregate = new HashMap<Long, List<INode>>();

		for (INode node : nodes)
		{
			List<IStream> streams = node.getStreams();
			if (streams == null || streams.size() == 0)
				continue;

			List<IFragment> fragments = streams.get(0).getFragments();
			if (fragments == null)
				continue;

			Long fragmentCount = (long)fragments.size();

			if (fragmentCount < minimumFragments)
				continue;

			List<INode> nodeList = fragmentsAggregate.get(fragmentCount);

			if (nodeList == null)
			{
				nodeList = new ArrayList<INode>();
				fragmentsAggregate.put(fragmentCount, nodeList);
			}

			nodeList.add(node);
		}

		return fragmentsAggregate;
	}

	public static HashMap<Long, List<INode>> AggregateBySize(Iterable<INode> nodes, Long minimumSize)
	{
		HashMap<Long, List<INode>> sizeAggregate = new HashMap<Long, List<INode>>();

		for (INode node : nodes)
		{
			if (Attributes.Directory.isSet(node.getAttributes()) || node.getSize() < minimumSize)
				continue;

			List<INode> nodeList = sizeAggregate.get(node.getSize());

			if (nodeList == null)
			{
				nodeList = new ArrayList<INode>();
				sizeAggregate.put(node.getSize(), nodeList);
			}

			nodeList.add(node);
		}

		return sizeAggregate;
	}
}
