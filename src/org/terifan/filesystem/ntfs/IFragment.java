package org.terifan.filesystem.ntfs;


public interface IFragment
{
	// Logical cluster number, location on disk.
	long getLcn();


	// Virtual cluster number of next fragment.
	long getNextVcn();
}
