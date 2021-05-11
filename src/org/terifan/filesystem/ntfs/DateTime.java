package org.terifan.filesystem.ntfs;

import java.text.SimpleDateFormat;


@Unmarshaller.ValueType
class DateTime
{
	public long time;


	public DateTime()
	{
	}


	@Unmarshaller.ValueTypeConstructor
	public DateTime(long aTime)
	{
		time = aTime;
	}


	@Override
	public String toString()
	{
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time / 10000 - 11644473600000L);
	}
}
