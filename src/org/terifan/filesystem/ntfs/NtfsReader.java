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

	private byte[] BitmapMasks = new byte[] { 1, 2, 4, 8, 16, 32, 64, (byte)128 };

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
	private int GetNameIndex(String name)
	{
		Integer existingIndex = _nameIndex.get(name);
		if (existingIndex != null)
			return existingIndex;

		_names.add(name);
		_nameIndex.put(name, _names.size() - 1);

		return _names.size() - 1;
	}

	/// Get the string from our stringtable from the given index.
	String GetNameFromIndex(int nameIndex)
	{
		return nameIndex == 0 ? null : _names.get(nameIndex);
	}

	private Stream SearchStream(List<Stream> streams, AttributeType streamType)
	{
		//since the number of stream is usually small, we can afford O(n)
		for (Stream stream : streams)
			if (stream.Type == streamType)
				return stream;

		return null;
	}

	private Stream SearchStream(List<Stream> streams, AttributeType streamType, int streamNameIndex)
	{
		//since the number of stream is usually small, we can afford O(n)
		for (Stream stream : streams)
			if (stream.Type == streamType &&
				stream.NameIndex == streamNameIndex)
				return stream;

		return null;
	}

	private void ReadFile(byte[] buffer, int offset, int len, long absolutePosition)
	{
//		System.out.println("------ " + absolutePosition / 512);

		try
		{
			_driveInfo.read(absolutePosition / 512, buffer, offset, len);
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
	private boolean ReadNextChunk(
		byte[] buffer,
		int bufferSize, // UInt32
		int nodeIndex, // UInt32
		int fragmentIndex,
		Stream dataStream,
		AtomicLong BlockStart, // ref UInt64
		AtomicLong BlockEnd, // ref UInt64
		AtomicLong Vcn, // ref UInt64
		AtomicLong RealVcn // ref UInt64
		)
	{
		BlockStart.set(nodeIndex);
		BlockEnd.set(BlockStart.get() + bufferSize / _diskInfo.BytesPerMftRecord);
		if (BlockEnd.get() > dataStream.Size * 8)
			BlockEnd.set(dataStream.Size * 8);

		long u1 = 0;

		int fragmentCount = dataStream.getFragments().size();
		while (fragmentIndex < fragmentCount)
		{
			Fragment fragment = dataStream.getFragments().get(fragmentIndex);

			/* Calculate Inode at the end of the fragment. */
			u1 = (RealVcn.get() + fragment.NextVcn - Vcn.get()) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster / _diskInfo.BytesPerMftRecord;

			if (u1 > nodeIndex)
				break;

			do
			{
				if (fragment.Lcn != VIRTUALFRAGMENT)
					RealVcn.set(RealVcn.get() + fragment.NextVcn - Vcn.get());

				Vcn.set(fragment.NextVcn);

				if (++fragmentIndex >= fragmentCount)
					break;

			} while (fragment.Lcn == VIRTUALFRAGMENT);
		}

		if (fragmentIndex >= fragmentCount)
			return false;

		if (BlockEnd.get() >= u1)
			BlockEnd.set(u1);

		long position = (dataStream.getFragments().get(fragmentIndex).Lcn - RealVcn.get()) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster + BlockStart.get() * _diskInfo.BytesPerMftRecord;

		long len = (BlockEnd.get() - BlockStart.get()) * _diskInfo.BytesPerMftRecord;

		ReadFile(buffer, 0, (int)len, position);

		return true;
	}

	/// Gather basic disk information we need to interpret data
	private void InitializeDiskInfo()
	{
		byte[] volumeData = new byte[512];

		ReadFile(volumeData, 0, volumeData.length, 0);

		Debug.hexDump(volumeData);

		BootSector bootSector = unmarshal(BootSector.class, volumeData, 0);

		if (bootSector.Signature != 0x202020205346544EL)
			throw new IllegalStateException("This is not an NTFS disk.");

		DiskInfoWrapper diskInfo = new DiskInfoWrapper();
		diskInfo.BytesPerSector = bootSector.BytesPerSector;
		diskInfo.SectorsPerCluster = bootSector.SectorsPerCluster;
		diskInfo.TotalSectors = bootSector.TotalSectors;
		diskInfo.MftStartLcn = bootSector.MftStartLcn;
		diskInfo.Mft2StartLcn = bootSector.Mft2StartLcn;
		diskInfo.ClustersPerMftRecord = bootSector.ClustersPerMftRecord;
		diskInfo.ClustersPerIndexRecord = bootSector.ClustersPerIndexRecord;

		if (bootSector.ClustersPerMftRecord >= 128)
			diskInfo.BytesPerMftRecord = (1L << (byte)(256 - (byte)bootSector.ClustersPerMftRecord));
		else
			diskInfo.BytesPerMftRecord = diskInfo.ClustersPerMftRecord * diskInfo.BytesPerSector * diskInfo.SectorsPerCluster;

		diskInfo.BytesPerCluster = diskInfo.BytesPerSector * diskInfo.SectorsPerCluster;

		if (diskInfo.SectorsPerCluster > 0)
			diskInfo.TotalClusters = diskInfo.TotalSectors / diskInfo.SectorsPerCluster;

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

	/// Used to check/adjust data before we begin to interpret it
	private void FixupRawMftdata(byte[] buffer, int offset, int len) // UInt64
	{
		FileRecordHeader ntfsFileRecordHeader = unmarshal(FileRecordHeader.class, buffer, offset);

		if (ntfsFileRecordHeader.RecordHeader.Type != RecordType.File.CODE)
			return;

//		short[] wordBuffer = new short[buffer.length]; //(UInt16*)buffer;

		int offsetPtr = offset + (0xffff & ntfsFileRecordHeader.RecordHeader.UsaOffset);
		int increment = (int)_diskInfo.BytesPerSector / 2;

		int Index = increment - 1;

		for (int i = 1; i < ntfsFileRecordHeader.RecordHeader.UsaCount; i++)
		{
			/* Check if we are inside the buffer. */
			if (Index * 2 >= len)
				throw new IllegalArgumentException("USA data indicates that data is missing, the MFT may be corrupt.");

			//System.out.println(i+" "+Index+" "+getShort(buffer, offset + 2 * Index)+" != "+getShort(buffer, offsetPtr));

			// Check if the last 2 bytes of the sector contain the Update Sequence Number.
			if (getShort(buffer, offset + 2 * Index) != getShort(buffer, offsetPtr))
				throw new IllegalArgumentException("USA fixup word is not equal to the Update Sequence Number, the MFT may be corrupt.");

			/* Replace the last 2 bytes in the sector with the value from the Usa array. */
			setShort(buffer, offset + 2 * Index, getShort(buffer, offsetPtr + 2 * i));
			Index = Index + increment;
		}
	}


	/// Decode the RunLength value.
//	private static Int64 ProcessRunLength(byte* runData, UInt32 runDataLength, Int32 runLengthSize, ref UInt32 index)
	private static long ProcessRunLength(byte[] runData, int runDataEnd, int runLengthSize, AtomicInteger index) // Int64, Int32, Int32, ref Int32
	{
//		Int64 runLength = 0;
//		byte* runLengthBytes = (byte*)&runLength;
//		for (int i = 0; i < runLengthSize; i++)
//		{
//			runLengthBytes[i] = runData[index];
//			if (++index >= runDataLength)
//				throw new Exception("Datarun is longer than buffer, the MFT may be corrupt.");
//		}
//		return runLength;
		long runLength = 0;
		byte[] runLengthBytes = new byte[runLengthSize];
		for (int i = 0; i < runLengthSize; i++)
		{
			runLengthBytes[i] = runData[index.get()];
			if (index.incrementAndGet() >= runDataEnd)
				throw new IllegalStateException("Datarun is longer than buffer, the MFT may be corrupt.");
		}
		return runLength;
	}

	/// Decode the RunOffset value.
//	private static Int64 ProcessRunOffset(byte* runData, UInt32 runDataLength, Int32 runOffsetSize, ref UInt32 index)
//	{
//		Int64 runOffset = 0;
//		byte* runOffsetBytes = (byte*)&runOffset;
//
//		int i;
//		for (i = 0; i < runOffsetSize; i++)
//		{
//			runOffsetBytes[i] = runData[index];
//			if (++index >= runDataLength)
//				throw new Exception("Datarun is longer than buffer, the MFT may be corrupt.");
//		}
//
//		//process negative values
//		if (runOffsetBytes[i - 1] >= 0x80)
//			while (i < 8)
//				runOffsetBytes[i++] = 0xFF;
//
//		return runOffset;
	private static long ProcessRunOffset(byte[] runData, int runDataEnd, int runOffsetSize, AtomicInteger index) // UInt64
	{
		long runOffset = 0;
		byte[] runOffsetBytes = new byte[runOffsetSize];

		int i;
		for (i = 0; i < runOffsetSize; i++)
		{
			runOffsetBytes[i] = runData[index.get()];
			if (index.incrementAndGet() >= runDataEnd)
				throw new IllegalStateException("Datarun is longer than buffer, the MFT may be corrupt.");
		}

		//process negative values
		if (runOffsetBytes[i - 1] >= 0x80)
			while (i < 8)
				runOffsetBytes[i++] = (byte)0xFF;

		return runOffset;
	}

	/// Read the data that is specified in a RunData list from disk into memory,
	/// skipping the first Offset bytes.
	private byte[] ProcessNonResidentData(
		byte[] RunData,
		int RunDataLength, // UInt32
		int offset, // UInt64         /* Bytes to skip from begin of data. */
		long WantedLength // UInt64    /* Number of bytes to read. */
		)
	{
		/* Sanity check. */
		if (RunData == null || RunDataLength == 0)
			throw new IllegalArgumentException("nothing to read");

		if (WantedLength >= Integer.MAX_VALUE)
			throw new IllegalArgumentException("too many bytes to read");

		/* We have to round up the WantedLength to the nearest sector. For some
		   reason or other Microsoft has decided that raw reading from disk can
		   only be done by whole sector, even though ReadFile() accepts it's
		   parameters in bytes. */
		if (WantedLength % _diskInfo.BytesPerSector > 0)
			WantedLength += _diskInfo.BytesPerSector - (WantedLength % _diskInfo.BytesPerSector);

		/* Walk through the RunData and read the requested data from disk. */
		AtomicInteger Index = new AtomicInteger(); // UInt32
		long Lcn = 0; // Int64
		long Vcn = 0; // Int64

		byte[] buffer = new byte[(int)WantedLength];

		while (RunData[Index.get()] != 0)
		{
			/* Decode the RunData and calculate the next Lcn. */
			int RunLengthSize = (RunData[Index.get()] & 0x0F);
			int RunOffsetSize = ((RunData[Index.get()] & 0xF0) >> 4);

			if (Index.incrementAndGet() >= RunDataLength)
				throw new IllegalArgumentException("Error: datarun is longer than buffer, the MFT may be corrupt.");

			long RunLength = ProcessRunLength(RunData, offset + RunDataLength, RunLengthSize, Index);

			long RunOffset = ProcessRunOffset(RunData, offset + RunDataLength, RunOffsetSize, Index);

			// Ignore virtual extents.
			if (RunOffset == 0 || RunLength == 0)
				continue;

			Lcn += RunOffset;
			Vcn += RunLength;

			/* Determine how many and which bytes we want to read. If we don't need
			   any bytes from this extent then loop. */
			long ExtentVcn = (long)((Vcn - RunLength) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster);
			long ExtentLcn = (long)(Lcn * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster);
			long ExtentLength = (long)(RunLength * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster);

			if (offset >= ExtentVcn + ExtentLength)
				continue;

			if (offset > ExtentVcn)
			{
				ExtentLcn = ExtentLcn + offset - ExtentVcn;
				ExtentLength = ExtentLength - (offset - ExtentVcn);
				ExtentVcn = offset;
			}

			if (offset + WantedLength <= ExtentVcn)
				continue;

			if (offset + WantedLength < ExtentVcn + ExtentLength)
				ExtentLength = offset + WantedLength - ExtentVcn;

			if (ExtentLength == 0)
				continue;

//			ReadFile(bufPtr + ExtentVcn - Offset, ExtentLength, ExtentLcn);
			ReadFile(buffer, (int)(ExtentVcn - offset), (int)ExtentLength, ExtentLcn);
		}

		return buffer;
	}

	/// Process each attributes and gather information when necessary
	private void ProcessAttributes(AtomicReference<Node> node, int nodeIndex, byte[] aBuffer, int ptr, long BufLength, short instance, int depth, List<Stream> streams, boolean isMftNode)
	{
		Attribute attribute = null;

		for (int AttributeOffset = ptr, bufEnd = ptr + (int)BufLength; AttributeOffset < bufEnd; AttributeOffset += attribute.Length)
		{
			// exit the loop if end-marker.
			if ((AttributeOffset + 4 <= bufEnd) && getInt(aBuffer, AttributeOffset) == -1)
				break;

			attribute = unmarshal(Attribute.class, aBuffer, AttributeOffset);

			//make sure we did read the data correctly
			if ((AttributeOffset + 4 > bufEnd) || attribute.Length < 3 || (AttributeOffset + attribute.Length > bufEnd))
				throw new IllegalStateException("Error: attribute in Inode %I64u is bigger than the data, the MFT may be corrupt." + AttributeOffset+" "+attribute.Length+" "+BufLength+" "+attribute.Length);

			//attributes list needs to be processed at the end
			if (attribute.AttributeType == AttributeType.AttributeAttributeList.CODE)
				continue;

			/* If the Instance does not equal the AttributeNumber then ignore the attribute.
			   This is used when an AttributeList is being processed and we only want a specific
			   instance. */
			if ((instance != (short)65535) && (instance != attribute.AttributeNumber))
				continue;

			if (attribute.Nonresident == 0)
			{
				ResidentAttribute residentAttribute = unmarshal(ResidentAttribute.class, aBuffer, AttributeOffset);

				switch (AttributeType.decode(attribute.AttributeType))
				{
					case AttributeFileName:
						AttributeFileName attributeFileName = unmarshal(AttributeFileName.class, aBuffer, AttributeOffset + residentAttribute.ValueOffset);

						if (attributeFileName.ParentDirectory.InodeNumberHighPart > 0)
							throw new IllegalStateException("48 bits inode are not supported to reduce memory footprint.");

//						node.get().ParentNodeIndex = ((long)attributeFileName.ParentDirectory.InodeNumberHighPart << 32) + attributeFileName.ParentDirectory.InodeNumberLowPart;
						node.get().ParentNodeIndex = attributeFileName.ParentDirectory.InodeNumberLowPart;

						if (attributeFileName.NameType == 1 || node.get().NameIndex == 0)
//							node.get().NameIndex = GetNameIndex(new String(attributeFileName.Name, 0, attributeFileName.NameLength));
							node.get().NameIndex = GetNameIndex(attributeFileName.Name);

						break;

					case AttributeStandardInformation:
						AttributeStandardInformation attributeStandardInformation = unmarshal(AttributeStandardInformation.class, aBuffer, AttributeOffset + residentAttribute.ValueOffset);

						node.get().Attributes |= attributeStandardInformation.FileAttributes;

						if ((_retrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
							_standardInformations[nodeIndex] =
								new StandardInformation(
									attributeStandardInformation.CreationTime,
									attributeStandardInformation.FileChangeTime,
									attributeStandardInformation.LastAccessTime
								);

						break;

					case AttributeData:
						node.get().Size = residentAttribute.ValueLength;
						break;
				}
			}
			else
			{
				NonResidentAttribute nonResidentAttribute = unmarshal(NonResidentAttribute.class, aBuffer, AttributeOffset);

				//save the length (number of bytes) of the data.
				if (attribute.AttributeType == AttributeType.AttributeData.CODE && node.get().Size == 0)
					node.get().Size = nonResidentAttribute.DataSize;

				if (streams != null)
				{
					//extract the stream name
					int streamNameIndex = 0;
					if (attribute.NameLength > 0)
						streamNameIndex = GetNameIndex(new String(aBuffer, (AttributeOffset + attribute.NameOffset), (int)attribute.NameLength));

					//find or create the stream
					Stream stream =	SearchStream(streams, AttributeType.decode(attribute.AttributeType), streamNameIndex);

					if (stream == null)
					{
						stream = new Stream(streamNameIndex, AttributeType.decode(attribute.AttributeType), nonResidentAttribute.DataSize);
						streams.add(stream);
					}
					else if (stream.Size == 0)
						stream.Size = nonResidentAttribute.DataSize;

					//we need the fragment of the MFTNode so retrieve them this time
					//even if fragments aren't normally read
					if (isMftNode || (_retrieveMode & RetrieveMode.Fragments.CODE) == RetrieveMode.Fragments.CODE)
						ProcessFragments(
							node,
							stream,
							aBuffer,
							AttributeOffset + nonResidentAttribute.RunArrayOffset,
							attribute.Length - nonResidentAttribute.RunArrayOffset,
							nonResidentAttribute.StartingVcn
						);
				}
			}
		}

		if (streams != null && streams.size() > 0)
			node.get().Size = streams.get(0).Size;
	}

	/// Process fragments for streams
	private void ProcessFragments(
		AtomicReference<Node> node,
		Stream stream,
		byte[] runData,
		int offset,
		int runDataLength,
		long StartingVcn)
	{
		if (runData == null)
			return;

		/* Walk through the RunData and add the extents. */
		AtomicInteger index = new AtomicInteger(offset); // uint
		long lcn = 0; // Int64
		long vcn = StartingVcn; // Int64
		int runOffsetSize = 0;
		int runLengthSize = 0;

		while (runData[index.get()] != 0)
		{
			/* Decode the RunData and calculate the next Lcn. */
			runLengthSize = (runData[index.get()] & 0x0F);
			runOffsetSize = ((runData[index.get()] & 0xF0) >> 4);

			if (index.incrementAndGet() >= offset + runDataLength)
				throw new IllegalStateException("Error: datarun is longer than buffer, the MFT may be corrupt. " + index.incrementAndGet()+" >= "+offset+" + "+runDataLength);

			long runLength = ProcessRunLength(runData, offset + runDataLength, runLengthSize, index);

			long runOffset = ProcessRunOffset(runData, offset + runDataLength, runOffsetSize, index);

			lcn += runOffset;
			vcn += runLength;

			/* Add the size of the fragment to the total number of clusters.
			   There are two kinds of fragments: real and virtual. The latter do not
			   occupy clusters on disk, but are information used by compressed
			   and sparse files. */
			if (runOffset != 0)
				stream.Clusters += runLength;

			stream.getFragments().add(
				new Fragment(
					runOffset == 0 ? VIRTUALFRAGMENT : lcn,
					vcn
				)
			);
		}
	}

	/// Process an actual MFT record from the buffer
//	private boolean ProcessMftRecord(byte* buffer, UInt64 length, UInt32 nodeIndex, out Node node, List<Stream> streams, bool isMftNode)
	private boolean ProcessMftRecord(byte[] aBuffer, int offset, long length, int nodeIndex, AtomicReference<Node> node, List<Stream> streams, boolean isMftNode)
	{
		node.set(new Node());

		Debug.hexDump(aBuffer, offset, (int)length);

		FileRecordHeader ntfsFileRecordHeader = unmarshal(FileRecordHeader.class, aBuffer, offset);

		if (ntfsFileRecordHeader.RecordHeader.Type != RecordType.File.CODE)
			return false;

		//the inode is not in use
		if ((ntfsFileRecordHeader.Flags & 1) != 1)
			return false;

		long baseInode = (ntfsFileRecordHeader.BaseFileRecord.InodeNumberHighPart << 32) + ntfsFileRecordHeader.BaseFileRecord.InodeNumberLowPart;

		//This is an inode extension used in an AttributeAttributeList of another inode, don't parse it
		if (baseInode != 0)
			return false;

		if (ntfsFileRecordHeader.AttributeOffset >= length)
			throw new IllegalStateException("Error: attributes in Inode %I64u are outside the FILE record, the MFT may be corrupt.");

		if (ntfsFileRecordHeader.BytesInUse > length)
			throw new IllegalStateException("Error: in Inode %I64u the record is bigger than the size of the buffer, the MFT may be corrupt.");

		//make the file appear in the rootdirectory by default
		node.get().ParentNodeIndex = ROOTDIRECTORY;

		if ((ntfsFileRecordHeader.Flags & 2) == 2)
			node.get().Attributes |= Attributes.Directory.CODE;

		ProcessAttributes(node, nodeIndex, aBuffer, (0xffff & ntfsFileRecordHeader.AttributeOffset), length - (0xffff & ntfsFileRecordHeader.AttributeOffset), (short)65535, 0, streams, isMftNode);

		return true;
	}

	/// Process the bitmap data that contains information on inode usage.
	private byte[] ProcessBitmapData(List<Stream> streams)
	{
		long Vcn = 0; // UInt64
		int MaxMftBitmapBytes = 0; // UInt64

		Stream bitmapStream = SearchStream(streams, AttributeType.AttributeBitmap);
		if (bitmapStream == null)
			throw new IllegalArgumentException("No Bitmap Data");

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.Lcn != VIRTUALFRAGMENT)
				MaxMftBitmapBytes += (fragment.NextVcn - Vcn) * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster;

			Vcn = fragment.NextVcn;
		}

		byte[] bitmapData = new byte[MaxMftBitmapBytes];

//		fixed (byte* bitmapDataPtr = bitmapData)

		Vcn = 0;
		long RealVcn = 0; // UInt64

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.Lcn != VIRTUALFRAGMENT)
			{
				ReadFile(
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

	/// Begin the process of interpreting MFT data
	private Node[] ProcessMft()
	{
		int bufferSize = 256 * 1024;

		byte[] buffer = new byte[bufferSize];

		// Read the $MFT record from disk into memory, which is always the first record in the MFT.
		ReadFile(buffer, 0, (int)_diskInfo.BytesPerMftRecord, _diskInfo.MftStartLcn * _diskInfo.BytesPerSector * _diskInfo.SectorsPerCluster);

		// Fixup the raw data from disk. This will also test if it's a valid $MFT record.
		FixupRawMftdata(buffer, 0, (int)_diskInfo.BytesPerMftRecord);

		List<Stream> mftStreams = new ArrayList<Stream>();

		if ((_retrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
			_standardInformations = new StandardInformation[1]; //allocate some space for $MFT record

		AtomicReference<Node> mftNode = new AtomicReference<>();

		if (!ProcessMftRecord(buffer, 0, _diskInfo.BytesPerMftRecord, 0, mftNode, mftStreams, true))
			throw new IllegalArgumentException("Can't interpret Mft Record");

		// the bitmap data contains all used inodes on the disk
		_bitmapData = ProcessBitmapData(mftStreams);

//		OnBitmapDataAvailable();

		Stream dataStream = SearchStream(mftStreams, AttributeType.AttributeData);

		int maxInode = (int)_bitmapData.length * 8;
		if (maxInode > (int)(dataStream.Size / _diskInfo.BytesPerMftRecord))
			maxInode = (int)(dataStream.Size / _diskInfo.BytesPerMftRecord);

		Node[] nodes = new Node[maxInode];
		nodes[0] = mftNode.get();

		if ((_retrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
		{
			StandardInformation mftRecordInformation = _standardInformations[0];
			_standardInformations = new StandardInformation[maxInode];
			_standardInformations[0] = mftRecordInformation;
		}

		if ((_retrieveMode & RetrieveMode.Streams.CODE) == RetrieveMode.Streams.CODE)
			_streams = new Stream[maxInode][];

		/* Read and process all the records in the MFT. The records are read into a
		   buffer and then given one by one to the InterpretMftRecord() subroutine. */

		AtomicLong BlockStart = new AtomicLong(); // UInt64
		AtomicLong BlockEnd = new AtomicLong(); // UInt64
		AtomicLong RealVcn = new AtomicLong(); // UInt64
		AtomicLong Vcn = new AtomicLong(); // UInt64

//		Stopwatch stopwatch = new Stopwatch();
//		stopwatch.Start();

		long totalBytesRead = 0;
		int fragmentIndex = 0;
		int fragmentCount = dataStream.getFragments().size();
		for (int nodeIndex = 1; nodeIndex < maxInode; nodeIndex++)
		{
			// Ignore the Inode if the bitmap says it's not in use.
			if ((_bitmapData[nodeIndex >> 3] & BitmapMasks[nodeIndex % 8]) == 0)
				continue;

			if (nodeIndex >= BlockEnd.get())
			{
				if (!ReadNextChunk(
						buffer,
						bufferSize,
						nodeIndex,
						fragmentIndex,
						dataStream,
						BlockStart,
						BlockEnd,
						Vcn,
						RealVcn))
					break;

				totalBytesRead += (BlockEnd.get() - BlockStart.get()) * _diskInfo.BytesPerMftRecord;
			}

			FixupRawMftdata(
					buffer,
					(int)((nodeIndex - BlockStart.get()) * _diskInfo.BytesPerMftRecord),
					(int)_diskInfo.BytesPerMftRecord
				);

			List<Stream> streams = null;
			if ((_retrieveMode & RetrieveMode.Streams.CODE) == RetrieveMode.Streams.CODE)
				streams = new ArrayList<Stream>();

			AtomicReference<Node> newNode = new AtomicReference<>();

			if (!ProcessMftRecord(
					buffer,
					(int)((nodeIndex - BlockStart.get()) * _diskInfo.BytesPerMftRecord),
					(int)_diskInfo.BytesPerMftRecord,
					nodeIndex,
					newNode,
					streams,
					false))
				continue;

			nodes[nodeIndex] = newNode.get();

			if (streams != null)
				_streams[nodeIndex] = streams.toArray(new Stream[0]);
		}

//		stopwatch.Stop();

//		Trace.WriteLine(
//			string.Format(
//				"{0:F3} MB of volume metadata has been read in {1:F3} s at {2:F3} MB/s",
//				(float)totalBytesRead / (1024*1024),
//				(float)stopwatch.Elapsed.TotalSeconds,
//				((float)totalBytesRead / (1024*1024)) / stopwatch.Elapsed.TotalSeconds
//			)
//		);

		return nodes;
	}


	// Recurse the node hierarchy and construct its entire name
	// stopping at the root directory.
	String getNodeFullNameCore(long nodeIndex)
	{
		long node = nodeIndex;

		ArrayDeque<Long> fullPathNodes = new ArrayDeque<Long>();
		fullPathNodes.add(node);

		Long lastNode = node;
		while (true)
		{
			long parent = _nodes[(int)node].ParentNodeIndex;

			//loop until we reach the root directory
			if (parent == ROOTDIRECTORY)
				break;

			if (parent == lastNode)
				throw new InvalidDataException("Detected a loop in the tree structure.");

			fullPathNodes.push(parent);

			lastNode = node;
			node = parent;
		}

		StringBuilder fullPath = new StringBuilder();
//		fullPath.Append(_driveInfo.Name.TrimEnd(new char[] { '\\' }));

		while (fullPathNodes.size() > 0)
		{
			node = fullPathNodes.pop();

			fullPath.append("\\");
			fullPath.append(GetNameFromIndex(_nodes[(int)node].NameIndex));
		}

		return fullPath.toString();
	}


////	[DllImport("kernel32", CharSet = CharSet.Auto, BestFitMapping = false)]
////	private static extern bool GetVolumeNameForVolumeMountPoint(String volumeName, StringBuilder uniqueVolumeName, int uniqueNameBufferCapacity);
////
////	[DllImport("kernel32", CharSet = CharSet.Auto, BestFitMapping = false)]
////	private static extern SafeFileHandle CreateFile(string lpFileName, FileAccess fileAccess, FileShare fileShare, IntPtr lpSecurityAttributes, FileMode fileMode, int dwFlagsAndAttributes, IntPtr hTemplateFile);
////
////	[DllImport("kernel32", CharSet = CharSet.Auto)]
////	private static extern bool ReadFile(SafeFileHandle hFile, IntPtr lpBuffer, uint nNumberOfBytesToRead, out uint lpNumberOfBytesRead, ref NativeOverlapped lpOverlapped);
//
//	private enum FileMode
//	{
//		Append(6),
//		Create(2),
//		CreateNew(1),
//		Open(3),
//		OpenOrCreate(4),
//		Truncate(5);
//
//		private int mFlag;
//
//
//		private FileMode(int aFlag)
//		{
//			mFlag = aFlag;
//		}
//	}

	private enum FileShare
	{
		None(0),
		Read(1),
		Write(2),
		Delete(4),
		All(1 + 2 + 4);

		private int mFlag;


		private FileShare(int aFlag)
		{
			mFlag = aFlag;
		}
	}

	private enum FileAccess
	{
		Read(1),
		ReadWrite(3),
		Write(2);

		private int mFlag;


		private FileAccess(int aFlag)
		{
			mFlag = aFlag;
		}
	}


	/// <param name="driveInfo">The drive you want to read metadata from.</param>
	/// <param name="include">Information to retrieve from each node while scanning the disk</param>
	/// <remarks>Streams & Fragments are expensive to store in memory, if you don't need them, don't retrieve them.</remarks>
	public NtfsReader(PageStore driveInfo, int retrieveMode)
	{
		_driveInfo = driveInfo;
		_retrieveMode = retrieveMode;

//		StringBuilder builder = new StringBuilder(1024);
//		GetVolumeNameForVolumeMountPoint(_driveInfo.RootDirectory.Name, builder, builder.Capacity);

//		String volume = builder.toString().TrimEnd(new char[] { '\\' });
//
//		_volumeHandle =
//			CreateFile(
//				volume,
//				FileAccess.Read,
//				FileShare.All,
//				IntPtr.Zero,
//				FileMode.Open,
//				0,
//				IntPtr.Zero
//				);

//		if (_volumeHandle == null || _volumeHandle.IsInvalid)
//			throw new IllegalStateException(
//				String.format(
//					"Unable to open volume {0}. Make sure it exists and that you have Administrator privileges.",
//					driveInfo
//				)
//			);

		InitializeDiskInfo();

		_nodes = ProcessMft();

		//cleanup anything that isn't used anymore
		_nameIndex = null;
//		_volumeHandle = null;
	}

	public IDiskInfo getDiskInfo()
	{
		return _diskInfo;
	}

	/// Get all nodes under the specified rootPath.
	/// <param name="rootPath">The rootPath must at least contains the drive and may include any number of subdirectories. Wildcards aren't supported.</param>
	public List<INode> GetNodes(String rootPath)
	{
//		Stopwatch stopwatch = new Stopwatch();
//		stopwatch.Start();

		List<INode> nodes = new ArrayList<INode>();

		//TODO use Parallel.Net to process this when it becomes available
		int nodeCount = _nodes.length;
		for (int i = 0; i < nodeCount; ++i)
			if (_nodes[i].NameIndex != 0 && getNodeFullNameCore(i).startsWith(rootPath))
				nodes.add(new NodeWrapper(this, i, _nodes[i]));

//		stopwatch.Stop();

//		Trace.WriteLine(
//			string.Format(
//				"{0} node{1} have been retrieved in {2} ms",
//				nodes.Count,
//				nodes.Count > 1 ? "s" : string.Empty,
//				(float)stopwatch.ElapsedTicks / TimeSpan.TicksPerMillisecond
//			)
//		);

		return nodes;
	}

	public byte[] GetVolumeBitmap()
	{
		return _bitmapData;
	}

//	public void Dispose()
//	{
//		if (_volumeHandle != null)
//		{
//			_volumeHandle.Dispose();
//			_volumeHandle = null;
//		}
//	}


//	private class NativeOverlapped
//	{
//		public int privateLow; // IntPtr
//		public int privateHigh; // IntPtr
//		public long Offset; // UInt64
//		public int EventHandle; // IntPtr
//
//		public NativeOverlapped(long offset) // UInt64
//		{
//			Offset = offset;
//			EventHandle = IntPtr.Zero;
//			privateLow = IntPtr.Zero;
//			privateHigh = IntPtr.Zero;
//		}
//	}
}
