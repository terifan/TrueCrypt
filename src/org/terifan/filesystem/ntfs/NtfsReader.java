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

	private PageStore mPageStore;
	private DiskInfoWrapper mDiskInfo;
	private Node[] mNodes;
	private ArrayList<String> mNames;
	private int mRetrieveMode;
	private byte[] mBitmapData;
	private HashMap<String, Integer> mNameIndex;
	StandardInformation[] mStandardInformations;
	Stream[][] mStreams;


	public NtfsReader(PageStore aDriveInfo, int aRetrieveMode)
	{
		mNames = new ArrayList<>();
		mNameIndex = new HashMap<>();
		mPageStore = aDriveInfo;
		mRetrieveMode = aRetrieveMode;

		initializeDiskInfo();

		mNodes = processMft();
	}


	/// Allocate or retrieve an existing index for the particular string.
	/// In order to mimize memory usage, we reuse string as much as possible.
	private int getNameIndex(String name)
	{
		Integer existingIndex = mNameIndex.get(name);
		if (existingIndex != null)
		{
			return existingIndex;
		}

		mNames.add(name);
		mNameIndex.put(name, mNames.size() - 1);

		return mNames.size() - 1;
	}


	/// Get the string from our stringtable from the given index.
	String getNameFromIndex(int aNameIndex)
	{
		return aNameIndex == 0 ? "" : mNames.get(aNameIndex);
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
			if (stream.mType == aStreamType && stream.mNameIndex == aStreamNameIndex)
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
			mPageStore.read(aAbsolutePosition / 512, aBuffer, aOffset, aLength);
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
		BlockEnd.set(BlockStart.get() + bufferSize / mDiskInfo.mBytesPerMftRecord);
		if (BlockEnd.get() > dataStream.mSize * 8)
		{
			BlockEnd.set(dataStream.mSize * 8);
		}

		long u1 = 0;

		int fragmentCount = dataStream.getFragments().size();
		while (fragmentIndex < fragmentCount)
		{
			Fragment fragment = dataStream.getFragments().get(fragmentIndex);

			// Calculate Inode at the end of the fragment.
			u1 = (RealVcn.get() + fragment.mNextVcn - Vcn.get()) * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster / mDiskInfo.mBytesPerMftRecord;

			if (u1 > nodeIndex)
			{
				break;
			}

			do
			{
				if (fragment.mLcn != VIRTUALFRAGMENT)
				{
					RealVcn.set(RealVcn.get() + fragment.mNextVcn - Vcn.get());
				}

				Vcn.set(fragment.mNextVcn);

				if (++fragmentIndex >= fragmentCount)
				{
					break;
				}

			}
			while (fragment.mLcn == VIRTUALFRAGMENT);
		}

		if (fragmentIndex >= fragmentCount)
		{
			return false;
		}

		if (BlockEnd.get() >= u1)
		{
			BlockEnd.set(u1);
		}

		long position = (dataStream.getFragments().get(fragmentIndex).mLcn - RealVcn.get()) * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster + BlockStart.get() * mDiskInfo.mBytesPerMftRecord;

		long len = (BlockEnd.get() - BlockStart.get()) * mDiskInfo.mBytesPerMftRecord;

		readFile(buffer, 0, (int)len, position);

		return true;
	}


	private void initializeDiskInfo()
	{
		byte[] volumeData = new byte[512];

		readFile(volumeData, 0, volumeData.length, 0);

		BootSector bootSector = unmarshal(BootSector.class, volumeData, 0);

		if (bootSector.mSignature != 0x202020205346544EL)
		{
			throw new IllegalStateException("This is not an NTFS disk.");
		}

		DiskInfoWrapper diskInfo = new DiskInfoWrapper();
		diskInfo.mBytesPerSector = bootSector.mBytesPerSector;
		diskInfo.mSectorsPerCluster = bootSector.mSectorsPerCluster;
		diskInfo.mTotalSectors = bootSector.mTotalSectors;
		diskInfo.mMftStartLcn = bootSector.mMftStartLcn;
		diskInfo.mMft2StartLcn = bootSector.mMft2StartLcn;
		diskInfo.mClustersPerMftRecord = bootSector.mClustersPerMftRecord;
		diskInfo.mClustersPerIndexRecord = bootSector.mClustersPerIndexRecord;

		if (bootSector.mClustersPerMftRecord >= 128)
		{
			diskInfo.mBytesPerMftRecord = (1L << (byte)(256 - (byte)bootSector.mClustersPerMftRecord));
		}
		else
		{
			diskInfo.mBytesPerMftRecord = diskInfo.mClustersPerMftRecord * diskInfo.mBytesPerSector * diskInfo.mSectorsPerCluster;
		}

		diskInfo.mBytesPerCluster = diskInfo.mBytesPerSector * diskInfo.mSectorsPerCluster;

		if (diskInfo.mSectorsPerCluster > 0)
		{
			diskInfo.mTotalClusters = diskInfo.mTotalSectors / diskInfo.mSectorsPerCluster;
		}

		mDiskInfo = diskInfo;
	}


	private void fixupRawMftdata(byte[] aBuffer, int aOffset, int aLength)
	{
		FileRecordHeader ntfsFileRecordHeader = unmarshal(FileRecordHeader.class, aBuffer, aOffset);

		if (ntfsFileRecordHeader.mRecordHeader.mType != RecordType.File.CODE)
		{
			return;
		}

		int offsetPtr = aOffset + (0xffff & ntfsFileRecordHeader.mRecordHeader.mUsaOffset);
		int increment = (int)mDiskInfo.mBytesPerSector / 2;

		int Index = increment - 1;

		for (int i = 1; i < ntfsFileRecordHeader.mRecordHeader.mUsaCount; i++)
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

		for (int AttributeOffset = aPointer, bufEnd = aPointer + (int)aBufferLength; AttributeOffset < bufEnd; AttributeOffset += attribute.mLength)
		{
			// exit the loop if end-marker.
			if ((AttributeOffset + 4 <= bufEnd) && getInt(aBuffer, AttributeOffset) == -1)
			{
				break;
			}

			attribute = unmarshal(Attribute.class, aBuffer, AttributeOffset);

			//make sure we did read the data correctly
			if ((AttributeOffset + 4 > bufEnd) || attribute.mLength < 3 || (AttributeOffset + attribute.mLength > bufEnd))
			{
				throw new IllegalStateException("Error: attribute in Inode %I64u is bigger than the data, the MFT may be corrupt." + AttributeOffset + " " + attribute.mLength + " " + aBufferLength + " " + attribute.mLength);
			}

			//attributes list needs to be processed at the end
			if (attribute.mAttributeType == AttributeType.AttributeAttributeList.CODE)
			{
				continue;
			}

			// If the Instance does not equal the AttributeNumber then ignore the attribute.
			// This is used when an AttributeList is being processed and we only want a specific instance.
			if ((aInstance != (short)65535) && (aInstance != attribute.mAttributeNumber))
			{
				continue;
			}

			if (attribute.mNonresident == 0)
			{
				ResidentAttribute residentAttribute = unmarshal(ResidentAttribute.class, aBuffer, AttributeOffset);

				switch (AttributeType.decode(attribute.mAttributeType))
				{
					case AttributeFileName:
						AttributeFileName attributeFileName = unmarshal(AttributeFileName.class, aBuffer, AttributeOffset + residentAttribute.mValueOffset);

						if (attributeFileName.mParentDirectory.InodeNumberHighPart > 0)
						{
							throw new IllegalStateException("48 bits inode are not supported to reduce memory footprint.");
						}

//						node.get().ParentNodeIndex = ((long)attributeFileName.ParentDirectory.InodeNumberHighPart << 32) + attributeFileName.ParentDirectory.InodeNumberLowPart;
						aNode.get().mParentNodeIndex = attributeFileName.mParentDirectory.InodeNumberLowPart;

						if (attributeFileName.mNameType == 1 || aNode.get().mNameIndex == 0)
//							node.get().NameIndex = GetNameIndex(new String(attributeFileName.Name, 0, attributeFileName.NameLength));
						{
							aNode.get().mNameIndex = getNameIndex(attributeFileName.mName);
						}

						break;

					case AttributeStandardInformation:
						AttributeStandardInformation attributeStandardInformation = unmarshal(AttributeStandardInformation.class, aBuffer, AttributeOffset + residentAttribute.mValueOffset);

						aNode.get().mAttributes |= attributeStandardInformation.mFileAttributes;

						if ((mRetrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
						{
							mStandardInformations[aNodeIndex]
								= new StandardInformation(
									attributeStandardInformation.mCreationTime,
									attributeStandardInformation.mFileChangeTime,
									attributeStandardInformation.mLastAccessTime
								);
						}

						break;

					case AttributeData:
						aNode.get().mSize = residentAttribute.mValueLength;
						break;
					default:
						System.out.println("Unsupported: " + AttributeType.decode(attribute.mAttributeType));
						break;
				}
			}
			else
			{
				NonResidentAttribute nonResidentAttribute = unmarshal(NonResidentAttribute.class, aBuffer, AttributeOffset);

				//save the length (number of bytes) of the data.
				if (attribute.mAttributeType == AttributeType.AttributeData.CODE && aNode.get().mSize == 0)
				{
					aNode.get().mSize = nonResidentAttribute.mDataSize;
				}

				if (aStreams != null)
				{
					int streamNameIndex = 0;
					if (attribute.mNameLength > 0)
					{
						streamNameIndex = getNameIndex(new String(aBuffer, (AttributeOffset + attribute.mNameOffset), (int)attribute.mNameLength));
					}

					Stream stream = searchStream(aStreams, AttributeType.decode(attribute.mAttributeType), streamNameIndex);

					if (stream == null)
					{
						stream = new Stream(streamNameIndex, AttributeType.decode(attribute.mAttributeType), nonResidentAttribute.mDataSize);
						aStreams.add(stream);
					}
					else if (stream.mSize == 0)
					{
						stream.mSize = nonResidentAttribute.mDataSize;
					}

					//we need the fragment of the MFTNode so retrieve them this time even if fragments aren't normally read
					if (aIsMftNode || (mRetrieveMode & RetrieveMode.Fragments.CODE) == RetrieveMode.Fragments.CODE)
					{
						processFragments(stream, aBuffer, AttributeOffset + nonResidentAttribute.mRunArrayOffset, attribute.mLength - nonResidentAttribute.mRunArrayOffset, nonResidentAttribute.mStartingVcn);
					}
				}
			}
		}

		if (aStreams != null && aStreams.size() > 0)
		{
			aNode.get().mSize = aStreams.get(0).mSize;
		}
	}


	private void processFragments(Stream aStream, byte[] aRunData, int aOffset, int aRunDataLength, long aStartingVcn)
	{
		long lcn = 0;
		long vcn = aStartingVcn;
		int runOffsetSize = 0;
		int runLengthSize = 0;

		AtomicInteger index = new AtomicInteger(aOffset);

		while (aRunData[index.get()] != 0)
		{
			// Decode the RunData and calculate the next Lcn.
			runLengthSize = aRunData[index.get()] & 0x0F;
			runOffsetSize = (aRunData[index.get()] & 0xF0) >> 4;

			if (index.incrementAndGet() >= index.get() + aRunDataLength)
			{
				throw new IllegalStateException("Error: datarun is longer than buffer, the MFT may be corrupt. " + index.incrementAndGet() + " >= " + index.get() + " + " + aRunDataLength);
			}

			long runLength = processRunLength(aRunData, runLengthSize, index);

			long runOffset = processRunOffset(aRunData, runOffsetSize, index);

			lcn += runOffset;
			vcn += runLength;

			// Add the size of the fragment to the total number of clusters. There are two kinds of fragments: real and virtual. The
			// latter do not occupy clusters on disk, but are information used by compressed and sparse files.
			if (runOffset != 0)
			{
				aStream.mClusters += runLength;
			}

			aStream.getFragments().add(
				new Fragment(runOffset == 0 ? VIRTUALFRAGMENT : lcn, vcn)
			);
		}
	}


	/// Process an actual MFT record from the buffer
	private boolean processMftRecord(byte[] aBuffer, int aOffset, long aLength, int aNodeIndex, AtomicReference<Node> aNode, List<Stream> aStreams, boolean aIsMftNode)
	{
		aNode.set(new Node());

		FileRecordHeader ntfsFileRecordHeader = unmarshal(FileRecordHeader.class, aBuffer, aOffset);

		if (ntfsFileRecordHeader.mRecordHeader.mType != RecordType.File.CODE)
		{
			return false;
		}

		//the inode is not in use
		if ((ntfsFileRecordHeader.mFlags & 1) != 1)
		{
			return false;
		}

		long baseInode = (ntfsFileRecordHeader.mBaseFileRecord.InodeNumberHighPart << 32) + ntfsFileRecordHeader.mBaseFileRecord.InodeNumberLowPart;

		//This is an inode extension used in an AttributeAttributeList of another inode, don't parse it
		if (baseInode != 0)
		{
			return false;
		}

		if (ntfsFileRecordHeader.mAttributeOffset >= aLength)
		{
			throw new IllegalStateException("Error: attributes in Inode %I64u are outside the FILE record, the MFT may be corrupt.");
		}

		if (ntfsFileRecordHeader.mBytesInUse > aLength)
		{
			throw new IllegalStateException("Error: in Inode %I64u the record is bigger than the size of the buffer, the MFT may be corrupt.");
		}

		//make the file appear in the rootdirectory by default
		aNode.get().mParentNodeIndex = ROOTDIRECTORY;

		if ((ntfsFileRecordHeader.mFlags & 2) == 2)
		{
			aNode.get().mAttributes |= Attributes.Directory.CODE;
		}

		processAttributes(aNode, aNodeIndex, aBuffer, (0xffff & ntfsFileRecordHeader.mAttributeOffset), aLength - (0xffff & ntfsFileRecordHeader.mAttributeOffset), (short)65535, 0, aStreams, aIsMftNode);

		return true;
	}


	private byte[] processBitmapData(List<Stream> aStreams)
	{
		long vcn = 0;
		int maxMftBitmapBytes = 0;

		Stream bitmapStream = searchStream(aStreams, AttributeType.AttributeBitmap);
		if (bitmapStream == null)
		{
			throw new IllegalArgumentException("No Bitmap Data");
		}

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.mLcn != VIRTUALFRAGMENT)
			{
				maxMftBitmapBytes += (fragment.mNextVcn - vcn) * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster;
			}

			vcn = fragment.mNextVcn;
		}

		byte[] bitmapData = new byte[maxMftBitmapBytes];

		vcn = 0;
		long realVcn = 0; // UInt64

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.mLcn != VIRTUALFRAGMENT)
			{
				int offset = (int)(realVcn * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster);
				int length = (int)((fragment.mNextVcn - vcn) * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster);
				long absolutePosition = fragment.mLcn * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster;

				readFile(bitmapData, offset, length, absolutePosition);

				realVcn = realVcn + fragment.mNextVcn - vcn;
			}

			vcn = fragment.mNextVcn;
		}

		return bitmapData;
	}


	private Node[] processMft()
	{
		int bufferSize = 256 * 1024;

		byte[] buffer = new byte[bufferSize];

		// Read the $MFT record from disk into memory, which is always the first record in the MFT.
		readFile(buffer, 0, (int)mDiskInfo.mBytesPerMftRecord, mDiskInfo.mMftStartLcn * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster);

		// Fixup the raw data from disk. This will also test if it's a valid $MFT record.
		fixupRawMftdata(buffer, 0, (int)mDiskInfo.mBytesPerMftRecord);

		List<Stream> mftStreams = new ArrayList<Stream>();

		if ((mRetrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
		{
			mStandardInformations = new StandardInformation[1]; //allocate some space for $MFT record
		}

		AtomicReference<Node> mftNode = new AtomicReference<>();

		if (!processMftRecord(buffer, 0, mDiskInfo.mBytesPerMftRecord, 0, mftNode, mftStreams, true))
		{
			throw new IllegalArgumentException("Can't interpret Mft Record");
		}

		// the bitmap data contains all used inodes on the disk
		mBitmapData = processBitmapData(mftStreams);

		Stream dataStream = searchStream(mftStreams, AttributeType.AttributeData);

		int maxInode = (int)mBitmapData.length * 8;
		if (maxInode > (int)(dataStream.mSize / mDiskInfo.mBytesPerMftRecord))
		{
			maxInode = (int)(dataStream.mSize / mDiskInfo.mBytesPerMftRecord);
		}

		Node[] nodes = new Node[maxInode];
		nodes[0] = mftNode.get();

		if ((mRetrieveMode & RetrieveMode.StandardInformations.CODE) == RetrieveMode.StandardInformations.CODE)
		{
			StandardInformation mftRecordInformation = mStandardInformations[0];
			mStandardInformations = new StandardInformation[maxInode];
			mStandardInformations[0] = mftRecordInformation;
		}

		if ((mRetrieveMode & RetrieveMode.Streams.CODE) == RetrieveMode.Streams.CODE)
		{
			mStreams = new Stream[maxInode][];
		}

		AtomicLong blockStart = new AtomicLong();
		AtomicLong blockEnd = new AtomicLong();
		AtomicLong realVcn = new AtomicLong();
		AtomicLong vcn = new AtomicLong();

		long totalBytesRead = 0;
		int fragmentIndex = 0;
		for (int nodeIndex = 1; nodeIndex < maxInode; nodeIndex++)
		{
			if ((mBitmapData[nodeIndex >> 3] & (1 << (nodeIndex & 7))) == 0)
			{
				continue;
			}

			if (nodeIndex >= blockEnd.get())
			{
				if (!readNextChunk(buffer, bufferSize, nodeIndex, fragmentIndex, dataStream, blockStart, blockEnd, vcn, realVcn))
				{
					break;
				}

				totalBytesRead += (blockEnd.get() - blockStart.get()) * mDiskInfo.mBytesPerMftRecord;
			}

			fixupRawMftdata(buffer, (int)((nodeIndex - blockStart.get()) * mDiskInfo.mBytesPerMftRecord), (int)mDiskInfo.mBytesPerMftRecord);

			List<Stream> streams = null;
			if ((mRetrieveMode & RetrieveMode.Streams.CODE) == RetrieveMode.Streams.CODE)
			{
				streams = new ArrayList<Stream>();
			}

			AtomicReference<Node> newNode = new AtomicReference<>();

			if (!processMftRecord(buffer, (int)((nodeIndex - blockStart.get()) * mDiskInfo.mBytesPerMftRecord), (int)mDiskInfo.mBytesPerMftRecord, nodeIndex, newNode, streams, false))
			{
				continue;
			}

			nodes[nodeIndex] = newNode.get();

			if (streams != null)
			{
				mStreams[nodeIndex] = streams.toArray(new Stream[0]);
			}
		}

		System.out.printf("%.1f MB of volume metadata has been read%n", totalBytesRead / 1024.0 / 1024.0);

		return nodes;
	}


	// Recurse the node hierarchy and construct its entire name stopping at the root directory.
	String getNodeFullNameCore(long aNodeIndex)
	{
		ArrayDeque<Long> fullPathNodes = new ArrayDeque<Long>();
		fullPathNodes.add(aNodeIndex);

		Long lastNode = aNodeIndex;
		for (;;)
		{
			long parent = mNodes[(int)aNodeIndex].mParentNodeIndex;

			if (parent == ROOTDIRECTORY) // loop until we reach the root directory
			{
				break;
			}

			if (parent == lastNode)
			{
				throw new InvalidDataException("Detected a loop in the tree structure.");
			}

			fullPathNodes.push(parent);

			lastNode = aNodeIndex;
			aNodeIndex = parent;
		}

		StringBuilder fullPath = new StringBuilder();

		while (fullPathNodes.size() > 0)
		{
			aNodeIndex = fullPathNodes.pop();

			fullPath.append("\\");
			fullPath.append(getNameFromIndex(mNodes[(int)aNodeIndex].mNameIndex));
		}

		return fullPath.toString();
	}


	public IDiskInfo getDiskInfo()
	{
		return mDiskInfo;
	}


	public List<INode> getNodes(String aRootPath)
	{
		List<INode> nodes = new ArrayList<INode>();

		int nodeCount = mNodes.length;

		System.out.println("*****" + nodeCount);

		for (int i = 0; i < nodeCount; i++)
		{
			System.out.println(mNodes[i].mNameIndex + " " + getNodeFullNameCore(i));

			if (mNodes[i].mNameIndex != 0 && getNodeFullNameCore(i).startsWith(aRootPath))
			{
				nodes.add(new NodeWrapper(this, i, mNodes[i]));
			}
		}

		return nodes;
	}


	public byte[] getVolumeBitmap()
	{
		return mBitmapData;
	}
}
