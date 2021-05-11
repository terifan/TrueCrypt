package org.terifan.filesystem.ntfs;

import java.io.IOException;
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
	private HashMap<Long, Node> mNodes;
	private HashMap<Long, List<Stream>> mStreams;
	private HashMap<Long, StandardInformation> mStandardInformations;
	private ArrayList<String> mNames;
	private int mRetrieveMode;
	private byte[] mBitmapData;
	private HashMap<String, Integer> mNameIndex;


	public NtfsReader(PageStore aDriveInfo, int aRetrieveMode)
	{
		mNames = new ArrayList<>();
		mNameIndex = new HashMap<>();
		mPageStore = aDriveInfo;
		mRetrieveMode = aRetrieveMode;

		initializeDiskInfo();

		processMft();
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
		try
		{
			mPageStore.read(aAbsolutePosition / 512, aBuffer, aOffset, aLength);
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}


	// Read the next contiguous block of information on disk
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


	public byte[] readFragment(IFragment aFragment, int aFragmentOffset, byte[] aBuffer, int aBufferOffset, int aBufferLength)
	{
		long position = aFragment.getLcn() * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster;

		readFile(aBuffer, aBufferOffset, aBufferLength, position + aFragmentOffset);

		return aBuffer;
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


	private void processAttributes(AtomicReference<Node> aNode, int aNodeIndex, byte[] aBuffer, int aOffset, long aBufferLength, short aInstance, int aDepth, List<Stream> oStreams, boolean aIsMftNode)
	{
		Attribute attribute = null;

		for (int attributeOffset = aOffset, bufEnd = aOffset + (int)aBufferLength; attributeOffset < bufEnd; attributeOffset += attribute.mLength)
		{
			// exit the loop if end-marker.
			if ((attributeOffset + 4 <= bufEnd) && getInt(aBuffer, attributeOffset) == -1)
			{
				break;
			}

			attribute = unmarshal(Attribute.class, aBuffer, attributeOffset);

			//make sure we did read the data correctly
			if ((attributeOffset + 4 > bufEnd) || attribute.mLength < 3 || (attributeOffset + attribute.mLength > bufEnd))
			{
				throw new IllegalStateException("Error: attribute in Inode %I64u is bigger than the data, the MFT may be corrupt." + attributeOffset + " " + attribute.mLength + " " + aBufferLength + " " + attribute.mLength);
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
				ResidentAttribute residentAttribute = unmarshal(ResidentAttribute.class, aBuffer, attributeOffset);

				switch (AttributeType.decode(attribute.mAttributeType))
				{
					case AttributeFileName:
					{
						AttributeFileName attr = unmarshal(AttributeFileName.class, aBuffer, attributeOffset + residentAttribute.mValueOffset);

						if (attr.mParentDirectory.mInodeNumberHighPart > 0)
						{
							throw new IllegalStateException("48 bits inode are not supported to reduce memory footprint.");
						}

//						aNode.get().mParentNodeIndex = ((long)attr.mParentDirectory.mInodeNumberHighPart << 32) + attr.mParentDirectory.mInodeNumberLowPart;
						aNode.get().mParentNodeIndex = attr.mParentDirectory.mInodeNumberLowPart;

						if (attr.mNameType == 1 || aNode.get().mNameIndex == 0)
						{
//							aNode.get().mNameIndex = getNameIndex(attr.mName.substring(0, attr.mNameLength));
							aNode.get().mNameIndex = getNameIndex(attr.mName);
						}

						break;
					}
					case AttributeStandardInformation:
					{
						AttributeStandardInformation attr = unmarshal(AttributeStandardInformation.class, aBuffer, attributeOffset + residentAttribute.mValueOffset);

						aNode.get().mAttributes |= attr.mFileAttributes;

						if (RetrieveMode.StandardInformations.isSet(mRetrieveMode))
						{
							mStandardInformations.put((long)aNodeIndex, new StandardInformation(attr.mCreationTime, attr.mFileChangeTime, attr.mLastAccessTime));
						}

						break;
					}
					case AttributeData:
						aNode.get().mSize = residentAttribute.mValueLength;
						break;
					default:
//						System.out.println("Unsupported: " + AttributeType.decode(attribute.mAttributeType));
						break;
				}
			}
			else
			{
				NonResidentAttribute nonResidentAttribute = unmarshal(NonResidentAttribute.class, aBuffer, attributeOffset);

				//save the length (number of bytes) of the data.
				if (attribute.mAttributeType == AttributeType.AttributeData.CODE && aNode.get().mSize == 0)
				{
					aNode.get().mSize = nonResidentAttribute.mDataSize;
				}

				if (oStreams != null)
				{
					int streamNameIndex = 0;
					if (attribute.mNameLength > 0)
					{
						streamNameIndex = getNameIndex(new String(aBuffer, (attributeOffset + attribute.mNameOffset), (int)attribute.mNameLength));
					}

					Stream stream = searchStream(oStreams, AttributeType.decode(attribute.mAttributeType), streamNameIndex);

					if (stream == null)
					{
						stream = new Stream(streamNameIndex, AttributeType.decode(attribute.mAttributeType), nonResidentAttribute.mDataSize);
						oStreams.add(stream);
					}
					else if (stream.mSize == 0)
					{
						stream.mSize = nonResidentAttribute.mDataSize;
					}

					//we need the fragment of the MFTNode so retrieve them this time even if fragments aren't normally read
					if (aIsMftNode || RetrieveMode.Fragments.isSet(mRetrieveMode))
					{
						processFragments(stream, aBuffer, attributeOffset + nonResidentAttribute.mRunArrayOffset, attribute.mLength - nonResidentAttribute.mRunArrayOffset, nonResidentAttribute.mStartingVcn);
					}
				}
			}
		}

		if (oStreams != null && oStreams.size() > 0)
		{
			aNode.get().mSize = oStreams.get(0).mSize;
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
	private boolean processMftRecord(byte[] aBuffer, int aOffset, long aLength, int aNodeIndex, AtomicReference<Node> oNode, List<Stream> oStreams, boolean aIsMftNode)
	{
		oNode.set(new Node());

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

		long baseInode = (ntfsFileRecordHeader.mBaseFileRecord.mInodeNumberHighPart << 32) + ntfsFileRecordHeader.mBaseFileRecord.mInodeNumberLowPart;

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
		oNode.get().mParentNodeIndex = ROOTDIRECTORY;

		if ((ntfsFileRecordHeader.mFlags & 2) == 2)
		{
			oNode.get().mAttributes |= Attributes.Directory.CODE;
		}

		processAttributes(oNode, aNodeIndex, aBuffer, aOffset + (0xffff & ntfsFileRecordHeader.mAttributeOffset), aLength - (0xffff & ntfsFileRecordHeader.mAttributeOffset), (short)65535, 0, oStreams, aIsMftNode);

		return true;
	}


	private byte[] processBitmapData(List<Stream> aStreams)
	{
		long vcn = 0;
		int maxMftBitmapBytes = 0;

		int clusterSize = mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster;

		Stream bitmapStream = searchStream(aStreams, AttributeType.AttributeBitmap);
		if (bitmapStream == null)
		{
			throw new IllegalArgumentException("No Bitmap Data");
		}

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.mLcn != VIRTUALFRAGMENT)
			{
				maxMftBitmapBytes += (fragment.mNextVcn - vcn) * clusterSize;
			}

			vcn = fragment.mNextVcn;
		}

		byte[] bitmapData = new byte[maxMftBitmapBytes];

		vcn = 0;
		long realVcn = 0;

		for (Fragment fragment : bitmapStream.getFragments())
		{
			if (fragment.mLcn != VIRTUALFRAGMENT)
			{
				int offset = (int)(realVcn * clusterSize);
				int length = (int)((fragment.mNextVcn - vcn) * clusterSize);
				long absolutePosition = fragment.mLcn * clusterSize;

				readFile(bitmapData, offset, length, absolutePosition);

				realVcn = realVcn + fragment.mNextVcn - vcn;
			}

			vcn = fragment.mNextVcn;
		}

		return bitmapData;
	}


	private void processMft()
	{
		mNodes = new HashMap<>();
		mStreams = new HashMap<>();
		mStandardInformations = new HashMap<>();

		int bufferSize = 256 * 1024;
		int bytesPerMftRecord = (int)mDiskInfo.mBytesPerMftRecord;

		byte[] buffer = new byte[bufferSize];

		// Read the $MFT record from disk into memory, which is always the first record in the MFT.
		readFile(buffer, 0, bytesPerMftRecord, mDiskInfo.mMftStartLcn * mDiskInfo.mBytesPerSector * mDiskInfo.mSectorsPerCluster);

		// Fixup the raw data from disk. This will also test if it's a valid $MFT record.
		fixupRawMftdata(buffer, 0, bytesPerMftRecord);

		List<Stream> mftStreams = new ArrayList<Stream>();

		AtomicReference<Node> mftNode = new AtomicReference<>();

		if (!processMftRecord(buffer, 0, bytesPerMftRecord, 0, mftNode, mftStreams, true))
		{
			throw new IllegalArgumentException("Can't interpret Mft Record");
		}

		// the bitmap data contains all used inodes on the disk
		mBitmapData = processBitmapData(mftStreams);

		Stream dataStream = searchStream(mftStreams, AttributeType.AttributeData);

		int maxInode = (int)mBitmapData.length * 8;
		if (maxInode > (int)(dataStream.mSize / bytesPerMftRecord))
		{
			maxInode = (int)(dataStream.mSize / bytesPerMftRecord);
		}

		mNodes.put(0L, mftNode.get());

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

				totalBytesRead += (blockEnd.get() - blockStart.get()) * bytesPerMftRecord;
			}

			int offset = (int)((nodeIndex - blockStart.get()) * bytesPerMftRecord);

			fixupRawMftdata(buffer, offset, bytesPerMftRecord);

			List<Stream> streams = null;
			if (RetrieveMode.Streams.isSet(mRetrieveMode))
			{
				streams = new ArrayList<Stream>();
			}

			AtomicReference<Node> newNode = new AtomicReference<>();

			if (!processMftRecord(buffer, offset, bytesPerMftRecord, nodeIndex, newNode, streams, false))
			{
				continue;
			}

			mNodes.put((long)nodeIndex, newNode.get());

			if (streams != null)
			{
				mStreams.put((long)nodeIndex, streams);
			}
		}

		System.out.printf("%.1f MB of volume metadata has been read%n", totalBytesRead / 1024.0 / 1024.0);
	}


	// Recurse the node hierarchy and construct its entire name stopping at the root directory.
	String getNodeFullNameCore(long aNodeIndex)
	{
		ArrayDeque<Long> fullPathNodes = new ArrayDeque<Long>();
		fullPathNodes.add(aNodeIndex);

		Long lastNode = aNodeIndex;
		for (;;)
		{
			long parent = mNodes.get(aNodeIndex).mParentNodeIndex;

			if (parent == ROOTDIRECTORY) // loop until we reach the root directory
			{
				break;
			}

			if (parent == lastNode)
			{
				throw new InvalidDataException("Detected a loop in the tree structure: " + parent);
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
			fullPath.append(getNameFromIndex(mNodes.get(aNodeIndex).mNameIndex));
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

		int nodeCount = mNodes.size();

		for (long i = 0; i < nodeCount; i++)
		{
			if (mNodes.get(i) != null && mNodes.get(i).mNameIndex != 0 && getNodeFullNameCore(i).startsWith(aRootPath))
			{
				nodes.add(new NodeWrapper(this, i, mNodes.get(i)));
			}
		}

		return nodes;
	}


	public byte[] getVolumeBitmap()
	{
		return mBitmapData;
	}


	List<Stream> getStreams(long aNodeIndex)
	{
		return mStreams.get(aNodeIndex);
	}


	StandardInformation getStandardInformations(long aNodeIndex)
	{
		return mStandardInformations.get(aNodeIndex);
	}
}
