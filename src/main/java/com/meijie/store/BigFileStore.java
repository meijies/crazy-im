package com.meijie.store;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Big File Store Engine
 * <p>
 * 不确定是否应该使用多个线程写大文件，因为多线程会破坏顺序写，并且需要注意很多问题,
 * 因此先提供单线程版本。(之后可以参考一下kafka如何写数据的)
 *
 * @author meijie
 */
public class BigFileStore implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private FileChannel bigFileIndexFileChannel;
    private RandomAccessFile bigFileStoreAccessFile;
    private MappedByteBuffer bigFileIndexBuffer; //被用来存储索引文件
    private BufferedOutputStream bigFileStoreOutputStream;
    private static final int MAPPED_SIZE = 20 * 1024 * 1024;
    private static final int BIG_FILE_META_ITEM_SIZE = 2 * 8 + 4;

    private long offset = 0;
    private long globalFileIndex = 0;
    private static final Map<Long, Pair<Long, Integer>> metaMap = new HashMap<>();

    public BigFileStore(String bigFileStoreFolder) {
        File imageStoreFile = new File(bigFileStoreFolder + File.separator + "IMAGE_STORE");
        File imageIndexFile = new File(bigFileStoreFolder + File.separator + "IMAGE_STORE_INDEX");
        try {
            createNotExistsFile(imageStoreFile);
            createNotExistsFile(imageIndexFile);
            bigFileIndexFileChannel = new RandomAccessFile(imageIndexFile, "rw").getChannel();
            bigFileStoreAccessFile = new RandomAccessFile(imageStoreFile, "r");
            bigFileIndexBuffer = bigFileIndexFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, MAPPED_SIZE);
            bigFileStoreOutputStream = new BufferedOutputStream(new FileOutputStream(imageStoreFile));

            // 加载元数据
            loadMetaData();
        } catch (FileNotFoundException e) {
            log.error("Big File Store Folder not exists " + bigFileStoreFolder);
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error("read and write Big File Store File error");
            throw new RuntimeException(e);
        }
    }

    private void loadMetaData() throws IOException {
        loadMetaDataFromIndexFile();
        recoverLeftMetaData();
    }

    private void recoverLeftMetaData() throws IOException {
        // recover the meta data which didn't stored at index file
        long storeFileLength = bigFileStoreAccessFile.length();
        Pair<Long, Integer> meta = metaMap.get(globalFileIndex);
        long indexedFileLength = 0;
        if (meta != null) {
            long offset = meta.getLeft();
            int length = meta.getRight();
            indexedFileLength = offset + length;
            bigFileStoreAccessFile.seek(indexedFileLength);
        }
        while (storeFileLength > indexedFileLength) {
            byte[] fileIndexByteArray = new byte[8];
            byte[] fileOffsetByteArray = new byte[8];
            byte[] fileLengthByteArray = new byte[4];

            bigFileStoreAccessFile.readFully(fileIndexByteArray);
            bigFileStoreAccessFile.readFully(fileOffsetByteArray);
            bigFileStoreAccessFile.readFully(fileLengthByteArray);

            bigFileIndexBuffer.put(fileIndexByteArray);
            bigFileIndexBuffer.put(fileOffsetByteArray);
            bigFileIndexBuffer.put(fileLengthByteArray);

            long fileIndex = byteArrayToLong(fileIndexByteArray);
            long fileOffset = byteArrayToLong(fileOffsetByteArray);
            int fileLength = byteArrayToInt(fileLengthByteArray);

            metaMap.put(fileIndex, Pair.of(fileOffset, fileLength));
            indexedFileLength = fileOffset + fileLength;
            globalFileIndex = Math.max(globalFileIndex, fileIndex);
        }
    }

    private void loadMetaDataFromIndexFile() {
        // read from the beginning of the index file
        bigFileIndexBuffer.position(0);
        while (true) {
            long fileIndex = bigFileIndexBuffer.getLong();
            long fileOffset = bigFileIndexBuffer.getLong();
            int fileLength = bigFileIndexBuffer.getInt();

            if (fileLength != 0) {
                metaMap.put(fileIndex, Pair.of(fileOffset, fileLength));
                globalFileIndex = Math.max(globalFileIndex, fileIndex);
            } else {
                bigFileIndexBuffer.position(bigFileIndexBuffer.position() - BIG_FILE_META_ITEM_SIZE);
                break;
            }
        }
    }

    private void createNotExistsFile(File file) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    // fileIndex | offset | length | content
    public synchronized long storeBigFile(byte[] bigFileByteArray) {
        try {
            long fileIndex = globalFileIndex + 1;
            int wholeLength = bigFileByteArray.length + BIG_FILE_META_ITEM_SIZE;
            if (bigFileIndexBuffer.remaining() > BIG_FILE_META_ITEM_SIZE) {
                // write the data
                bigFileStoreOutputStream.write(longToByteArray(fileIndex));
                bigFileStoreOutputStream.write(longToByteArray(offset));
                bigFileStoreOutputStream.write(intToByteArray(wholeLength));
                bigFileStoreOutputStream.write(bigFileByteArray);
                bigFileStoreOutputStream.flush();

                bigFileIndexBuffer.putLong(fileIndex);
                bigFileIndexBuffer.putLong(offset);
                bigFileIndexBuffer.putInt(wholeLength);

                // cache the meta data
                metaMap.putIfAbsent(fileIndex, Pair.of(offset, wholeLength));
                offset = offset + wholeLength;
                globalFileIndex = globalFileIndex + 1;
                return fileIndex;
            } else {
                throw new RuntimeException("the meta store space is not enough");
            }
        } catch (IOException e) {
            log.error("write big file failure ", e);
            throw new RuntimeException(e);
        }
    }

    public static int byteArrayToInt(byte[] b) {
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }


    public static byte[] longToByteArray(long data) {
        return new byte[]{
                (byte) ((data >> 56) & 0xff),
                (byte) ((data >> 48) & 0xff),
                (byte) ((data >> 40) & 0xff),
                (byte) ((data >> 32) & 0xff),
                (byte) ((data >> 24) & 0xff),
                (byte) ((data >> 16) & 0xff),
                (byte) ((data >> 8) & 0xff),
                (byte) ((data >> 0) & 0xff)};
    }

    public static long byteArrayToLong(byte[] b) {
        return b[7] & 0xFF |
                (b[6] & 0xFF) << 8 |
                (b[5] & 0xFF) << 16 |
                (b[4] & 0xFF) << 24 |
                (b[3] & 0xFF) << 32 |
                (b[2] & 0xFF) << 40 |
                (b[1] & 0xFF) << 48 |
                (b[0] & 0xFF) << 56;
    }


    public byte[] readFile(long fileId) {
        Pair<Long, Integer> meta = metaMap.get(fileId);
        long fileOffset = meta.getKey();
        int fileLength = meta.getValue();
        try {
            bigFileStoreAccessFile.seek(fileOffset + BIG_FILE_META_ITEM_SIZE);
            byte[] byteArray = new byte[fileLength - BIG_FILE_META_ITEM_SIZE];
            bigFileStoreAccessFile.readFully(byteArray);
            return byteArray;
        } catch (IOException e) {
            log.error("read big file failure ", e);
        }
        return new byte[0];
    }

    @Override
    public void close() throws Exception {
        if (bigFileIndexBuffer != null) {
            Cleaner var1 = ((DirectBuffer) bigFileIndexBuffer).cleaner();
            var1.clean();
        }
        if (bigFileIndexFileChannel != null) {
            bigFileIndexFileChannel.close();
        }
        if (bigFileStoreOutputStream != null) {
            bigFileStoreOutputStream.close();
        }
    }

}
