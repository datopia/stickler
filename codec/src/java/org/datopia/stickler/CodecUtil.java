package org.datopia.stickler;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

public final class CodecUtil {
  public static boolean readBoolean(final ByteArrayInputStream s) {
    return s.read() == 1;
  }

  public static void writeBoolean(final ByteArrayOutputStream s, final boolean v) {
    s.write(v ? 1 : 0);
  }

  public static int readVarint32(final ByteArrayInputStream s) {
    byte tmp = (byte)s.read();
    if(0 <= tmp)
      return tmp;
    int out = tmp & 0x7f;
    if ((tmp = (byte)s.read()) >= 0) {
      out |= tmp << 7;
    } else {
      out |= (tmp & 0x7f) << 7;
      if ((tmp = (byte)s.read()) >= 0) {
        out |= tmp << 14;
      } else {
        out |= (tmp & 0x7f) << 14;
        if ((tmp = (byte)s.read()) >= 0) {
          out |= tmp << 21;
        } else {
          out |= (tmp & 0x7f) << 21;
          out |= (tmp = (byte)s.read()) << 28;
          if (tmp < 0) {
            for (int i = 0; i < 5; i++)
              if (0 <= (byte)s.read())
                return out;
            throw new RuntimeException("Invalid varint.");
          }
        }
      }
    }
    return out;
  }

  public static void writeVarint32(final ByteArrayOutputStream s, int value) {
    while (true) {
      if ((value & ~0x7F) == 0) {
        s.write(value);
        return;
      } else {
        s.write((value & 0x7F) | 0x80);
        value >>>= 7;
      }
    }
  }

  public static int readUnsigned32(final ByteArrayInputStream s) {
    return readVarint32(s);
  }

  public static void writeUnsigned32(final ByteArrayOutputStream s, final int value) {
    writeVarint32(s, value);
  }

  public static long readVarint64(final ByteArrayInputStream s) {
    int  shift = 0;
    long out   = 0;
    while(shift < 64) {
      final byte b = (byte)s.read();
      out |= (long)(b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return out;
      }
      shift += 7;
    }
    throw new RuntimeException("Invalid varint.");
  }

  public static void writeVarint64(final ByteArrayOutputStream s, long value) {
    while (true) {
      if ((value & ~0x7FL) == 0) {
        s.write((int)value);
        return;
      } else {
        s.write(((int)value & 0x7F) | 0x80);
        value >>>= 7;
      }
    }
  }

  public static long readInt64(final ByteArrayInputStream s) {
    return readVarint64(s);
  }

  public static void writeInt64(final ByteArrayOutputStream s, final long value) {
    writeVarint64(s, value);
  }

  public static long readUnsigned64(final ByteArrayInputStream s) {
    return readVarint64(s);
  }

  public static void writeUnsigned64(final ByteArrayOutputStream s, final long value) {
    writeVarint64(s, value);
  }

  public static int readInt32(final ByteArrayInputStream s) {
    return readVarint32(s);
  }

  public static void writeInt32(final ByteArrayOutputStream s, final int value) {
    if(0 <= value)
      writeVarint32(s, value);
    else
      writeVarint64(s, value);
  }

  public static int encodeZigZag32(final int n) {
    return (n << 1) ^ (n >> 31);
  }

  public static int decodeZigZag32(final int n) {
    return (n >>> 1) ^ -(n & 1);
  }

  public static long encodeZigZag64(final long n) {
    return (n << 1) ^ (n >> 63);
  }

  public static int readSigned32(final ByteArrayInputStream s) {
    return decodeZigZag32(readVarint32(s));
  }

  public static void writeSigned32(final ByteArrayOutputStream s, final int v) {
    writeVarint32(s, encodeZigZag32(v));
  }

  public static long decodeZigZag64(final long n) {
    return (n >>> 1) ^ -(n & 1);
  }

  public static long readSigned64(final ByteArrayInputStream s) {
    return decodeZigZag64(readVarint64(s));
  }

  public static void writeSigned64(final ByteArrayOutputStream s, final long v) {
    writeVarint64(s, encodeZigZag64(v));
  }

  public static int readFixed32(final ByteArrayInputStream s) {
    return s.read() | (s.read() << 8) | (s.read() << 16) | (s.read() << 24);
  }

  public static void writeFixed32(final ByteArrayOutputStream s, final int n) {
    s.write(n         & 0xFF);
    s.write((n >> 8)  & 0xFF);
    s.write((n >> 16) & 0xFF);
    s.write((n >> 24) & 0xFF);
  }

  public static long readFixed64(final ByteArrayInputStream s) {
    return (((long)s.read() & 0xff)      ) |
      (((long)s.read() & 0xff) <<  8) |
      (((long)s.read() & 0xff) << 16) |
      (((long)s.read() & 0xff) << 24) |
      (((long)s.read() & 0xff) << 32) |
      (((long)s.read() & 0xff) << 40) |
      (((long)s.read() & 0xff) << 48) |
      (((long)s.read() & 0xff) << 56);
  }

  public static void writeFixed64(final ByteArrayOutputStream s, final long n) {
    s.write((int)n         & 0xFF);
    s.write((int)(n >>  8) & 0xFF);
    s.write((int)(n >> 16) & 0xFF);
    s.write((int)(n >> 24) & 0xFF);
    s.write((int)(n >> 32) & 0xFF);
    s.write((int)(n >> 40) & 0xFF);
    s.write((int)(n >> 48) & 0xFF);
    s.write((int)(n >> 56) & 0xFF);
  }

  public static double readDouble(final ByteArrayInputStream s) {
    return Double.longBitsToDouble(readFixed64(s));
  }

  public static void writeDouble(final ByteArrayOutputStream s, final double d) {
    writeFixed64(s, Double.doubleToLongBits(d));
  }

  public static float readFloat(final ByteArrayInputStream s) {
    return Float.intBitsToFloat(readFixed32(s));
  }

  public static void writeFloat(final ByteArrayOutputStream s, final float f) {
    writeFixed32(s, Float.floatToIntBits(f));
  }
}
