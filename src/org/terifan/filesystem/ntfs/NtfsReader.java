package org.terifan.filesystem.ntfs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.terifan.filesystem.ntfs.Unmarshaller.getInt;
import static org.terifan.filesystem.ntfs.Unmarshaller.getShort;
import static org.terifan.filesystem.ntfs.Unmarshaller.setShort;
import static org.terifan.filesystem.ntfs.Unmarshaller.unmarshal;
import org.terifan.pagestore.PageStore;
import org.terifan.util.Debug;


// https://sourceforge.net/p/ntfsreader/
public class NtfsReader
{
	private long VIRTUALFRAGMENT = -1L; // UInt64
	private int ROOTDIRECTORY = 5; // UInt32

	private byte[] BitmapMasks = new byte[]
	{
		1, 2, 4, 8, 16, 32, 64, (byte)128
	};

//	SafeFileHandle _volumeHandle;
	DiskInfoWrapper _diskInfo;
	Node[] _nodes;
	StandardInformation[] _standardInformations;
	Stream[][] _streams;
	PageStore _driveInfo;
	ArrayList<String> _names = new ArrayList<>();
	int _retrieveMode;
	byte[] _bitmapData;

	//preallocate a lot of space for the strings to avoid too much dictionary resizing
	//use ordinal comparison to improve performance
	//this will be deallocated once the MFT reading is finished
	HashMap<String, Integer> _nameIndex = new HashMap<String, Integer>(); // 128 * 1024, StringComparer.Ordinal


	/// Allocate or retrieve an existing index for the particular string.
	/// In order to mimize memory usage, we reuse string as much as possible.
	private int getNameIndex(String name)
	{
		Integer existingIndex = _nameIndex.get(name);
		if (existingIndex != null)
		{
			return existingIndex;
		}

		_names.add(name);
		_nameIndex.put(name, _names.size() - 1);

		return _names.size() - 1;
	}


	/// Get the string from our stringtable from the given index.
	String getNameFromIndex(int aNameIndex)
	{
		return aNameIndex == 0 ? null : _names.get(aNameIndex);
	}


	private Stream searchStream(List<Stream> aStreams, AttributeType aStreamType)
	{
		for (Stream stream : aStreams)
		{
			if (stream.mType == aStreamType)
			{
				return stream;
			}
		}

		return null;
	}


	private Stream searchStream(List<Stream> aStreams, AttributeType aStreamType, int aStreamNameIndex)
	{
		for (Stream stream : aStreams)
		{
			if (stream.mType == aStreamType && stream.NameIndex == aStreamNameIndex)
			{
				return stream;
			}
		}

		return null;
	}


	private void readFile(byte[] aBuffer, int aOffset, int aLength, long aAbsolutePosition)
	{
//		System.out.println("------ " + absolutePosition / 512);

		try
		{
			_driveInfo.read(aAbsolutePosition / 512, aBuffer, aOffset, aLength);
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}

//		NativeOverlapped overlapped = new NativeOverlapped(absolutePosition);
//
//		int read;
//		if (!ReadFile(_volumeHandle, (IntPtr)buffer, (uint)len, out read, ref overlapped))
//			throw new Exception("Unable to read volume information");
//
//		if (read != (uint)len)
//			throw new IllegalStateException("Unable to read volume information");
	}


	/// Read the next contiguous block of information on disk
	private boolean readNextChunk(byte[] buffer, int bufferSize, int nodeIndex, int fragmentIndex, Stream dataStream, AtomicLong BlockStart, AtomicLong BlockEnd, AtomicLong Vcn, AtomicLong RealVcn)
	{
		BlockStart.set(nodeIndex);
		BlockEnd.set(BlockStart.get() + bufferSize / _diskInfo.BytesPerMftRecord);
		if (BlockEnd.get() > dataStream.Size * 8)
		{
			BlockEnd.set(dataStream.Size * 8);
		}

		long u1 = 0;

		int fragmentCount = dataStream.getFragments().size();
		while (fragmentIndex < fragmentCount)
		{
			Fragment fragment = dataStream.getFragments().get(fragmentIndex);

			/* Calculate Inode at the end of the fragment. */
			u1 = (RealVcn.get() + fragment.NextVcn - Vcn.get()) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster / _diskInfo.BytesPerMftRecord;

			if (u1 > nodeIndex)
			{
				break;
			}

			do
			{
				if (fragment.Lcn != VIRTUALFRAGMENT)
				{
					RealVcn.set(RealVcn.get() + fragment.NextVcn - Vcn.get());
				}

				Vcn.set(fragment.NextVcn);

				if (++fragmentIndex >= fragmentCount)
				{
					break;
				}

			}
			while (fragment.Lcn == VIRTUALFRAGMENT);
		}

		if (fragmentIndex >= fragmentCount)
		{
			return false;
		}

		if (BlockEnd.get() >= u1)
		{
			BlockEnd.set(u1);
		}

		long position = (dataStream.getFragments().get(fragmentIndex).Lcn - RealVcn.get()) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster + BlockStart.get() * _diskInfo.BytesPerMftRecord;

		long len = (BlockEnd.get() - BlockStart.get()) * _diskInfo.BytesPerMftRecord;

		readFile(buffer, 0, (int)len, position);

		return true;
	}


	private void initializeDiskInfo()
	{
		byte[] volumeData = new byte[512];

		readFile(volumeData, 0, volumeData.length, 0);

		BootSector bootSector = unmarshal(BootSector.class, volumeData, 0);

		if (bootSector.Signature != 0x202020205346544EL)
		{
			throw new IllegalStateException("This is not an NTFS disk.");
		}

		DiskInfoWrapper diskInfo = new DiskInfoWrapper();
		diskInfo.BytesPerSector = bootSector.BytesPerSector;
		diskInfo.SectorsPerCluster = bootSector.SectorsPerCluster;
		diskInfo.TotalSectors = bootSector.TotalSectors;
		diskInfo.MftStartLcn = bootSector.MftStartLcn;
		diskInfo.Mft2StartLcn = bootSector.Mft2StartLcn;
		diskInfo.ClustersPerMftRecord = bootSector.ClustersPerMftRecord;
		diskInfo.ClustersPerIndexRecord = bootSector.ClustersPerIndexRecord;

		if (bootSector.ClustersPerMftRecord >= 128)
		{
			diskInfo.BytesPerMftRecord = (1L << (byte)(256 - (byte)bootSector.ClustersPerMftRecord));
		}
		else
		{
			diskInfo.BytesPerMftRecord = diskInfo.ClustersPerMftRecord * diskInfo.BytesPerSector * diskInfo.SectorsPerCluster;
		}

		diskInfo.BytesPerCluster = diskInfo.BytesPerSector * diskInfo.SectorsPerCluster;

		if (diskInfo.SectorsPerCluster > 0)
		{
			diskInfo.TotalClusters = diskInfo.TotalSectors / diskInfo.SectorsPerCluster;
		}

		_diskInfo = diskInfo;
	}

//	private void FixupRawMftdata(byte* buffer, UInt64 len)
//	{
//		FileRecordHeader* ntfsFileRecordHeader = (FileRecordHeader*)buffer;
//
//		if (ntfsFileRecordHeader->RecordHeader.Type != RecordType.File)
//			return;
//
//		UInt16* wordBuffer = (UInt16*)buffer;
//
//		UInt16* UpdateSequenceArray = (UInt16*)(buffer + ntfsFileRecordHeader->RecordHeader.UsaOffset);
//		UInt32 increment = (UInt32)_diskInfo.BytesPerSector / sizeof(UInt16);
//
//		UInt32 Index = increment - 1;
//
//		for (int i = 1; i < ntfsFileRecordHeader->RecordHeader.UsaCount; i++)
//		{
//			/* Check if we are inside the buffer. */
//			if (Index * sizeof(UInt16) >= len)
//				throw new Exception("USA data indicates that data is missing, the MFT may be corrupt.");
//
//			// Check if the last 2 bytes of the sector contain the Update Sequence Number.
//			if (wordBuffer[Index] != UpdateSequenceArray[0])
//				throw new Exception("USA fixup word is not equal to the Update Sequence Number, the MFT may be corrupt.");
//
//			/* Replace the last 2 bytes in the sector with the value from the Usa array. */
//			wordBuffer[Index] = UpdateSequenceArray[i];
//			Index = Index + increment;
//		}
//	}

	private void fixupRawMftdata(byte[] aBuffer, int aOffset, int aLength)
	{
		FileRecordHeader ntfsFileRecordHeader = unmarshal(FileRecordHeader.class, aBuffer, aOffset);

		if (ntfsFileRecordHeader.RecordHeader.Type != RecordType.File.CODE)
		{
			return;
		}

		int offsetPtr = aOffset + (0xffff & ntfsFileRecordHeader.RecordHeader.UsaOffset);
		int increment = (int)_diskInfo.BytesPerSector / 2;

		int Index = increment - 1;

		for (int i = 1; i < ntfsFileRecordHeader.RecordHeader.UsaCount; i++)
		{
			if (Index * 2 >= aLength)
			{
				throw new IllegalArgumentException("USA data indicates that data is missing, the MFT may be corrupt.");
			}

			// Check if the last 2 bytes of the sector contain the Update Sequence Number.
			if (getShort(aBuffer, aOffset + 2 * Index) != getShort(aBuffer, offsetPtr))
			{
				throw new IllegalArgumentException("USA fixup word is not equal to the Update Sequence Number, the MFT may be corrupt.");
			}

			// Replace the last 2 bytes in the sector with the value from the Usa array.
			setShort(aBuffer, aOffset + 2 * Index, getShort(aBuffer, offsetPtr + 2 * i));
			Index = Index + increment;
		}
	}


	private static long processRunLength(byte[] aRunData, int aRunLengthSize, AtomicInteger aOffset)
	{
		long runLength = 0;
		for (int i = 0; i < aRunLengthSize; i++)
		{
			runLength += (long)(0xff & aRunData[aOffset.getAndIncrement()]) << (8 * i);

//			if (index.incrementAndGet() >= end)
//				throw new IllegalStateException("Datarun is longer than buffer, the MFT may be corrupt.");
		}
		return runLength;
	}


	private static long processRunOffset(byte[] aRunData, int aRunOffsetSize, AtomicInteger aOffset)
	{
		byte[] runOffsetBytes = new byte[8];
		int i = 0;
		for (; i < aRunOffsetSize; i++)
		{
			runOffsetBytes[i] = aRunData[aOffset.getAndIncrement()];

//			if (index.incrementAndGet() >= end)
//				throw new IllegalStateException("Datarun is longer than buffer, the MFT may be corrupt.");
		}

		if ((0xff & aRunData[aOffset.get() - 1]) >= 0x80)
		{
			while (i < 8)
			{
				runOffsetBytes[i++] = (byte)0xFF;
			}
		}

		return Unmarshaller.getLong(runOffsetBytes, 0);
	}


	private void processAttributes(AtomicReference<Node> aNode, int aNodeIndex, byte[] aBuffer, int aPointer, long aBufferLength, short aInstance, int aDepth, List<Stream> aStreams, boolean aIsMftNode)
	{
		Attribute attribute = null;

		for (int AttributeOffset = aPointer, bufEnd = aPointer + (int)aBufferLength; AttributeOffset < bufEnd; AttributeOffset += attribute.Length)
		{
			// exit the loop if end-marker.
			if ((AttributeOffset + 4 <= bufEnd) && getInt(aBuffer, AttributeOffset) == -1)
			{
				break;
			}

			attribute = unmarshal(Attribute.class, aBuffer, AttributeOffset);

			//make sure we did read the data correctly
			if ((AttributeOffset + 4 > bufEnd) || attribute.Length < 3 || (AttributeOffset + attribute.Length > bufEnd))
			{
				throw new IllegalStateException("Error: attribute in Inode %I64u is bigger than the data, the MFT may be corrupt." + AttributeOffset + " " + attribute.Length + " " + aBufferLength + " " + attribute.Length);
			}

			//attributes list needs to be processed at the end
			if (attribute.AttributeType == AttributeType.AttributeAttributeList.CODE)
			{
				continue;
			}

			/* If the Instance does not equal the AttributeNumber then ignore the attribute.
			   This is used when an AttributeList is being processed and we only want a specific
			   instance. */
			if ((aInstance != (short)65535) && (aInstance != attribute.AttributeNumber))
			{
				continue;
			}

			if (attribute.Nonresident == 0)
			{
				ResidentAttribute residentAttribute = unmarshal(ResidentAttribute.class, aBuffer, AttributeOffset);

				switch (AttributeType.decode(attribute.AttributeType))
				{
					case AttributeFileName:
						AttributeFileName attributeFileName = unmarshal(AttributeFileName.class, aBuffer, AttributeOffset + residentAttribute.ValueOffset);

						if (attributeFileName.ParentDirectory.InodeNumberHighPart > 0)
						{
							throw new IllegalStateException("48 bits inode are not supported to reduce memory footprint.");
						}

//						node.get().ParentNodeIndex = ((long)attributeFileName.ParentDirectory.InodeNumberHighPart << 32) + attributeFileName.ParentDirectory.InodeNumberLowPart;
						aNode.get().ParentNodeIndex = attributeFileName.ParentDirectory.InodeNumberLowPart;

						if (attributeFileName.NameType == 1 || aNode.get().NameIndex == 0)
//							node.get().NameIndex = GetNameIndex(new String(attributeFileName.Name, 0, attributeFileName.NameLength));
						{
							aNode.get().NameIndex = getNameIndex(attributeFileName.Name);
						}

						break;

					case AttributeStandardInformation:
						AttributeStandardInformation attributeStandardInformation = unmarshal(AttributeStandardInformation.class, aBuffer, AttributeOffset + residentAttribute.ValueOffset);

						aNode.get().Attributes |= attributeStandardInformation.FileAttributes;

						if ((_retrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
						{
							_standardInformations[aNodeIndex]
								= new StandardInformation(
									attributeStandardInformation.CreationTime,
									attributeStandardInformation.FileChangeTime,
									attributeStandardInformation.LastAccessTime
								);
						}

						break;

					case AttributeData:
						aNode.get().Size = residentAttribute.ValueLength;
						break;
					default:
						System.out.println("Unsupported: " + AttributeType.decode(attribute.AttributeType));
						break;
				}
			}
			else
			{
				NonResidentAttribute nonResidentAttribute = unmarshal(NonResidentAttribute.class, aBuffer, AttributeOffset);

				//save the length (number of bytes) of the data.
				if (attribute.AttributeType == AttributeType.AttributeData.CODE && aNode.get().Size == 0)
				{
					aNode.get().Size = nonResidentAttribute.DataSize;
				}

				if (aStreams != null)
				{
					//extract the stream name
					int streamNameIndex = 0;
					if (attribute.NameLength > 0)
					{
						streamNameIndex = getNameIndex(new String(aBuffer, (AttributeOffset + attribute.NameOffset), (int)attribute.NameLength));
					}

					//find or create the stream
					Stream stream = searchStream(aStreams, AttributeType.decode(attribute.AttributeType), streamNameIndex);

					if (stream == null)
					{
						stream = new Stream(streamNameIndex, AttributeType.decode(attribute.AttributeType), nonResidentAttribute.DataSize);
						aStreams.add(stream);
					}
					else if (stream.Size == 0)
					{
						stream.Size = nonResidentAttribute.DataSize;
					}

					//we need the fragment of the MFTNode so retrieve them this time even if fragments aren't normally read
					if (aIsMftNode || (_retrieveMode & RetrieveMode.Fragments.CODE) == RetrieveMode.Fragments.CODE)
					{
						processFragments(
							stream,
							aBuffer,
							AttributeOffset + nonResidentAttribute.RunArrayOffset,
							attribute.Length - nonResidentAttribute.RunArrayOffset,
							nonResidentAttribute.StartingVcn
						);
					}
				}
			}
		}

		if (aStreams != null && aStreams.size() > 0)
		{
			aNode.get().Size = aStreams.get(0).Size;
		}
	}


	private void processFragments(Stream stream, byte[] runData, int offset, int runDataLength, long StartingVcn)
	{
		/* Walk through the RunData and add the extents. */
		AtomicInteger index = new AtomicInteger(offset); // uint
		long lcn = 0; // Int64
		long vcn = StartingVcn; // Int64
		int runOffsetSize = 0;
		int runLengthSize = 0;

		while (runData[index.get()] != 0)
		{
			/* Decode the RunData and calculate the next Lcn. */
			runLengthSize = runData[index.get()] & 0x0F;
			runOffsetSize = (runData[index.get()] & 0xF0) >> 4;

			if (index.incrementAndGet() >= index.get() + runDataLength)
			{
				throw new IllegalStateException("Error: datarun is longer than buffer, the MFT may be corrupt. " + index.incrementAndGet() + " >= " + index.get() + " + " + runDataLength);
			}

			long runLength = processRunLength(runData, runLengthSize, index);

			long runOffset = processRunOffset(runData, runOffsetSize, index);

			lcn += runOffset;
			vcn += runLength;

			/* Add the size of the fragment to the total number of clusters.
			   There are two kinds of fragments: real and virtual. The latter do not
			   occupy clusters on disk, but are information used by compressed
			   and sparse files. */
			if (runOffset != 0)
			{
				stream.Clusters += runLength;
			}

			stream.getFragments().add(
				new Fragment(runOffset == 0 ? VIRTUALFRAGMENT : lcn, vcn)
			);
		}
	}


	/// Process an actual MFT record from the buffer
	private boolean processMftRecord(byte[] aBuffer, int aOffset, long aLength, int aNodeIndex, AtomicReference<Node> aNode, List<Stream> aStreams, boolean aIsMftNode)
	{
		aNode.set(new Node());

		FileRecordHeader ntfsFileRecordHeader = unmarshal(FileRecordHeader.class, aBuffer, aOffset);

		if (ntfsFileRecordHeader.RecordHeader.Type != RecordType.File.CODE)
		{
			return false;
		}

		//the inode is not in use
		if ((ntfsFileRecordHeader.Flags & 1) != 1)
		{
			return false;
		}

		long baseInode = (ntfsFileRecordHeader.BaseFileRecord.InodeNumberHighPart << 32) + ntfsFileRecordHeader.BaseFileRecord.InodeNumberLowPart;

		//This is an inode extension used in an AttributeAttributeList of another inode, don't parse it
		if (baseInode != 0)
		{
			return false;
		}

		if (ntfsFileRecordHeader.AttributeOffset >= aLength)
		{
			throw new IllegalStateException("Error: attributes in Inode %I64u are outside the FILE record, the MFT may be corrupt.");
		}

		if (ntfsFileRecordHeader.BytesInUse > aLength)
		{
			throw new IllegalStateException("Error: in Inode %I64u the record is bigger than the size of the buffer, the MFT may be corrupt.");
		}

		//make the file appear in the rootdirectory by default
		aNode.get().ParentNodeIndex = ROOTDIRECTORY;

		if ((ntfsFileRecordHeader.Flags & 2) == 2)
		{
			aNode.get().Attributes |= Attributes.Directory.CODE;
		}

		processAttributes(aNode, aNodeIndex, aBuffer, (0xffff & ntfsFileRecordHeader.AttributeOffset), aLength - (0xffff & ntfsFileRecordHeader.AttributeOffset), (short)65535, 0, aStreams, aIsMftNode);

		return true;
	}


	private byte[] processBitmapData(List<Stream> aStreams)
	{
		long Vcn = 0;
		int MaxMftBitmapBytes = 0;

		Stream bitmapStream = searchStream(aStreams, AttributeType.AttributeBitmap);
		if (bitmapStream == null)
		{
			throw new IllegalArgumentException("No Bitmap Data");
		}

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.Lcn != VIRTUALFRAGMENT)
			{
				MaxMftBitmapBytes += (fragment.NextVcn - Vcn) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster;
			}

			Vcn = fragment.NextVcn;
		}

		byte[] bitmapData = new byte[MaxMftBitmapBytes];

		Vcn = 0;
		long RealVcn = 0; // UInt64

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.Lcn != VIRTUALFRAGMENT)
			{
				readFile(
					bitmapData,
					(int)(RealVcn * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster),
					(int)((fragment.NextVcn - Vcn) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster),
					fragment.Lcn * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster
				);

				RealVcn = RealVcn + fragment.NextVcn - Vcn;
			}

			Vcn = fragment.NextVcn;
		}

		return bitmapData;
	}


	private Node[] processMft()
	{
		int bufferSize = 256 * 1024;

		byte[] buffer = new byte[bufferSize];

		// Read the $MFT record from disk into memory, which is always the first record in the MFT.
		readFile(buffer, 0, (int)_diskInfo.BytesPerMftRecord, _diskInfo.MftStartLcn * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster);

		// Fixup the raw data from disk. This will also test if it's a valid $MFT record.
		fixupRawMftdata(buffer, 0, (int)_diskInfo.BytesPerMftRecord);

		List<Stream> mftStreams = new ArrayList<Stream>();

		if ((_retrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
		{
			_standardInformations = new StandardInformation[1]; //allocate some space for $MFT record
		}
		AtomicReference<Node> mftNode = new AtomicReference<>();

		if (!processMftRecord(buffer, 0, _diskInfo.BytesPerMftRecord, 0, mftNode, mftStreams, true))
		{
			throw new IllegalArgumentException("Can't interpret Mft Record");
		}

		// the bitmap data contains all used inodes on the disk
		_bitmapData = processBitmapData(mftStreams);

		Stream dataStream = searchStream(mftStreams, AttributeType.AttributeData);

		int maxInode = (int)_bitmapData.length * 8;
		if (maxInode > (int)(dataStream.Size / _diskInfo.BytesPerMftRecord))
		{
			maxInode = (int)(dataStream.Size / _diskInfo.BytesPerMftRecord);
		}

		Node[] nodes = new Node[maxInode];
		nodes[0] = mftNode.get();

		if ((_retrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
		{
			StandardInformation mftRecordInformation = _standardInformations[0];
			_standardInformations = new StandardInformation[maxInode];
			_standardInformations[0] = mftRecordInformation;
		}

		if ((_retrieveMode & RetrieveMode.Streams.CODE) == RetrieveMode.Streams.CODE)
		{
			_streams = new Stream[maxInode][];
		}

		AtomicLong BlockStart = new AtomicLong(); // UInt64
		AtomicLong BlockEnd = new AtomicLong(); // UInt64
		AtomicLong RealVcn = new AtomicLong(); // UInt64
		AtomicLong Vcn = new AtomicLong(); // UInt64

		long totalBytesRead = 0;
		int fragmentIndex = 0;
		for (int nodeIndex = 1; nodeIndex < maxInode; nodeIndex++)
		{
			// Ignore the Inode if the bitmap says it's not in use.
			if ((_bitmapData[nodeIndex >> 3] & BitmapMasks[nodeIndex % 8]) == 0)
			{
				continue;
			}

			if (nodeIndex >= BlockEnd.get())
			{
				if (!readNextChunk(buffer, bufferSize, nodeIndex, fragmentIndex, dataStream, BlockStart, BlockEnd, Vcn, RealVcn))
				{
					break;
				}

				totalBytesRead += (BlockEnd.get() - BlockStart.get()) * _diskInfo.BytesPerMftRecord;
			}

			fixupRawMftdata(
				buffer,
				(int)((nodeIndex - BlockStart.get()) * _diskInfo.BytesPerMftRecord),
				(int)_diskInfo.BytesPerMftRecord
			);

			List<Stream> streams = null;
			if ((_retrieveMode & RetrieveMode.Streams.CODE) == RetrieveMode.Streams.CODE)
			{
				streams = new ArrayList<Stream>();
			}

			AtomicReference<Node> newNode = new AtomicReference<>();

			if (!processMftRecord(
				buffer,
				(int)((nodeIndex - BlockStart.get()) * _diskInfo.BytesPerMftRecord),
				(int)_diskInfo.BytesPerMftRecord,
				nodeIndex,
				newNode,
				streams,
				false))
			{
				continue;
			}

			nodes[nodeIndex] = newNode.get();

			if (streams != null)
			{
				_streams[nodeIndex] = streams.toArray(new Stream[0]);
			}
		}

		System.out.printf("%f MB of volume metadata has been read", (float)totalBytesRead / (1024 * 1024));

		return nodes;
	}


	// Recurse the node hierarchy and construct its entire name stopping at the root directory.
	String getNodeFullNameCore(long aNodeIndex)
	{
		long node = aNodeIndex;

		ArrayDeque<Long> fullPathNodes = new ArrayDeque<Long>();
		fullPathNodes.add(node);

		Long lastNode = node;
		while (true)
		{
			long parent = _nodes[(int)node].ParentNodeIndex;

			//loop until we reach the root directory
			if (parent == ROOTDIRECTORY)
			{
				break;
			}

			if (parent == lastNode)
			{
				throw new InvalidDataException("Detected a loop in the tree structure.");
			}

			fullPathNodes.push(parent);

			lastNode = node;
			node = parent;
		}

		StringBuilder fullPath = new StringBuilder();

		while (fullPathNodes.size() > 0)
		{
			node = fullPathNodes.pop();

			fullPath.append("\\");
			fullPath.append(getNameFromIndex(_nodes[(int)node].NameIndex));
		}

		return fullPath.toString();
	}


	public NtfsReader(PageStore aDriveInfo, int aRetrieveMode)
	{
		_driveInfo = aDriveInfo;
		_retrieveMode = aRetrieveMode;

		initializeDiskInfo();

		_nodes = processMft();
		_nameIndex = null;
	}


	public IDiskInfo getDiskInfo()
	{
		return _diskInfo;
	}


	public List<INode> getNodes(String aRootPath)
	{
		List<INode> nodes = new ArrayList<INode>();

		int nodeCount = _nodes.length;

		for (int i = 0; i < nodeCount; i++)
		{
			System.out.println(_nodes[i].NameIndex + " " + getNodeFullNameCore(i));

			if (_nodes[i].NameIndex != 0 && getNodeFullNameCore(i).startsWith(aRootPath))
			{
				nodes.add(new NodeWrapper(this, i, _nodes[i]));
			}
		}

		return nodes;
	}


	public byte[] getVolumeBitmap()
	{
		return _bitmapData;
	}
}
