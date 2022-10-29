package iped.parsers.mft;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MFTEntry {
    public static final String MIME_TYPE = "application/x-mft-entry";
    public static final int entryLength = 1024;

    private static final Charset charset = Charset.forName("UTF-16LE");

    private long logFileSequenceNumber = -1, baseRecordFileRef = -1, length = -1, recordNumber = -1,
            parentRecordNumber = -1;
    private int fixUpOffset = -1, fixUpSize = -1, sequence = -1, linkCount = -1, attrOffset = -1, flags = -1,
            firstAttrId = -1, usedSize = -1, totalSize = -1, residentFileStart = -1, clusterLength = -1;
    private String name, dosAttributes;
    private Date creationDate, lastModificationDate, lastAccessDate, lastEntryModificationDate;
    private List<Long> dataruns;

    private MFTEntry() {
    }

    public static MFTEntry parse(byte[] in) {
        if (in == null || in.length != entryLength) {
            return null;
        }

        // Header
        MFTEntry entry = parseEntryHeader(in);

        // Attributes
        int pos = entry.attrOffset;
        while (pos < entryLength) {
            MFTAttribute attr = parseAttributeHeader(in, pos);
            if (attr == null || attr.getLen() <= 0)
                break;
            parseAttribute(entry, attr, in, pos);
            pos += attr.getLen();
        }

        return entry;
    }

    private static void parseAttribute(MFTEntry entry, MFTAttribute attr, byte[] in, int offset) {
        offset += attr.getDataOffset();
        if (attr.getType() == 0x30) {
            // File name
            int namespace = toInt1(in, offset + 65);
            if (namespace == 0 || namespace == 1 || (namespace == 3 && entry.name == null)) {
                int start = offset + 66;
                int len = toInt1(in, offset + 64);
                int end = start + len * 2;
                if (start < entryLength && end <= entryLength && start < end) {
                    try {
                        entry.name = new String(in, start, len * 2, charset).trim();
                    } catch (Exception e) {
                    }
                }
            }
            long parent = toLong(in, offset, 6);
            if (parent > 0 && parent < 1L << 32) {
                entry.parentRecordNumber = parent;
            }
        } else if (attr.getType() == 0x10) {
            // Standard Attributes (including file dates)
            entry.creationDate = toDate(in, offset);
            entry.lastModificationDate = toDate(in, offset + 8);
            entry.lastEntryModificationDate = toDate(in, offset + 16);
            entry.lastAccessDate = toDate(in, offset + 24);
            int dosAttributes = toInt(in, offset + 32);
            if (dosAttributes > 0) {
                entry.dosAttributes = formatDosAttributes(dosAttributes);
            }
        } else if (attr.getType() == 0x80) {
            // Data
            if (attr.isResident()) {
                int end = offset + (int) attr.getDataLength();
                if (offset < entryLength && end <= entryLength && offset < end && entry.dataruns == null) {
                    entry.length = end - offset;
                    entry.residentFileStart = offset;
                }
            } else {
                if (attr.getDataLength() >= 0) {
                    entry.length = attr.getDataLength();
                    entry.residentFileStart = -1;
                    if (attr.getDataruns() != null) {
                        entry.dataruns = attr.getDataruns();
                    }
                    if (attr.getClusterLength() > 0) {
                        entry.clusterLength = attr.getClusterLength();
                    }
                }
            }
        }
    }

    private static MFTAttribute parseAttributeHeader(byte[] in, int offset) {
        int type = toInt(in, offset);
        if (type == 0xFFFFFFFF) {
            return null;
        }
        MFTAttribute attr = new MFTAttribute();
        attr.setType(type);
        attr.setLen(toInt(in, offset + 4));
        attr.setResident(!toBoolean(in, offset + 8));
        attr.setNameLen(toInt1(in, offset + 9));
        attr.setNameOffset(toInt2(in, offset + 10));
        attr.setDataFlags(toInt2(in, offset + 12));
        attr.setId(toInt2(in, offset + 14));
        if (attr.isResident()) {
            attr.setDataLength(toInt(in, offset + 16));
            attr.setDataOffset(toInt2(in, offset + 20));
        } else {
            long dataLength = toLong(in, offset + 48);
            attr.setDataLength(dataLength);
            if (attr.getDataFlags() == 0 && dataLength > 0) {
                int dataRunsOffset = toInt2(in, offset + 32);
                if (dataRunsOffset >= 64) {
                    long firstVcn = toLong(in, offset + 16);
                    long lastVcn = toLong(in, offset + 24);
                    long allocated = toLong(in, offset + 40);
                    if (allocated >= dataLength && lastVcn >= firstVcn && firstVcn >= 0 && lastVcn >= 0) {
                        int clusterLength = (int) (allocated / (lastVcn - firstVcn + 1));
                        if (clusterLength >= 1 << 8 && clusterLength <= 1 << 21
                                && Integer.bitCount(clusterLength) == 1) {
                            attr.setClusterLength(clusterLength);
                            offset += dataRunsOffset;
                            List<Long> dataruns = new ArrayList<Long>();
                            boolean finished = false;
                            long prevPos = 0;
                            while (offset < entryLength) {
                                int b = toInt1(in, offset);
                                if (b == 0) {
                                    finished = true;
                                    break;
                                }
                                int lenLen = b & 0xF;
                                if (lenLen == 0 || lenLen > 8) {
                                    break;
                                }
                                int posLen = b >>> 4;
                                if (posLen == 0 || posLen > 8) {
                                    break;
                                }
                                if (++offset + lenLen > entryLength) {
                                    break;
                                }
                                long len = toLong(in, offset, lenLen);
                                if (len == 0) {
                                    break;
                                }
                                if ((offset += lenLen) + posLen > entryLength) {
                                    break;
                                }
                                long pos = toSignedLong(in, offset, posLen);
                                offset += posLen;
                                pos += prevPos;
                                if (pos <= 0) {
                                    break;
                                }
                                dataruns.add(pos);
                                dataruns.add(len);
                                prevPos = pos;
                            }
                            if (finished && !dataruns.isEmpty()) {
                                attr.setDataruns(dataruns);
                            }
                        }
                    }
                }
            }
        }
        return attr;
    }

    private static MFTEntry parseEntryHeader(byte[] in) {
        MFTEntry entry = new MFTEntry();
        entry.fixUpOffset = toInt2(in, 4);
        entry.fixUpSize = toInt2(in, 6);
        entry.logFileSequenceNumber = toLong(in, 8);
        entry.sequence = toInt2(in, 16);
        entry.linkCount = toInt2(in, 18);
        entry.attrOffset = toInt2(in, 20);
        entry.flags = toInt2(in, 22);
        entry.usedSize = toInt(in, 24);
        entry.totalSize = toInt(in, 28);
        entry.baseRecordFileRef = toLong(in, 32);
        entry.firstAttrId = toInt2(in, 40);
        if (entry.fixUpOffset >= 48) {
            entry.recordNumber = toLong(in, 44, 4);
        }
        if (entry.fixUpSize == 3 && entry.fixUpOffset >= 42 && entry.fixUpOffset <= 48) {
            int seqNumber = toInt2(in, entry.fixUpOffset);
            if (seqNumber == toInt2(in, 510) && seqNumber == toInt2(in, 1022)) {
                System.arraycopy(in, entry.fixUpOffset + 2, in, 510, 2);
                System.arraycopy(in, entry.fixUpOffset + 4, in, 1022, 2);
            }
        }
        return entry;
    }

    private static long toLong(byte[] bytes, int pos) {
        return (bytes[pos] & 255L) | ((bytes[pos + 1] & 255L) << 8) | ((bytes[pos + 2] & 255L) << 16)
                | ((bytes[pos + 3] & 255L) << 24) | ((bytes[pos + 4] & 255L) << 32) | ((bytes[pos + 5] & 255L) << 40)
                | ((bytes[pos + 6] & 255L) << 48) | ((bytes[pos + 7] & 255L) << 56);
    }

    private static long toLong(byte[] bytes, int pos, int len) {
        long v = 0;
        for (int i = 0; i < len; i++) {
            v |= (bytes[pos + i] & 255) << (i << 3);
        }
        return v;
    }

    private static long toSignedLong(byte[] bytes, int pos, int len) {
        if ((bytes[pos + len - 1] & 128) == 0) {
            return toLong(bytes, pos, len);
        }
        long v = 0;
        for (int i = 0; i < len; i++) {
            v |= ((bytes[pos + i] & 255) ^ 255) << (i << 3);
        }
        return -(v + 1);
    }

    private static long toLong4(byte[] bytes, int pos) {
        return (bytes[pos] & 255L) | ((bytes[pos + 1] & 255L) << 8) | ((bytes[pos + 2] & 255L) << 16)
                | ((bytes[pos + 3] & 255L) << 24) | ((bytes[pos + 4] & 255L) << 32);
    }

    private static int toInt(byte[] bytes, int pos) {
        return (bytes[pos] & 255) | ((bytes[pos + 1] & 255) << 8) | ((bytes[pos + 2] & 255) << 16)
                | ((bytes[pos + 3] & 255) << 24);
    }

    private static int toInt2(byte[] bytes, int pos) {
        return (bytes[pos] & 255) | ((bytes[pos + 1] & 255) << 8);
    }

    private static int toInt1(byte[] bytes, int pos) {
        return bytes[pos] & 255;
    }

    private static boolean toBoolean(byte[] bytes, int pos) {
        return bytes[pos] != 0;
    }

    private static Date toDate(byte[] bytes, int pos) {
        long lo = toLong4(bytes, pos);
        long hi = toLong4(bytes, pos + 4);
        if (lo == 0 && hi == 0)
            return null;
        long t = (hi << 32) | lo;
        return new Date(t / 10000 - 11_644_473_600_000L);
    }

    public long getLogFileSequenceNumber() {
        return logFileSequenceNumber;
    }

    public long getBaseRecordFileRef() {
        return baseRecordFileRef;
    }

    public long getLength() {
        return length;
    }

    public long getRecordNumber() {
        return recordNumber;
    }

    public long getParentRecordNumber() {
        return parentRecordNumber;
    }

    public int getFixUpOffset() {
        return fixUpOffset;
    }

    public int getFixUpSize() {
        return fixUpSize;
    }

    public int getSequence() {
        return sequence;
    }

    public int getLinkCount() {
        return linkCount;
    }

    public int getAttrOffset() {
        return attrOffset;
    }

    public int getFlags() {
        return flags;
    }

    public boolean isFile() {
        if (flags < 0)
            return false;
        return (flags & 2) == 0;
    }

    public boolean isFolder() {
        if (flags < 0)
            return false;
        return (flags & 2) != 0;
    }

    public boolean isActive() {
        if (flags < 0)
            return false;
        return (flags & 1) != 0;
    }

    public boolean isInactive() {
        if (flags < 0)
            return false;
        return (flags & 1) == 0;
    }

    public int getFirstAttrId() {
        return firstAttrId;
    }

    public int getUsedSize() {
        return usedSize;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public String getName() {
        return name;
    }

    public String getDosAttributes() {
        return dosAttributes;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public Date getLastModificationDate() {
        return lastModificationDate;
    }

    public Date getLastAccessDate() {
        return lastAccessDate;
    }

    public Date getLastEntryModificationDate() {
        return lastEntryModificationDate;
    }

    public byte[] getResidentContent(byte[] in) {
        if (length > 0 && residentFileStart > 0) {
            byte[] content = new byte[(int) length];
            System.arraycopy(in, residentFileStart, content, 0, content.length);
            return content;
        }
        return null;
    }

    public boolean hasResidentContent() {
        return length > 0 && residentFileStart > 0;
    }

    public boolean hasNonResidentContent() {
        return length > 0 && dataruns != null;
    }

    public List<Long> getDataruns() {
        return dataruns;
    }

    public int getClusterLength() {
        return clusterLength;
    }

    public boolean isFragmented() {
        return dataruns != null && dataruns.size() > 2;
    }

    private static String formatDosAttributes(int a) {
        StringBuilder sb = new StringBuilder();
        sb.append("0x");
        sb.append(String.format("%04x", a));
        sb.append(" (");
        if ((a & 0x0001) != 0) {
            sb.append("Read-Only, ");
        }
        if ((a & 0x0002) != 0) {
            sb.append("Hidden, ");
        }
        if ((a & 0x0004) != 0) {
            sb.append("System, ");
        }
        if ((a & 0x0020) != 0) {
            sb.append("Archive, ");
        }
        if ((a & 0x0040) != 0) {
            sb.append("Device, ");
        }
        if ((a & 0x0080) != 0) {
            sb.append("Normal, ");
        }
        if ((a & 0x0100) != 0) {
            sb.append("Temporary, ");
        }
        if ((a & 0x0200) != 0) {
            sb.append("Sparse File, ");
        }
        if ((a & 0x0400) != 0) {
            sb.append("Reparse Point, ");
        }
        if ((a & 0x0800) != 0) {
            sb.append("Compressed, ");
        }
        if ((a & 0x1000) != 0) {
            sb.append("Offline, ");
        }
        if ((a & 0x2000) != 0) {
            sb.append("Not Content Indexed, ");
        }
        if ((a & 0x4000) != 0) {
            sb.append("Encrypted, ");
        }
        if (sb.charAt(sb.length() - 1) == '(') {
            sb.delete(sb.length() - 2, sb.length());
        } else if (sb.charAt(sb.length() - 1) == ' ') {
            sb.delete(sb.length() - 1, sb.length());
            sb.setCharAt(sb.length() - 1, ')');
        }
        return sb.toString();
    }
}