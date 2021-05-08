package org.terifan.filesystem.ntfs;


// Node struct for file and directory entries
class Node
{
	public int Attributes; // Attributes
	public int ParentNodeIndex; // UInt32
	public long Size; // UInt64
	public int NameIndex;
}
