package org.terifan.filesystem.ntfs;

import java.lang.reflect.Field;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;


class Unmarshaller
{
	private final static String CYAN = "\033[0;36m";
	private final static String GREEN = "\033[0;32m";
	private final static String YELLOW = "\033[0;33m";
	private final static String MAGENTA = "\033[0;35m";
	private final static String RESET = "\033[0m";


	public static <T> T unmarshal(Class<T> aType, byte[] aBuffer, int aOffset)
	{
		return unmarshal(aType, aBuffer, new AtomicInteger(aOffset));
	}


	public static <T> T unmarshal(Class<T> aType, byte[] aBuffer, AtomicInteger aOffset)
	{
		System.out.println(CYAN + aType.getSimpleName() + RESET + " {");

		T v = unmarshal(aType, aBuffer, aOffset, "    ");

		System.out.println("}");

		return v;
	}


	private static <T> T unmarshal(Class<T> aType, byte[] aBuffer, AtomicInteger aOffset, String aIndent)
	{
		T instance;

		try
		{
			instance = aType.newInstance();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}

		Field[] fields = aType.getDeclaredFields();

		for (int i = 0; i < fields.length; i++)
		{
			try
			{
				Field field = fields[i];
				Object value = null;

				if (field.getType() == Byte.TYPE)
				{
					value = getByte(aBuffer, aOffset.getAndAdd(1));
					print(aIndent, field, value);
				}
				else if (field.getType() == Short.TYPE)
				{
					value = getShort(aBuffer, aOffset.getAndAdd(2));
					print(aIndent, field, value);
				}
				else if (field.getType() == Integer.TYPE)
				{
					value = getInt(aBuffer, aOffset.getAndAdd(4));
					print(aIndent, field, value);
				}
				else if (field.getType() == Long.TYPE)
				{
					value = getLong(aBuffer, aOffset.getAndAdd(8));
					print(aIndent, field, value);
				}
				else if (field.getType() == String.class)
				{
					Hint hint = field.getAnnotation(Hint.class);

					int len = ((Number)aType.getField(hint.length()).get(instance)).intValue();

					switch (hint.format())
					{
						case UNICODE:
							StringBuilder sb = new StringBuilder();
							for (int j = 0; j < len; j++)
							{
								sb.append((char)getShort(aBuffer, aOffset.getAndAdd(2)));
							}
							value = sb.toString();
							break;
						default:
							throw new IllegalArgumentException();
					}

					print(aIndent, field, "\"" + MAGENTA + value + YELLOW + "\"");
				}
				else if (field.getType().isArray())
				{
					StringBuilder sb = new StringBuilder();

					if (field.getType().getComponentType() == Byte.TYPE)
					{
						byte[] dst = (byte[])field.get(instance);
						for (int j = 0; j < dst.length; j++)
						{
							dst[j] = aBuffer[aOffset.getAndAdd(1)];

							if (j < 16)
							{
								if (sb.length() > 0)
								{
									sb.append(",");
								}
								sb.append(0xff & dst[j]);
							}
							else if (j == 16)
							{
								sb.append(" ... (" + (dst.length - j) + " bytes excluded)");
							}
						}
						value = dst;
					}
					else if (field.getType().getComponentType() == Character.TYPE)
					{
						char[] dst = (char[])field.get(instance);
						if (dst == null)
						{
							Hint hint = field.getAnnotation(Hint.class);
							int len = ((Number)aType.getField(hint.length()).get(instance)).intValue();
							dst = new char[len];
						}
						for (int j = 0; j < dst.length; j++)
						{
							dst[j] = (char)getShort(aBuffer, aOffset.getAndAdd(2));

							if (j < 16)
							{
								if (sb.length() > 0)
								{
									sb.append(",");
								}
								sb.append(0xff & dst[j]);
							}
							else if (j == 16)
							{
								sb.append(" ... (" + (dst.length - j) + " bytes excluded)");
							}
						}
						value = dst;
					}
					else
					{
						throw new IllegalArgumentException("Unsupported: " + field);
					}

					print(aIndent, field, "[" + sb.toString() + "]");
				}
				else
				{
					if (field.getType().getAnnotation(ValueType.class) != null)
					{
						for (Constructor c : field.getType().getConstructors())
						{
							if (c.getAnnotation(ValueTypeConstructor.class) != null)
							{
								Class[] parameterTypes = c.getParameterTypes();

								Object[] params = new Object[parameterTypes.length];

								for (int j = 0; j < parameterTypes.length; j++)
								{
									params[j] = read(parameterTypes[j], aBuffer, aOffset);
								}

								value = c.newInstance(params);
								break;
							}
						}

						print(aIndent, field, "\"" + MAGENTA + value + YELLOW + "\"");
					}
					else
					{
						System.out.println(aIndent + CYAN + field.getType().getSimpleName() + " " + GREEN + field.getName() + RESET + " = {");
						value = unmarshal(field.getType(), aBuffer, aOffset, aIndent + "    ");
						System.out.println(aIndent + "}");
					}
				}

				field.set(instance, value);
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		}

		return instance;
	}


	private static Object read(Class aType, byte[] aBuffer, AtomicInteger aOffset)
	{
		if (aType == Byte.TYPE || aType == Byte.class)
		{
			return getByte(aBuffer, aOffset.getAndAdd(1));
		}
		if (aType == Short.TYPE || aType == Short.class)
		{
			return getShort(aBuffer, aOffset.getAndAdd(2));
		}
		if (aType == Integer.TYPE || aType == Integer.class)
		{
			return getInt(aBuffer, aOffset.getAndAdd(4));
		}
		if (aType == Long.TYPE || aType == Long.class)
		{
			return getLong(aBuffer, aOffset.getAndAdd(8));
		}

		throw new IllegalArgumentException("" + aType);
	}


	private static void print(String aIndent, Field aField, Object aValue)
	{
		System.out.println(aIndent + CYAN + aField.getType().getSimpleName() + " " + GREEN + aField.getName() + " = " + YELLOW + aValue + RESET + ";");
	}


	static byte getByte(byte[] aBuffer, int aOffset)
	{
		int v = 0;
		v += (0xff & aBuffer[aOffset++]);
		return (byte)v;
	}


	static short getShort(byte[] aBuffer, int aOffset)
	{
		int v = 0;
		v += (0xff & aBuffer[aOffset++]);
		v += (0xff & aBuffer[aOffset++]) << 8;
		return (short)v;
	}


	static void setShort(byte[] aBuffer, int aOffset, short aValue)
	{
		aBuffer[aOffset++] = (byte)aValue;
		aBuffer[aOffset++] = (byte)(aValue >>> 8);
	}


	static int getInt(byte[] aBuffer, int aOffset)
	{
		int v = 0;
		v += (0xff & aBuffer[aOffset++]);
		v += (0xff & aBuffer[aOffset++]) << 8;
		v += (0xff & aBuffer[aOffset++]) << 16;
		v += (0xff & aBuffer[aOffset++]) << 24;
		return v;
	}


	static long getLong(byte[] aBuffer, int aOffset)
	{
		long v = 0;
		v += (long)(0xff & aBuffer[aOffset++]);
		v += (long)(0xff & aBuffer[aOffset++]) << 8;
		v += (long)(0xff & aBuffer[aOffset++]) << 16;
		v += (long)(0xff & aBuffer[aOffset++]) << 24;
		v += (long)(0xff & aBuffer[aOffset++]) << 32;
		v += (long)(0xff & aBuffer[aOffset++]) << 40;
		v += (long)(0xff & aBuffer[aOffset++]) << 48;
		v += (long)(0xff & aBuffer[aOffset++]) << 56;
		return v;
	}


	@Target(value = {ElementType.FIELD}) @Retention(value = RetentionPolicy.RUNTIME)
	public @interface Hint
	{
		public String length() default "";

		public Format format() default Format.UNICODE;
	}


	@Target(value = {ElementType.TYPE}) @Retention(value = RetentionPolicy.RUNTIME)
	public @interface ValueType
	{
	}


	@Target(value = {ElementType.CONSTRUCTOR}) @Retention(value = RetentionPolicy.RUNTIME)
	public @interface ValueTypeConstructor
	{
	}


	public enum Format
	{
		BYTES,
		UNICODE,
		UTF8,
	}
}
